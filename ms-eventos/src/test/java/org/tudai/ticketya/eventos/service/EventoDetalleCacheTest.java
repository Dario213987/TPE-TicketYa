package org.tudai.ticketya.eventos.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.redisson.api.RMapCache;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.tudai.ticketya.contract.eventos.model.EstadoEvento;
import org.tudai.ticketya.contract.eventos.model.EventoUpdateRequest;
import org.tudai.ticketya.contract.eventos.model.TipoEvento;
import org.tudai.ticketya.core.exception.ConflictException;
import org.tudai.ticketya.core.exception.NotFoundException;
import org.tudai.ticketya.eventos.client.DevolucionesClient;
import org.tudai.ticketya.eventos.client.TicketsClient;
import org.tudai.ticketya.eventos.domain.Evento;
import org.tudai.ticketya.eventos.domain.Lugar;
import org.tudai.ticketya.eventos.domain.Ubicacion;
import org.tudai.ticketya.eventos.repository.EventoRepository;
import org.tudai.ticketya.eventos.repository.LugarRepository;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Cache del detalle de evento con Redis real (Testcontainers): se sirve de la cache, cada
 * operación que modifica el evento invalida su entrada, y la regla de borradores no se cachea
 * (TKY-82).
 */
@SpringBootTest(properties = {
        "eureka.client.enabled=false",
        "spring.datasource.url=jdbc:h2:mem:eventos-detalle-cache;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.liquibase.enabled=false",
        "ticketya.eventos.finalizacion.enabled=false",
        "ticketya.eventos.notificaciones.enabled=false",
        "ticketya.logging.dir=target/test-logs"
})
@Testcontainers(disabledWithoutDocker = true)
class EventoDetalleCacheTest {

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    @DynamicPropertySource
    static void redis(DynamicPropertyRegistry registry) {
        registry.add("ticketya.cache.redis-host", REDIS::getHost);
        registry.add("ticketya.cache.redis-port", () -> REDIS.getMappedPort(6379));
    }

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
        lugar = lugarRepository.save(Lugar.builder()
                .nombre("Teatro")
                .tipo("TEATRO")
                .direccion("Calle 123")
                .ciudad("Tandil")
                .ubicacion(Ubicacion.builder().latitud(-37.32).longitud(-59.14).build())
                .build());
    }

    @Test
    void laSegundaConsultaSaleDeLaCacheConElTtlConfigurado() {
        Evento evento = guardar(EstadoEvento.PUBLICADO, LocalDateTime.now().plusDays(10));
        assertThat(eventoService.obtenerEvento(evento.getId(), null, false).getNombre()).isEqualTo("Recital");

        // Cambio directo en la base, sin pasar por el servicio: la cache no se entera.
        evento.setNombre("Cambiado por fuera");
        eventoRepository.save(evento);

        assertThat(eventoService.obtenerEvento(evento.getId(), null, false).getNombre()).isEqualTo("Recital");
        assertThat(cache().containsKey(evento.getId())).isTrue();
        assertThat(cache().remainTimeToLive(evento.getId())).isGreaterThan(4 * 60_000L).isLessThanOrEqualTo(5 * 60_000L);
    }

    @Test
    void suspender_invalidaElDetalle() {
        verificaQueInvalida(EstadoEvento.PUBLICADO, id -> eventoService.suspenderEvento(id), EstadoEvento.SUSPENDIDO);
    }

    @Test
    void cancelar_invalidaElDetalle() {
        verificaQueInvalida(EstadoEvento.PUBLICADO, id -> eventoService.cancelarEvento(id), EstadoEvento.CANCELADO);
    }

    @Test
    void marcarAgotado_invalidaElDetalle() {
        verificaQueInvalida(EstadoEvento.PUBLICADO, id -> eventoService.marcarAgotado(id), EstadoEvento.AGOTADO);
    }

    @Test
    void marcarPublicado_invalidaElDetalle() {
        verificaQueInvalida(EstadoEvento.AGOTADO, id -> eventoService.marcarPublicado(id), EstadoEvento.PUBLICADO);
    }

    @Test
    void editar_invalidaElDetalle() {
        Evento evento = guardar(EstadoEvento.BORRADOR, LocalDateTime.now().plusDays(10));
        eventoService.obtenerEvento(evento.getId(), 7L, false);
        assertThat(cache().containsKey(evento.getId())).isTrue();

        eventoService.actualizarEvento(evento.getId(), new EventoUpdateRequest().nombre("Recital nuevo"), 7L, false);

        assertThat(cache().containsKey(evento.getId())).isFalse();
        assertThat(eventoService.obtenerEvento(evento.getId(), 7L, false).getNombre()).isEqualTo("Recital nuevo");
    }

    @Test
    void finalizarVencidos_invalidaElDetalleDeCadaEventoFinalizado() {
        Evento vencido = guardar(EstadoEvento.PUBLICADO, LocalDateTime.now().minusDays(2));
        eventoService.obtenerEvento(vencido.getId(), null, false);

        eventoService.finalizarEventosVencidos();

        assertThat(cache().containsKey(vencido.getId())).isFalse();
        assertThat(eventoService.obtenerEvento(vencido.getId(), null, false).getEstado())
                .isEqualTo(EstadoEvento.FINALIZADO);
    }

    @Test
    void siLaOperacionFalla_noInvalida() {
        Evento borrador = guardar(EstadoEvento.BORRADOR, LocalDateTime.now().plusDays(10));
        eventoService.obtenerEvento(borrador.getId(), 7L, false);

        assertThatThrownBy(() -> eventoService.suspenderEvento(borrador.getId()))
                .isInstanceOf(ConflictException.class);

        assertThat(cache().containsKey(borrador.getId())).isTrue();
    }

    @Test
    void unBorradorCacheadoSigueOcultoParaOtroUsuario() {
        Evento borrador = guardar(EstadoEvento.BORRADOR, LocalDateTime.now().plusDays(10));
        assertThat(eventoService.obtenerEvento(borrador.getId(), 7L, false).getEstado()).isEqualTo(EstadoEvento.BORRADOR);
        assertThat(cache().containsKey(borrador.getId())).isTrue();

        assertThatThrownBy(() -> eventoService.obtenerEvento(borrador.getId(), 99L, false))
                .isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> eventoService.obtenerEvento(borrador.getId(), null, false))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void todasLasOperacionesQueModificanUnEventoInvalidanSuDetalle() {
        List<String> operaciones = List.of("publicarEvento", "cancelarEvento", "suspenderEvento",
                "reactivarEvento", "marcarAgotado", "marcarPublicado", "actualizarEvento", "eliminarEvento",
                "definirPrecioSector");

        for (String nombre : operaciones) {
            Method metodo = Arrays.stream(EventoService.class.getDeclaredMethods())
                    .filter(m -> m.getName().equals(nombre))
                    .findFirst().orElseThrow(() -> new AssertionError("No existe " + nombre));
            CacheEvict evict = metodo.getAnnotation(CacheEvict.class);
            assertThat(evict).as(nombre + " invalida el detalle").isNotNull();
            assertThat(evict.cacheNames()).as(nombre).containsExactly(EventoDetalleCache.CACHE);
            assertThat(evict.key()).as(nombre + " usa el id del evento (primer parámetro)")
                    .isEqualTo("#" + metodo.getParameters()[0].getName());
        }
    }

    private void verificaQueInvalida(EstadoEvento inicial, Consumer<Long> operacion, EstadoEvento esperado) {
        Evento evento = guardar(inicial, LocalDateTime.now().plusDays(10));
        assertThat(eventoService.obtenerEvento(evento.getId(), null, false).getEstado()).isEqualTo(inicial);
        assertThat(cache().containsKey(evento.getId())).isTrue();

        operacion.accept(evento.getId());

        assertThat(cache().containsKey(evento.getId())).isFalse();
        assertThat(eventoService.obtenerEvento(evento.getId(), null, false).getEstado()).isEqualTo(esperado);
    }

    private RMapCache<Object, Object> cache() {
        return redissonCacheClient.getMapCache(EventoDetalleCache.CACHE);
    }

    private Evento guardar(EstadoEvento estado, LocalDateTime fecha) {
        return eventoRepository.save(Evento.builder()
                .nombre("Recital")
                .descripcion("Detalle")
                .tipo(TipoEvento.RECITAL)
                .fecha(fecha)
                .estado(estado)
                .organizadorId(7L)
                .lugar(lugar)
                .build());
    }
}
