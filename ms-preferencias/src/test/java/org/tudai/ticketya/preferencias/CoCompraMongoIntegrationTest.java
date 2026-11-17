package org.tudai.ticketya.preferencias;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.tudai.ticketya.preferencias.repository.CoCompraRepository;
import org.tudai.ticketya.preferencias.service.CoCompraService;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifica la co-ocurrencia contra MongoDB (Testcontainers).
 * <p>
 * Se omite automáticamente cuando no hay Docker disponible.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(properties = {
        "eureka.client.enabled=false",
        "spring.cloud.discovery.enabled=false",
        "preferencias.historial.consumer-enabled=false"
})
class CoCompraMongoIntegrationTest {

    @Container
    static final MongoDBContainer MONGO = new MongoDBContainer("mongo:7");

    @DynamicPropertySource
    static void propiedades(DynamicPropertyRegistry registry) {
        registry.add("spring.data.mongodb.uri", MONGO::getReplicaSetUrl);
    }

    @Autowired
    private CoCompraService coCompraService;

    @Autowired
    private CoCompraRepository coCompraRepository;

    @AfterEach
    void limpiar() {
        coCompraRepository.deleteAll();
    }

    @Test
    void registrarCompra_guardaAmbasDireccionesYAcumulaLosClientes() {
        coCompraService.registrarCompra(6L, List.of(5L));
        coCompraService.registrarCompra(7L, List.of(5L, 6L));

        var cinco = coCompraRepository.findById(5L).orElseThrow();
        var seis = coCompraRepository.findById(6L).orElseThrow();
        var siete = coCompraRepository.findById(7L).orElseThrow();

        assertThat(cinco.getRelacionados()).anySatisfy(relacionado -> {
            assertThat(relacionado.getEventoId()).isEqualTo(6L);
            assertThat(relacionado.getCantidad()).isEqualTo(1);
        });
        assertThat(cinco.getRelacionados()).anySatisfy(relacionado -> {
            assertThat(relacionado.getEventoId()).isEqualTo(7L);
            assertThat(relacionado.getCantidad()).isEqualTo(1);
        });
        assertThat(seis.getRelacionados()).anySatisfy(relacionado -> {
            assertThat(relacionado.getEventoId()).isEqualTo(5L);
            assertThat(relacionado.getCantidad()).isEqualTo(1);
        });
        assertThat(siete.getRelacionados()).anySatisfy(relacionado -> {
            assertThat(relacionado.getEventoId()).isEqualTo(5L);
            assertThat(relacionado.getCantidad()).isEqualTo(1);
        });
        assertThat(siete.getRelacionados()).anySatisfy(relacionado -> {
            assertThat(relacionado.getEventoId()).isEqualTo(6L);
            assertThat(relacionado.getCantidad()).isEqualTo(1);
        });
    }

    @Test
    void coOcurrencias_sumaLosEventosRelacionadosDeLosEventosBase() {
        coCompraService.registrarCompra(6L, List.of(5L));
        coCompraService.registrarCompra(7L, List.of(5L, 6L));

        Map<Long, Long> totales = coCompraService.coOcurrencias(List.of(6L, 7L));

        assertThat(totales).containsEntry(5L, 2L);
        assertThat(totales).containsEntry(6L, 1L);
        assertThat(totales).containsEntry(7L, 1L);
    }

    @Test
    void registrarCompra_delMismoParDosVeces_incrementaLaCantidad() {
        coCompraService.registrarCompra(6L, List.of(5L));
        coCompraService.registrarCompra(6L, List.of(5L));

        assertThat(coCompraService.coOcurrencias(List.of(6L))).containsEntry(5L, 2L);
    }
}
