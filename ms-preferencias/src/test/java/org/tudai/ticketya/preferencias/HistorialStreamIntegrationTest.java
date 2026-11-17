package org.tudai.ticketya.preferencias;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import org.tudai.ticketya.contract.preferencias.model.HistorialCompraDTO;
import org.tudai.ticketya.contract.preferencias.model.TipoEvento;
import org.tudai.ticketya.preferencias.repository.PreferenciaRepository;
import org.tudai.ticketya.preferencias.service.HistorialService;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Verifica el consumidor real contra Redis y MongoDB (Testcontainers).
 * <p>
 * Cubre los criterios de la card: una compra suma el evento al historial, el uso
 * lo marca como usado y un evento repetido no duplica la entrada.
 * Se omite automáticamente cuando no hay Docker disponible.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(properties = {
        "eureka.client.enabled=false",
        "spring.cloud.discovery.enabled=false",
        "preferencias.historial.poll-timeout=200ms"
})
@org.springframework.test.annotation.DirtiesContext(
        classMode = org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_CLASS)
class HistorialStreamIntegrationTest {

    @Container
    static final MongoDBContainer MONGO = new MongoDBContainer("mongo:7");

    @Container
    static final GenericContainer<?> REDIS =
            new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
                    .withExposedPorts(6379);

    @DynamicPropertySource
    static void propiedades(DynamicPropertyRegistry registry) {
        registry.add("spring.data.mongodb.uri", MONGO::getReplicaSetUrl);
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
    }

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private HistorialService historialService;

    @Autowired
    private PreferenciaRepository preferenciaRepository;

    @AfterEach
    void limpiar() {
        preferenciaRepository.deleteAll();
    }

    @Test
    void ventaPagada_sumaElEventoYNoDuplicaSiLlegaDeNuevo() {
        Map<String, String> venta = Map.of(
                "clienteId", "7",
                "eventoId", "5",
                "tipo", "RECITAL",
                "participanteIds", "15,20",
                "ciudad", "Tandil",
                "fechaEvento", "2026-12-01T20:00:00Z",
                "traceId", "trace-1");

        redisTemplate.opsForStream().add("venta.pagada", venta);

        esperar(() -> !historialService.obtenerHistorial(7L).isEmpty());
        List<HistorialCompraDTO> historial = historialService.obtenerHistorial(7L);
        assertThat(historial).singleElement().satisfies(entrada -> {
            assertThat(entrada.getEventoId()).isEqualTo(5L);
            assertThat(entrada.getTipo()).isEqualTo(TipoEvento.RECITAL);
            assertThat(entrada.getParticipanteIds()).containsExactly(15L, 20L);
            assertThat(entrada.getCiudad()).isEqualTo("Tandil");
            assertThat(entrada.getFecha()).isEqualTo(LocalDateTime.parse("2026-12-01T20:00:00"));
            assertThat(entrada.getComprado()).isTrue();
            assertThat(entrada.getUsado()).isFalse();
        });

        redisTemplate.opsForStream().add("venta.pagada", venta);
        sleep(1500);

        assertThat(historialService.obtenerHistorial(7L)).hasSize(1);
    }

    @Test
    void ticketUtilizado_marcaElEventoComoUsadoYPesaSobreElComprado() {
        redisTemplate.opsForStream().add("venta.pagada", Map.of(
                "clienteId", "7",
                "eventoId", "5",
                "tipo", "RECITAL",
                "ciudad", "Tandil"));
        esperar(() -> !historialService.obtenerHistorial(7L).isEmpty());

        redisTemplate.opsForStream().add("ticket.utilizado", Map.of(
                "clienteId", "7",
                "eventoId", "5",
                "ticketId", "99",
                "fechaUso", "2026-12-02T21:00:00Z",
                "traceId", "trace-2"));

        esperar(() -> historialService.obtenerHistorial(7L).stream()
                .anyMatch(entrada -> Boolean.TRUE.equals(entrada.getUsado())));

        assertThat(historialService.obtenerHistorial(7L))
                .singleElement()
                .satisfies(entrada -> {
                    assertThat(entrada.getComprado()).isTrue();
                    assertThat(entrada.getUsado()).isTrue();
                });
    }

    private void esperar(BooleanSupplier condicion) {
        long limite = System.nanoTime() + Duration.ofSeconds(10).toNanos();
        while (System.nanoTime() < limite) {
            if (condicion.getAsBoolean()) {
                return;
            }
            sleep(100);
        }
        fail("No se cumplió la condición a tiempo");
    }

    private static void sleep(long milisegundos) {
        try {
            Thread.sleep(milisegundos);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
