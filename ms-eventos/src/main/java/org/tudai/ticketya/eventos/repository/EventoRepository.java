package org.tudai.ticketya.eventos.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.tudai.ticketya.eventos.domain.EstadoEvento;
import org.tudai.ticketya.eventos.domain.Evento;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

@Repository
public interface EventoRepository extends JpaRepository<Evento, Long> {

    Page<Evento> findByEstadoIn(Collection<EstadoEvento> estados, Pageable pageable);

    @Query("""
            SELECT e.id
              FROM Evento e
             WHERE e.fecha < :hoy
               AND e.estado IN :estadosFinalizables
            """)
    List<Long> findIdsEventosVencidos(@Param("hoy") LocalDate hoy,
                                      @Param("estadosFinalizables") Collection<EstadoEvento> estadosFinalizables);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE Evento e
               SET e.estado = :estadoFinalizado
             WHERE e.fecha < :hoy
               AND e.estado IN :estadosFinalizables
            """)
    int finalizarEventosVencidos(@Param("hoy") LocalDate hoy,
                                 @Param("estadoFinalizado") EstadoEvento estadoFinalizado,
                                 @Param("estadosFinalizables") Collection<EstadoEvento> estadosFinalizables);
}
