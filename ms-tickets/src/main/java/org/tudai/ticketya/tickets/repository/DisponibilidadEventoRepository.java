package org.tudai.ticketya.tickets.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.tudai.ticketya.tickets.domain.DisponibilidadEvento;

public interface DisponibilidadEventoRepository extends JpaRepository<DisponibilidadEvento, Long> {
}
