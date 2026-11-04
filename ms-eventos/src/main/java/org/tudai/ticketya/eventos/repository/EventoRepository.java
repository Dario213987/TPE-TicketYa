package org.tudai.ticketya.eventos.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;
import org.tudai.ticketya.eventos.domain.EstadoEvento;
import org.tudai.ticketya.eventos.domain.Evento;

import java.util.Collection;

@Repository
public interface EventoRepository extends JpaRepository<Evento, Long>, JpaSpecificationExecutor<Evento>, EventoRepositoryCustom {

    Page<Evento> findByEstadoIn(Collection<EstadoEvento> estados, Pageable pageable);
}
