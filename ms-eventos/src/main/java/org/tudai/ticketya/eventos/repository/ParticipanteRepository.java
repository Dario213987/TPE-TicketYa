package org.tudai.ticketya.eventos.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.tudai.ticketya.eventos.domain.Participante;

public interface ParticipanteRepository extends JpaRepository<Participante, Long>{
}
