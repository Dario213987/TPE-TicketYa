package org.tudai.ticketya.tickets.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.tudai.ticketya.tickets.domain.Disponibilidad;

import java.util.Optional;

public interface DisponibilidadRepository extends JpaRepository<Disponibilidad, Long> {

    Optional<Disponibilidad> findByEvento_EventoIdAndSectorIdExterno(Long eventoId, Long sectorIdExterno);
}
