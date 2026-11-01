package org.tudai.ticketya.tickets.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.tudai.ticketya.tickets.domain.DisponibilidadSector;

import java.util.Optional;

public interface DisponibilidadSectorRepository extends JpaRepository<DisponibilidadSector, Long> {

    Optional<DisponibilidadSector> findByEvento_EventoIdAndSectorIdExterno(Long eventoId, Long sectorIdExterno);
}
