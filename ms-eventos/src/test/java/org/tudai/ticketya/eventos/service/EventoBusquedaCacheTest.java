package org.tudai.ticketya.eventos.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.redisson.api.RMapCache;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
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
import org.tudai.ticketya.core.exception.BadRequestException;
import org.tudai.ticketya.eventos.client.DevolucionesClient;
import org.tudai.ticketya.eventos.client.TicketsClient;
import org.tudai.ticketya.eventos.domain.Evento;
import org.tudai.ticketya.eventos.domain.Lugar;
import org.tudai.ticketya.eventos.domain.SectorNoNumerado;
import org.tudai.ticketya.eventos.domain.Ubicacion;
import org.tudai.ticketya.eventos.repository.EventoRepository;
import org.tudai.ticketya.eventos.repository.LugarRepository;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Cache de los resultados de búsqueda con Redis real (Testcontainers): la misma búsqueda se sirve
 * de la cache con TTL corto, otros parámetros consultan la base, y publicar o cancelar un evento no
 * invalida las búsquedas guardadas (la inconsistencia máxima es el TTL) (TKY-83).
 */
@SpringBootTest(properties = {
        "eureka.client.enabled=false",
        "spring.datasource.url=jdbc:h2:mem:eventos-busqueda-cache;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.liquibase.enabled=false",
        "ticketya.eventos.finalizacion.enabled=false",
        "ticketya.eventos.notificaciones.enabled=false",
        "ticketya.logging.dir=target/test-logs"
})
@Testcontainers(disabledWithoutDocker = true)
class EventoBusquedaCacheTest {

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

    private Lugar lugar;

    @BeforeEach
    void preparar() {
        redissonCacheClient.getKeys().flushall();
        eventoRepository.deleteAll();
        lugarRepository.deleteAll();
        Lugar nuevo = Lugar.builder()
                .nombre("Teatro")
                .tipo("TEATRO")
                .direccion("Calle 123")
                .ciudad("Tandil")
                .ubicacion(Ubicacion.builder().latitud(-37.32).longitud(-59.14).build())
                .build();
        nuevo.addSector(SectorNoNumerado.builder().nombre("Campo").precio(1000.0).capacidadMaxima(100).build());
        lugar = lugarRepository.save(nuevo);
    }

    @Test
    void laMismaBusquedaSaleDeLaCacheConElTtlConfigurado() {
        guardar("Recital", EstadoEvento.PUBLICADO);
        assertThat(nombres(new EventoFiltroRequest(), PRIMERA_PAGINA)).containsExactly("Recital");

        // Evento nuevo cargado directo en la base: la búsqueda cacheada no lo ve.
        guardar("Otro recital", EstadoEvento.PUBLICADO);

        EventoBusquedaCache.Resultado resultado = eventoBusquedaCache.buscar(new EventoFiltroRequest(), PRIMERA_PAGINA);
        assertThat(resultado.total()).isEqualTo(1);
        assertThat(resultado.comoPage(PRIMERA_PAGINA).getContent()).extracting(EventoResumen::getNombre)
                .containsExactly("Recital");

        String clave = EventoBusquedaCache.clave(new EventoFiltroRequest(), PRIMERA_PAGINA);
        assertThat(cache().containsKey(clave)).isTrue();
        assertThat(cache().remainTimeToLive(clave)).isGreaterThan(20_000L).isLessThanOrEqualTo(30_000L);
    }

    @Test
    void conOtrosParametrosConsultaLaBase() {
        guardar("Recital", EstadoEvento.PUBLICADO);
        nombres(new EventoFiltroRequest(), PRIMERA_PAGINA);
        guardar("Otro recital", EstadoEvento.PUBLICADO);

        assertThat(nombres(new EventoFiltroRequest().ciudad("Tandil"), PRIMERA_PAGINA)).hasSize(2);
        assertThat(nombres(new EventoFiltroRequest(), PageRequest.of(0, 10))).hasSize(2);
        assertThat(nombres(new EventoFiltroRequest(), PageRequest.of(0, 20, Sort.by("nombre")))).hasSize(2);
        assertThat(nombres(new EventoFiltroRequest().orden("relevancia"), PRIMERA_PAGINA)).hasSize(2);
        assertThat(cache().size()).isEqualTo(5);
    }

    @Test
    void publicarUnEvento_noInvalidaLasBusquedasHastaElTtl() {
        Evento borrador = guardar("Recital nuevo", EstadoEvento.BORRADOR);
        borrador.definirPrecioSector(lugar.getSectores().iterator().next().getId(), 1500.0);
        eventoRepository.save(borrador);
        assertThat(nombres(new EventoFiltroRequest(), PRIMERA_PAGINA)).isEmpty();

        eventoService.publicarEvento(borrador.getId(), 7L, false);

        // Inconsistencia aceptada: la búsqueda guardada no muestra el evento publicado hasta que vence.
        assertThat(nombres(new EventoFiltroRequest(), PRIMERA_PAGINA)).isEmpty();
        assertThat(eventoService.obtenerEvento(borrador.getId(), null, false).getEstado())
                .isEqualTo(EstadoEvento.PUBLICADO);
    }

    @Test
    void cancelarUnEvento_noInvalidaLasBusquedasHastaElTtl() {
        Evento evento = guardar("Recital", EstadoEvento.PUBLICADO);
        assertThat(nombres(new EventoFiltroRequest(), PRIMERA_PAGINA)).containsExactly("Recital");

        eventoService.cancelarEvento(evento.getId());

        assertThat(nombres(new EventoFiltroRequest(), PRIMERA_PAGINA)).containsExactly("Recital");
        assertThat(eventoService.obtenerEvento(evento.getId(), null, false).getEstado())
                .isEqualTo(EstadoEvento.CANCELADO);
    }

    @Test
    void unaBusquedaInvalidaNoSeCachea() {
        assertThatThrownBy(() -> eventoBusquedaCache.buscar(new EventoFiltroRequest().tipo("NO_EXISTE"), PRIMERA_PAGINA))
                .isInstanceOf(BadRequestException.class);

        assertThat(cache().isEmpty()).isTrue();
    }

    private List<String> nombres(EventoFiltroRequest filtro, Pageable pageable) {
        return eventoBusquedaCache.buscar(filtro, pageable).comoPage(pageable).getContent().stream()
                .map(EventoResumen::getNombre)
                .toList();
    }

    private RMapCache<Object, Object> cache() {
        return redissonCacheClient.getMapCache(EventoBusquedaCache.CACHE);
    }

    private Evento guardar(String nombre, EstadoEvento estado) {
        return eventoRepository.save(Evento.builder()
                .nombre(nombre)
                .descripcion("Detalle")
                .tipo(TipoEvento.RECITAL)
                .fecha(LocalDateTime.now().plusDays(10))
                .estado(estado)
                .organizadorId(7L)
                .lugar(lugar)
                .build());
    }
}
