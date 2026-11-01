package org.tudai.ticketya.tickets.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.tudai.ticketya.tickets.domain.DisponibilidadAsiento;
import org.tudai.ticketya.tickets.domain.EstadoDisponibilidad;

import java.util.Optional;

public interface DisponibilidadAsientoRepository extends JpaRepository<DisponibilidadAsiento, Long> {

    Optional<DisponibilidadAsiento> findBySector_IdAndFilaAndNumero(Long sectorId, Integer fila, Integer numero);

    long countBySector_IdAndEstado(Long sectorId, EstadoDisponibilidad estado);
}
