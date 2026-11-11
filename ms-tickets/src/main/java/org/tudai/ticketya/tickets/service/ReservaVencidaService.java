package org.tudai.ticketya.tickets.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.tudai.ticketya.tickets.domain.DisponibilidadSector;
import org.tudai.ticketya.tickets.domain.EstadoDisponibilidad;
import org.tudai.ticketya.tickets.domain.EstadoTicket;
import org.tudai.ticketya.tickets.domain.Reserva;
import org.tudai.ticketya.tickets.domain.Ticket;
import org.tudai.ticketya.tickets.repository.DisponibilidadEventoRepository;
import org.tudai.ticketya.tickets.repository.TicketRepository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Service
public class ReservaVencidaService {

    private final TicketRepository ticketRepository;
    private final DisponibilidadEventoRepository eventoRepository;

    public ReservaVencidaService(TicketRepository ticketRepository) {
        this(ticketRepository, null);
    }

    @Autowired
    public ReservaVencidaService(TicketRepository ticketRepository,
                                 DisponibilidadEventoRepository eventoRepository) {
        this.ticketRepository = ticketRepository;
        this.eventoRepository = eventoRepository;
    }

    /**
     * Libera todas las reservas vencidas de todos los eventos. Pensado para el job periodico.
     *
     * @return cantidad de tickets liberados
     */
    @Transactional
    public int liberarVencidas(Instant ahora) {
        List<Long> eventos = ticketRepository.findEventosConReservasVencidas(
                EstadoTicket.RESERVADO, ahora);

        int liberadas = 0;
        for (Long eventoId : eventos) {
            liberadas += liberarEvento(eventoId, ahora);
        }
        return liberadas;
    }

    /**
     * Libera las reservas vencidas de un evento. Se usa de forma perezosa antes de reservar
     * y antes de consultar la disponibilidad.
     */
    @Transactional
    public void liberarVencidas(Long eventoId, Instant ahora) {
        liberarEvento(eventoId, ahora);
    }

    private int liberarEvento(Long eventoId, Instant ahora) {
        if (eventoRepository != null) {
            // Mismo bloqueo que usa la reserva, para no pisar el contador del sector.
            eventoRepository.findByIdForUpdate(eventoId).orElse(null);
        }

        List<Ticket> vencidas = ticketRepository
                .findByEvento_EventoIdAndEstadoTicketAndExpiraElLessThanEqual(
                        eventoId, EstadoTicket.RESERVADO, ahora);

        List<Ticket> liberables = new ArrayList<>(vencidas.size());
        for (Ticket ticket : vencidas) {
            if (ticket.isPagoEnCurso() || enPago(ticket)) {
                continue;
            }

            ticket.setEstadoTicket(EstadoTicket.LIBERADO);
            if (ticket.getAsiento() != null) {
                ticket.getAsiento().setEstado(EstadoDisponibilidad.LIBRE);
                ticket.setAsiento(null);
            }

            DisponibilidadSector sector = ticket.getSector();
            if (sector.getDisponibles() < sector.getCapacidadTotal()) {
                sector.setDisponibles(sector.getDisponibles() + 1);
            }
            liberables.add(ticket);
        }

        if (!liberables.isEmpty()) {
            ticketRepository.saveAll(liberables);
        }
        return liberables.size();
    }

    private boolean enPago(Ticket ticket) {
        Reserva reserva = ticket.getReserva();
        return reserva != null && reserva.isPagoEnCurso();
    }
}
