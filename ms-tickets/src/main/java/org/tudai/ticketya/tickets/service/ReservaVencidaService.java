package org.tudai.ticketya.tickets.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.tudai.ticketya.tickets.domain.DisponibilidadSector;
import org.tudai.ticketya.tickets.domain.EstadoDisponibilidad;
import org.tudai.ticketya.tickets.domain.EstadoTicket;
import org.tudai.ticketya.tickets.domain.Ticket;
import org.tudai.ticketya.tickets.repository.TicketRepository;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ReservaVencidaService {

    private final TicketRepository ticketRepository;

    @Transactional
    public void liberarVencidas(Long eventoId, Instant ahora) {
        List<Ticket> vencidas = ticketRepository
                .findByEvento_EventoIdAndEstadoTicketAndExpiraElLessThanEqual(
                        eventoId, EstadoTicket.RESERVADO, ahora);

        if (vencidas.isEmpty()) {
            return;
        }

        liberar(vencidas, EstadoTicket.EXPIRADO);
    }

    /**
     * Libera los lugares tomados por los tickets: los asientos vuelven a LIBRE, el sector
     * recupera la capacidad y cada ticket queda en el estado final indicado.
     *
     * @param tickets     tickets a liberar
     * @param estadoFinal estado en el que quedan los tickets (EXPIRADO, CANCELADO)
     */
    @Transactional
    public void liberar(List<Ticket> tickets, EstadoTicket estadoFinal) {
        for (Ticket ticket : tickets) {
            ticket.setEstadoTicket(estadoFinal);
            if (ticket.getAsiento() != null) {
                ticket.getAsiento().setEstado(EstadoDisponibilidad.LIBRE);
                ticket.setAsiento(null);
            }
            DisponibilidadSector sector = ticket.getSector();
            if (sector.getDisponibles() < sector.getCapacidadTotal()) {
                sector.setDisponibles(sector.getDisponibles() + 1);
            }
        }

        ticketRepository.saveAll(tickets);
    }
}
