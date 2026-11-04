package org.tudai.ticketya.tickets.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.tudai.ticketya.tickets.domain.DisponibilidadAsiento;
import org.tudai.ticketya.tickets.domain.DisponibilidadEvento;
import org.tudai.ticketya.tickets.domain.DisponibilidadSector;
import org.tudai.ticketya.tickets.domain.EstadoDisponibilidad;
import org.tudai.ticketya.tickets.domain.EstadoTicket;
import org.tudai.ticketya.tickets.domain.Ticket;
import org.tudai.ticketya.tickets.domain.TipoSector;
import org.tudai.ticketya.tickets.repository.TicketRepository;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReservaVencidaServiceTest {

    @Mock
    private TicketRepository ticketRepository;

    private ReservaVencidaService service;

    @BeforeEach
    void setUp() {
        service = new ReservaVencidaService(ticketRepository);
    }

    @Test
    void liberarVencidas_cuandoNoHayReservasVencidas_noGuardaNada() {
        Instant ahora = Instant.now();
        when(ticketRepository.findByEvento_EventoIdAndEstadoTicketAndExpiraElLessThanEqual(
                7L, EstadoTicket.RESERVADO, ahora)).thenReturn(List.of());

        service.liberarVencidas(7L, ahora);

        verify(ticketRepository, never()).saveAll(any());
    }

    @Test
    void liberarVencidas_marcaLaReservacionYLiberaElAsiento() {
        Instant ahora = Instant.now();
        DisponibilidadEvento evento = DisponibilidadEvento.builder()
                .eventoId(7L)
                .creadoEl(Instant.now())
                .build();
        DisponibilidadSector sector = DisponibilidadSector.builder()
                .id(10L)
                .evento(evento)
                .sectorIdExterno(3L)
                .tipo(TipoSector.NUMERADO)
                .capacidadTotal(3)
                .disponibles(2)
                .build();
        DisponibilidadAsiento asiento = DisponibilidadAsiento.builder()
                .fila(1)
                .numero(1)
                .sector(sector)
                .evento(evento)
                .estado(EstadoDisponibilidad.RESERVADO)
                .build();
        Ticket ticket = Ticket.builder()
                .estadoTicket(EstadoTicket.RESERVADO)
                .expiraEl(ahora.minusSeconds(60))
                .evento(evento)
                .sector(sector)
                .asiento(asiento)
                .build();
        when(ticketRepository.findByEvento_EventoIdAndEstadoTicketAndExpiraElLessThanEqual(
                7L, EstadoTicket.RESERVADO, ahora)).thenReturn(List.of(ticket));

        service.liberarVencidas(7L, ahora);

        assertThat(ticket.getEstadoTicket()).isEqualTo(EstadoTicket.EXPIRADO);
        assertThat(asiento.getEstado()).isEqualTo(EstadoDisponibilidad.LIBRE);
        assertThat(sector.getDisponibles()).isEqualTo(3);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Ticket>> captor = ArgumentCaptor.forClass(List.class);
        verify(ticketRepository).saveAll(captor.capture());
        assertThat(captor.getValue()).containsExactly(ticket);
    }

    @Test
    void liberarVencidas_noSuperaLaCapacidadDelSector() {
        Instant ahora = Instant.now();
        DisponibilidadSector sector = DisponibilidadSector.builder()
                .id(10L)
                .sectorIdExterno(3L)
                .tipo(TipoSector.NO_NUMERADO)
                .capacidadTotal(10)
                .disponibles(10)
                .build();
        Ticket ticket = Ticket.builder()
                .estadoTicket(EstadoTicket.RESERVADO)
                .expiraEl(ahora.minusSeconds(60))
                .sector(sector)
                .build();
        when(ticketRepository.findByEvento_EventoIdAndEstadoTicketAndExpiraElLessThanEqual(
                eq(7L), eq(EstadoTicket.RESERVADO), eq(ahora))).thenReturn(List.of(ticket));

        service.liberarVencidas(7L, ahora);

        assertThat(sector.getDisponibles()).isEqualTo(10);
        assertThat(ticket.getEstadoTicket()).isEqualTo(EstadoTicket.EXPIRADO);
    }
}
