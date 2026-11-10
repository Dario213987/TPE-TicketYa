package org.tudai.ticketya.eventos.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;
import org.tudai.ticketya.eventos.client.TicketsClient;
import org.tudai.ticketya.eventos.domain.EstadoNotificacion;
import org.tudai.ticketya.eventos.domain.EventoNotificacion;
import org.tudai.ticketya.eventos.domain.TipoNotificacionTickets;
import org.tudai.ticketya.eventos.repository.EventoNotificacionRepository;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificacionTicketsServiceTest {

    @Mock
    private EventoNotificacionRepository notificacionRepository;

    @Mock
    private TicketsClient ticketsClient;

    @InjectMocks
    private NotificacionTicketsService service;

    @Test
    void encolar_guardaUnaNotificacionPendiente() {
        when(notificacionRepository.existsByEventoIdAndTipo(1L, TipoNotificacionTickets.FINALIZAR))
                .thenReturn(false);

        service.encolar(1L, TipoNotificacionTickets.FINALIZAR);

        ArgumentCaptor<EventoNotificacion> captor = ArgumentCaptor.forClass(EventoNotificacion.class);
        verify(notificacionRepository).save(captor.capture());
        EventoNotificacion notificacion = captor.getValue();
        assertThat(notificacion.getEventoId()).isEqualTo(1L);
        assertThat(notificacion.getTipo()).isEqualTo(TipoNotificacionTickets.FINALIZAR);
        assertThat(notificacion.getEstado()).isEqualTo(EstadoNotificacion.PENDIENTE);
        assertThat(notificacion.getIntentos()).isZero();
        assertThat(notificacion.getProximoIntento()).isNotNull();
    }

    @Test
    void encolar_siYaExiste_noDuplica() {
        when(notificacionRepository.existsByEventoIdAndTipo(1L, TipoNotificacionTickets.FINALIZAR))
                .thenReturn(true);

        service.encolar(1L, TipoNotificacionTickets.FINALIZAR);

        verify(notificacionRepository, never()).save(any(EventoNotificacion.class));
    }

    @Test
    void procesarPendientes_enviaYMarcaComoEnviada() {
        EventoNotificacion notificacion = notificacionPendiente(0);
        when(notificacionRepository
                .findByEstadoAndProximoIntentoLessThanEqualOrderByCreadoElAsc(
                        eq(EstadoNotificacion.PENDIENTE), any(Instant.class), any(Pageable.class)))
                .thenReturn(List.of(notificacion));

        service.procesarPendientes();

        verify(ticketsClient).finalizarEvento(1L);
        assertThat(notificacion.getEstado()).isEqualTo(EstadoNotificacion.ENVIADO);
        assertThat(notificacion.getProximoIntento()).isNull();
        verify(notificacionRepository).save(notificacion);
    }

    @Test
    void procesarPendientes_siFalla_reprogramaElReintento() {
        EventoNotificacion notificacion = notificacionPendiente(0);
        when(notificacionRepository
                .findByEstadoAndProximoIntentoLessThanEqualOrderByCreadoElAsc(
                        eq(EstadoNotificacion.PENDIENTE), any(Instant.class), any(Pageable.class)))
                .thenReturn(List.of(notificacion));
        doThrow(new RuntimeException("ms-tickets caído")).when(ticketsClient).finalizarEvento(1L);

        service.procesarPendientes();

        assertThat(notificacion.getEstado()).isEqualTo(EstadoNotificacion.PENDIENTE);
        assertThat(notificacion.getIntentos()).isEqualTo(1);
        assertThat(notificacion.getProximoIntento()).isAfter(Instant.now());
        verify(notificacionRepository).save(notificacion);
    }

    @Test
    void procesarPendientes_alLlegarAlMaximoDeIntentos_marcaFallida() {
        ReflectionTestUtils.setField(service, "maxIntentos", 2);
        EventoNotificacion notificacion = notificacionPendiente(1);
        when(notificacionRepository
                .findByEstadoAndProximoIntentoLessThanEqualOrderByCreadoElAsc(
                        eq(EstadoNotificacion.PENDIENTE), any(Instant.class), any(Pageable.class)))
                .thenReturn(List.of(notificacion));
        doThrow(new RuntimeException("ms-tickets caído")).when(ticketsClient).finalizarEvento(1L);

        service.procesarPendientes();

        assertThat(notificacion.getEstado()).isEqualTo(EstadoNotificacion.FALLIDO);
        assertThat(notificacion.getIntentos()).isEqualTo(2);
        assertThat(notificacion.getProximoIntento()).isNull();
        verify(notificacionRepository).save(notificacion);
    }

    @Test
    void procesarPendientes_sinPendientes_noLlamaATickets() {
        when(notificacionRepository
                .findByEstadoAndProximoIntentoLessThanEqualOrderByCreadoElAsc(
                        eq(EstadoNotificacion.PENDIENTE), any(Instant.class), any(Pageable.class)))
                .thenReturn(List.of());

        service.procesarPendientes();

        verifyNoInteractions(ticketsClient);
        verify(notificacionRepository, never()).save(any(EventoNotificacion.class));
    }

    private static EventoNotificacion notificacionPendiente(int intentos) {
        return EventoNotificacion.builder()
                .id(1L)
                .eventoId(1L)
                .tipo(TipoNotificacionTickets.FINALIZAR)
                .estado(EstadoNotificacion.PENDIENTE)
                .intentos(intentos)
                .proximoIntento(Instant.now().minusSeconds(60))
                .build();
    }
}
