package org.tudai.ticketya.eventos.repository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.tudai.ticketya.contract.eventos.model.EstadoEvento;
import org.tudai.ticketya.eventos.domain.EstadoNotificacion;
import org.tudai.ticketya.eventos.domain.Evento;
import org.tudai.ticketya.eventos.domain.EventoNotificacion;
import org.tudai.ticketya.eventos.domain.TipoNotificacionTickets;
import org.tudai.ticketya.eventos.service.EventoService;

import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "eureka.client.enabled=false",
        "spring.datasource.password=ignored",
        "spring.liquibase.contexts=prod",
        "ticketya.eventos.finalizacion.enabled=false",
        "ticketya.eventos.notificaciones.enabled=false",
        "ticketya.logging.dir=target/test-logs"
})
@Testcontainers
class EventoRepositoryEstadosVencidosTest {

    private static final Set<EstadoEvento> ESTADOS_FINALIZABLES = EnumSet.of(
            EstadoEvento.PUBLICADO,
            EstadoEvento.AGOTADO,
            EstadoEvento.SUSPENDIDO);

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    EventoRepository eventoRepository;

    @Autowired
    EventoNotificacionRepository notificacionRepository;

    @Autowired
    EventoService eventoService;

    @BeforeEach
    void limpiarBase() {
        notificacionRepository.deleteAllInBatch();
        eventoRepository.deleteAllInBatch();
    }

    @Test
    void findIdsEventosVencidos_soloDevuelveEstadosFinalizablesConFechaAnteriorAlLimite() {
        LocalDateTime limite = LocalDateTime.now().minusHours(6).withNano(0);
        Evento publicado = guardar("publicado", limite.minusMinutes(1), EstadoEvento.PUBLICADO);
        Evento agotado = guardar("agotado", limite.minusMinutes(1), EstadoEvento.AGOTADO);
        Evento suspendido = guardar("suspendido", limite.minusMinutes(1), EstadoEvento.SUSPENDIDO);
        guardar("borrador", limite.minusMinutes(1), EstadoEvento.BORRADOR);
        guardar("cancelado", limite.minusMinutes(1), EstadoEvento.CANCELADO);
        guardar("finalizado", limite.minusMinutes(1), EstadoEvento.FINALIZADO);
        guardar("futuro", limite.plusMinutes(1), EstadoEvento.PUBLICADO);
        guardar("exacto", limite, EstadoEvento.PUBLICADO);

        List<Long> ids = eventoRepository.findIdsEventosVencidos(limite, ESTADOS_FINALIZABLES);

        assertThat(ids).containsExactlyInAnyOrder(
                publicado.getId(),
                agotado.getId(),
                suspendido.getId());
    }

    @Test
    void finalizarEventosVencidos_actualizaSoloElegiblesYEncolaUnaNotificacionPorEvento() {
        LocalDateTime ahora = LocalDateTime.now();
        Evento publicado = guardar("publicado", ahora.minusHours(7), EstadoEvento.PUBLICADO);
        Evento agotado = guardar("agotado", ahora.minusHours(7), EstadoEvento.AGOTADO);
        Evento suspendido = guardar("suspendido", ahora.minusHours(7), EstadoEvento.SUSPENDIDO);
        Evento futuro = guardar("futuro", ahora.plusHours(1), EstadoEvento.PUBLICADO);
        Evento borrador = guardar("borrador", ahora.minusHours(7), EstadoEvento.BORRADOR);
        Evento cancelado = guardar("cancelado", ahora.minusHours(7), EstadoEvento.CANCELADO);
        Evento finalizado = guardar("finalizado", ahora.minusHours(7), EstadoEvento.FINALIZADO);

        eventoService.finalizarEventosVencidos();

        assertThat(estadoDe(publicado)).isEqualTo(EstadoEvento.FINALIZADO);
        assertThat(estadoDe(agotado)).isEqualTo(EstadoEvento.FINALIZADO);
        assertThat(estadoDe(suspendido)).isEqualTo(EstadoEvento.FINALIZADO);
        assertThat(estadoDe(futuro)).isEqualTo(EstadoEvento.PUBLICADO);
        assertThat(estadoDe(borrador)).isEqualTo(EstadoEvento.BORRADOR);
        assertThat(estadoDe(cancelado)).isEqualTo(EstadoEvento.CANCELADO);
        assertThat(estadoDe(finalizado)).isEqualTo(EstadoEvento.FINALIZADO);

        List<EventoNotificacion> notificaciones = notificacionRepository.findAll();
        assertThat(notificaciones).hasSize(3);
        assertThat(notificaciones)
                .extracting(EventoNotificacion::getEventoId)
                .containsExactlyInAnyOrder(publicado.getId(), agotado.getId(), suspendido.getId());
        assertThat(notificaciones).allSatisfy(notificacion -> {
            assertThat(notificacion.getTipo()).isEqualTo(TipoNotificacionTickets.FINALIZAR);
            assertThat(notificacion.getEstado()).isEqualTo(EstadoNotificacion.PENDIENTE);
        });

        eventoService.finalizarEventosVencidos();

        assertThat(notificacionRepository.count()).isEqualTo(3);
    }

    private Evento guardar(String nombre, LocalDateTime fecha, EstadoEvento estado) {
        return eventoRepository.save(Evento.builder()
                .nombre(nombre)
                .fecha(fecha)
                .estado(estado)
                .organizadorId(7L)
                .build());
    }

    private EstadoEvento estadoDe(Evento evento) {
        return eventoRepository.findById(evento.getId()).orElseThrow().getEstado();
    }
}
