package org.tudai.ticketya.eventos.service;

import org.junit.jupiter.api.Test;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.tudai.ticketya.contract.eventos.model.EstadoEvento;
import org.tudai.ticketya.contract.eventos.model.EventoFiltroRequest;
import org.tudai.ticketya.contract.eventos.model.EventoResumen;
import org.tudai.ticketya.contract.eventos.model.TipoEvento;
import org.tudai.ticketya.eventos.client.DevolucionesClient;
import org.tudai.ticketya.eventos.client.TicketsClient;
import org.tudai.ticketya.eventos.domain.Evento;
import org.tudai.ticketya.eventos.domain.Lugar;
import org.tudai.ticketya.eventos.domain.Ubicacion;
import org.tudai.ticketya.eventos.repository.EventoRepository;
import org.tudai.ticketya.eventos.repository.LugarRepository;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Con un TTL de 1 s: después de cancelar un evento, la búsqueda cacheada lo sigue mostrando hasta
 * que vence, y la siguiente ya trae el dato nuevo (inconsistencia máxima = TTL, TKY-83).
 */
@SpringBootTest(properties = {
        "eureka.client.enabled=false",
        "spring.datasource.url=jdbc:h2:mem:eventos-busqueda-vencimiento;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.liquibase.enabled=false",
        "ticketya.eventos.finalizacion.enabled=false",
        "ticketya.eventos.notificaciones.enabled=false",
        "ticketya.logging.dir=target/test-logs",
        "ticketya.cache.ttl.evento-busqueda=PT1S"
})
@Testcontainers(disabledWithoutDocker = true)
class EventoBusquedaCacheVencimientoTest {

    static final Pageable PRIMERA_PAGINA = PageRequest.of(0, 20);

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    @DynamicPropertySource
    static void redis(DynamicPropertyRegistry registry) {
        registry.add("ticketya.cache.redis-host", REDIS::getHost);
        registry.add("ticketya.cache.redis-port", () -> REDIS.getMappedPort(6379));
    }

    @Autowired
    EventoBusquedaCache eventoBusquedaCache;

    @Autowired
    EventoService eventoService;

    @Autowired
    EventoRepository eventoRepository;

    @Autowired
    LugarRepository lugarRepository;

    @Autowired
    RedissonClient redissonCacheClient;

    @MockitoBean
    TicketsClient ticketsClient;

    @MockitoBean
    DevolucionesClient devolucionesClient;

    @Test
    void despuesDelTtlLaBusquedaTraeElDatoNuevo() throws InterruptedException {
        redissonCacheClient.getKeys().flushall();
        Lugar lugar = lugarRepository.save(Lugar.builder()
                .nombre("Teatro")
                .tipo("TEATRO")
                .direccion("Calle 123")
                .ciudad("Tandil")
                .ubicacion(Ubicacion.builder().latitud(-37.32).longitud(-59.14).build())
                .build());
        Evento evento = eventoRepository.save(Evento.builder()
                .nombre("Recital")
                .descripcion("Detalle")
                .tipo(TipoEvento.RECITAL)
                .fecha(LocalDateTime.now().plusDays(10))
                .estado(EstadoEvento.PUBLICADO)
                .organizadorId(7L)
                .lugar(lugar)
                .build());
        assertThat(nombres()).containsExactly("Recital");

        eventoService.cancelarEvento(evento.getId());
        assertThat(nombres()).containsExactly("Recital");

        Thread.sleep(1_500);

        assertThat(nombres()).isEmpty();
    }

    private List<String> nombres() {
        return eventoBusquedaCache.buscar(new EventoFiltroRequest(), PRIMERA_PAGINA).comoPage(PRIMERA_PAGINA)
                .getContent().stream()
                .map(EventoResumen::getNombre)
                .toList();
    }
}
