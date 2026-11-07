package org.tudai.ticketya.eventos.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.tudai.ticketya.contract.eventos.model.EstadoEvento;
import org.tudai.ticketya.eventos.domain.Evento;

import java.util.Collection;
import java.util.Optional;

@Repository
public interface EventoRepository extends JpaRepository<Evento, Long> {

    Page<Evento> findByEstadoIn(Collection<EstadoEvento> estados, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select evento from Evento evento where evento.id = :id")
    Optional<Evento> findByIdForUpdate(@Param("id") Long id);
}
