package org.tudai.ticketya.eventos.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.tudai.ticketya.contract.eventos.model.EstadoEvento;
import org.tudai.ticketya.eventos.domain.Evento;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface EventoRepository extends JpaRepository<Evento, Long>, JpaSpecificationExecutor<Evento> {

    Page<Evento> findByEstadoIn(Collection<EstadoEvento> estados, Pageable pageable);

    @Query("""
            SELECT e.id
              FROM Evento e
             WHERE e.fecha < :limite
               AND e.estado IN :estadosFinalizables
            """)
    List<Long> findIdsEventosVencidos(@Param("limite") LocalDateTime limite,
                                      @Param("estadosFinalizables") Collection<EstadoEvento> estadosFinalizables);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE Evento e
               SET e.estado = :estadoFinalizado
             WHERE e.fecha < :limite
               AND e.estado IN :estadosFinalizables
            """)
    int finalizarEventosVencidos(@Param("limite") LocalDateTime limite,
                                 @Param("estadoFinalizado") EstadoEvento estadoFinalizado,
                                 @Param("estadosFinalizables") Collection<EstadoEvento> estadosFinalizables);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select evento from Evento evento where evento.id = :id")
    Optional<Evento> findByIdForUpdate(@Param("id") Long id);
}
