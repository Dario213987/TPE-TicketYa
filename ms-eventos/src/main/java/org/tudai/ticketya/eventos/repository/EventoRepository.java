package org.tudai.ticketya.eventos.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.tudai.ticketya.eventos.domain.Evento;

public interface EventoRepository extends JpaRepository<Evento, Long> {
}
