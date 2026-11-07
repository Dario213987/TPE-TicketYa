package org.tudai.ticketya.eventos.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.modelmapper.ModelMapper;
import org.springframework.transaction.support.TransactionTemplate;
import org.tudai.ticketya.contract.eventos.model.EstadoEvento;
import org.tudai.ticketya.contract.eventos.model.EventoEstadoResponse;
import org.tudai.ticketya.core.exception.ConflictException;
import org.tudai.ticketya.core.exception.NotFoundException;
import org.tudai.ticketya.eventos.client.TicketsClient;
import org.tudai.ticketya.eventos.domain.Evento;
import org.tudai.ticketya.eventos.domain.TipoNotificacionTickets;
import org.tudai.ticketya.eventos.repository.EventoRepository;
import org.tudai.ticketya.eventos.repository.LugarRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EventoServiceEstadosAutomaticosTest {

    @Mock
    private EventoRepository eventoRepository;

    @Mock
    private LugarRepository lugarRepository;

    @Mock
    private TicketsClient ticketsClient;

    @Mock
    private ModelMapper modelMapper;

    @Mock
    private NotificacionTicketsService notificacionTicketsService;

    @Mock
    private TransactionTemplate transactionTemplate;

    @InjectMocks
    private EventoService eventoService;

    @Test
    void finalizarEventosVencidos_finalizaYEncolaUnaNotificacionPorEvento() {
        LocalDate hoy = LocalDate.now();
        when(eventoRepository.findIdsEventosVencidos(eq(hoy), any()))
                .thenReturn(List.of(10L, 20L));

        eventoService.finalizarEventosVencidos();

        verify(eventoRepository).finalizarEventosVencidos(
                eq(hoy),
                eq(EstadoEvento.FINALIZADO),
                eq(Set.of(EstadoEvento.PUBLICADO, EstadoEvento.AGOTADO, EstadoEvento.SUSPENDIDO)));
        verify(notificacionTicketsService).encolar(10L, TipoNotificacionTickets.FINALIZAR);
        verify(notificacionTicketsService).encolar(20L, TipoNotificacionTickets.FINALIZAR);
        verifyNoInteractions(ticketsClient);
    }

    @Test
    void finalizarEventosVencidos_sinEventosVencidos_noActualizaNiEncola() {
        when(eventoRepository.findIdsEventosVencidos(eq(LocalDate.now()), any()))
                .thenReturn(List.of());

        eventoService.finalizarEventosVencidos();

        verify(eventoRepository, never()).finalizarEventosVencidos(any(), any(), any());
        verifyNoInteractions(notificacionTicketsService, ticketsClient);
    }

    @Test
    void marcarAgotado_enEventoPublicado_loPasaAAgotado() {
        Evento evento = eventoEnEstado(EstadoEvento.PUBLICADO);
        when(eventoRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(evento));
        when(eventoRepository.save(evento)).thenReturn(evento);

        EventoEstadoResponse response = eventoService.marcarAgotado(1L);

        assertThat(evento.getEstado()).isEqualTo(EstadoEvento.AGOTADO);
        assertThat(response.getId()).isEqualTo(1L);
        assertThat(response.getNombre()).isEqualTo("Festival");
        assertThat(response.getEstado()).isEqualTo(EstadoEvento.AGOTADO);
        verify(eventoRepository).save(evento);
        verifyNoInteractions(ticketsClient, lugarRepository);
    }

    @Test
    void marcarAgotado_yaAgotado_esIdempotenteYNoGuarda() {
        Evento evento = eventoEnEstado(EstadoEvento.AGOTADO);
        when(eventoRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(evento));

        EventoEstadoResponse response = eventoService.marcarAgotado(1L);

        assertThat(response.getId()).isEqualTo(1L);
        assertThat(response.getNombre()).isEqualTo("Festival");
        assertThat(response.getEstado()).isEqualTo(EstadoEvento.AGOTADO);
        verify(eventoRepository, never()).save(any(Evento.class));
        verifyNoInteractions(ticketsClient, lugarRepository);
    }

    @Test
    void marcarAgotado_enEstadoNoPublicado_lanzaConflictYNoGuarda() {
        for (EstadoEvento estado : List.of(
                EstadoEvento.BORRADOR,
                EstadoEvento.CANCELADO,
                EstadoEvento.FINALIZADO)) {
            Evento evento = eventoEnEstado(estado);
            when(eventoRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(evento));

            assertThatThrownBy(() -> eventoService.marcarAgotado(1L))
                    .isInstanceOf(ConflictException.class)
                    .hasMessage("Solo un evento publicado puede pasar a agotado");
        }
        verify(eventoRepository, never()).save(any(Evento.class));
        verifyNoInteractions(ticketsClient, lugarRepository);
    }

    @Test
    void marcarAgotado_conEventoInexistente_lanzaNotFound() {
        when(eventoRepository.findByIdForUpdate(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> eventoService.marcarAgotado(99L))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Evento no encontrado con id: 99");
        verify(eventoRepository, never()).save(any(Evento.class));
        verifyNoInteractions(ticketsClient, lugarRepository);
    }

    @Test
    void marcarPublicado_enEventoAgotado_loPasaAPublicado() {
        Evento evento = eventoEnEstado(EstadoEvento.AGOTADO);
        when(eventoRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(evento));
        when(eventoRepository.save(evento)).thenReturn(evento);

        EventoEstadoResponse response = eventoService.marcarPublicado(1L);

        assertThat(evento.getEstado()).isEqualTo(EstadoEvento.PUBLICADO);
        assertThat(response.getId()).isEqualTo(1L);
        assertThat(response.getNombre()).isEqualTo("Festival");
        assertThat(response.getEstado()).isEqualTo(EstadoEvento.PUBLICADO);
        verify(eventoRepository).save(evento);
        verifyNoInteractions(ticketsClient, lugarRepository);
    }

    @Test
    void marcarPublicado_yaPublicado_esIdempotenteYNoGuarda() {
        Evento evento = eventoEnEstado(EstadoEvento.PUBLICADO);
        when(eventoRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(evento));

        EventoEstadoResponse response = eventoService.marcarPublicado(1L);

        assertThat(response.getId()).isEqualTo(1L);
        assertThat(response.getEstado()).isEqualTo(EstadoEvento.PUBLICADO);
        verify(eventoRepository, never()).save(any(Evento.class));
        verifyNoInteractions(ticketsClient, lugarRepository);
    }

    @Test
    void marcarPublicado_enEstadoNoAgotado_lanzaConflictYNoGuarda() {
        for (EstadoEvento estado : List.of(
                EstadoEvento.BORRADOR,
                EstadoEvento.CANCELADO,
                EstadoEvento.FINALIZADO)) {
            Evento evento = eventoEnEstado(estado);
            when(eventoRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(evento));

            assertThatThrownBy(() -> eventoService.marcarPublicado(1L))
                    .isInstanceOf(ConflictException.class)
                    .hasMessage("Solo un evento agotado puede pasar a publicado");
        }
        verify(eventoRepository, never()).save(any(Evento.class));
        verifyNoInteractions(ticketsClient, lugarRepository);
    }

    @Test
    void marcarPublicado_conEventoInexistente_lanzaNotFound() {
        when(eventoRepository.findByIdForUpdate(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> eventoService.marcarPublicado(99L))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Evento no encontrado con id: 99");
        verify(eventoRepository, never()).save(any(Evento.class));
        verifyNoInteractions(ticketsClient, lugarRepository);
    }

    private Evento eventoEnEstado(EstadoEvento estado) {
        return Evento.builder()
                .id(1L)
                .nombre("Festival")
                .estado(estado)
                .organizadorId(7L)
                .build();
    }
}
