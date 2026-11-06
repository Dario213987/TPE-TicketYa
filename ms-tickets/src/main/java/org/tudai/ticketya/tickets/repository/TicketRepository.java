package org.tudai.ticketya.tickets.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.tudai.ticketya.tickets.domain.EstadoTicket;
import org.tudai.ticketya.tickets.domain.Ticket;

import java.util.Collection;

public interface TicketRepository extends JpaRepository<Ticket, Long> {

    long countByDisponibilidad_IdAndEstadoTicketNotIn(
            Long disponibilidadId, Collection<EstadoTicket> estados);
}
