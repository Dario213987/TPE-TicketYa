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

            liberarLugar(ticket, EstadoTicket.LIBERADO);
            liberables.add(ticket);
        }

        if (!liberables.isEmpty()) {
            ticketRepository.saveAll(liberables);
        }
        return liberables.size();
    }

    /**
     * Libera todas las reservas vigentes de un evento cuando se cancela. A diferencia de
     * {@link #liberarVencidas(Long, Instant)}, no espera a que venza el plazo: libera el lugar
     * y deja los tickets CANCELADO. Es idempotente porque solo toma tickets RESERVADO o
     * PENDIENTE_PAGO y ninguno de esos estados vuelve a aparecer en la consulta.
     *
     * @param eventoId id del evento cancelado
     * @param ahora    instante a partir del cual se considera vigente la reserva
     *
     * @return cantidad de reservas liberadas
     */
    @Transactional
    public int liberarReservasVigentes(Long eventoId, Instant ahora) {
        if (eventoRepository != null) {
            eventoRepository.findByIdForUpdate(eventoId).orElse(null);
        }

        List<Ticket> vigentes = ticketRepository
                .findByEvento_EventoIdAndEstadoTicketInAndExpiraElGreaterThan(
                        eventoId, List.of(EstadoTicket.RESERVADO, EstadoTicket.PENDIENTE_PAGO), ahora);

        List<Ticket> liberables = new ArrayList<>(vigentes.size());
        for (Ticket ticket : vigentes) {
            if (ticket.isPagoEnCurso() || enPago(ticket)) {
                continue;
            }
            liberables.add(ticket);
        }

        if (!liberables.isEmpty()) {
            liberar(liberables, EstadoTicket.CANCELADO);
        }
        return liberables.size();
    }

    /**
     * Libera los lugares tomados por los tickets: los asientos vuelven a LIBRE, el sector
     * recupera la capacidad y cada ticket queda en el estado final indicado.
     *
     * @param tickets     tickets a liberar
     * @param estadoFinal estado en el que quedan los tickets (por ejemplo, CANCELADO)
     */
    @Transactional
    public void liberar(List<Ticket> tickets, EstadoTicket estadoFinal) {
        for (Ticket ticket : tickets) {
            liberarLugar(ticket, estadoFinal);
        }
        ticketRepository.saveAll(tickets);
    }

    private void liberarLugar(Ticket ticket, EstadoTicket estadoFinal) {
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

    private boolean enPago(Ticket ticket) {
        Reserva reserva = ticket.getReserva();
        return reserva != null && reserva.isPagoEnCurso();
    }
}
