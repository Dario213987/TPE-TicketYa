package org.tudai.ticketya.eventos.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.tudai.ticketya.eventos.domain.Sector;

@Repository
public interface SectorRepository extends JpaRepository<Sector, Long> {
}