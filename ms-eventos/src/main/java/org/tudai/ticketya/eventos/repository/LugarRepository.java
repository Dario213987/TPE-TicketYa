package org.tudai.ticketya.eventos.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.tudai.ticketya.eventos.domain.Lugar;

@Repository
public interface LugarRepository extends JpaRepository<Lugar, Long> {
}