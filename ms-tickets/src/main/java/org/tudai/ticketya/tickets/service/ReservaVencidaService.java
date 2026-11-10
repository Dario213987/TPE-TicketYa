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

        for (Ticket ticket : vencidas) {
            ticket.setEstadoTicket(EstadoTicket.EXPIRADO);
            if (ticket.getAsiento() != null) {
                ticket.getAsiento().setEstado(EstadoDisponibilidad.LIBRE);
                ticket.setAsiento(null);
            }
            DisponibilidadSector sector = ticket.getSector();
            if (sector.getDisponibles() < sector.getCapacidadTotal()) {
                sector.setDisponibles(sector.getDisponibles() + 1);
            }
        }

        ticketRepository.saveAll(vencidas);
    }
}
