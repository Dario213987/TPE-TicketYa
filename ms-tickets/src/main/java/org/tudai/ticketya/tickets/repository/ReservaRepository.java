package org.tudai.ticketya.tickets.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.tudai.ticketya.tickets.domain.Reserva;

public interface ReservaRepository extends JpaRepository<Reserva, Long> {
}
