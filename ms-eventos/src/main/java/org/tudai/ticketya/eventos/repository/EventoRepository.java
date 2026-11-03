package org.tudai.ticketya.eventos.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import org.tudai.ticketya.eventos.domain.EstadoEvento;
import org.tudai.ticketya.eventos.domain.Evento;

import java.time.LocalDate;
import java.util.Collection;

@Repository
public interface EventoRepository extends JpaRepository<Evento, Long> {

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query("""
            UPDATE Evento e
               SET e.estado = :estadoFinalizado
             WHERE e.fecha < :hoy
               AND e.estado NOT IN :estadosExcluidos
            """)
    int finalizarEventosVencidos(@Param("hoy") LocalDate hoy,
                                 @Param("estadoFinalizado") EstadoEvento estadoFinalizado,
                                 @Param("estadosExcluidos") Collection<EstadoEvento> estadosExcluidos);
}
