package org.tudai.ticketya.devoluciones.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.tudai.ticketya.devoluciones.domain.EstadoReintegroMasivo;
import org.tudai.ticketya.devoluciones.domain.ReintegroMasivo;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface ReintegroMasivoRepository extends JpaRepository<ReintegroMasivo, Long> {

    Optional<ReintegroMasivo> findByEventoId(Long eventoId);

    boolean existsByEventoId(Long eventoId);

    List<ReintegroMasivo> findByEstadoOrderByIdAsc(EstadoReintegroMasivo estado, Pageable pageable);

    /**
     * Toma el proceso para esta ejecución. Solo un job puede ganar el update, lo que evita
     * que dos instancias reintegren la misma entrada al mismo tiempo.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update ReintegroMasivo r
               set r.estado = :enProceso, r.reclamadoEl = :ahora
             where r.id = :id
               and r.estado = :pendiente
            """)
    int reclamar(@Param("id") Long id,
                 @Param("enProceso") EstadoReintegroMasivo enProceso,
                 @Param("pendiente") EstadoReintegroMasivo pendiente,
                 @Param("ahora") Instant ahora);

    /**
     * Devuelve al estado PENDIENTE los procesos que quedaron EN_PROCESO porque la ejecución
     * se interrumpió antes de registrar el resultado del lote.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update ReintegroMasivo r
               set r.estado = :pendiente, r.reclamadoEl = null
             where r.estado = :enProceso
               and r.reclamadoEl < :limite
            """)
    int liberarBloqueosVencidos(@Param("enProceso") EstadoReintegroMasivo enProceso,
                                @Param("pendiente") EstadoReintegroMasivo pendiente,
                                @Param("limite") Instant limite);

    List<ReintegroMasivo> findAllByEstado(EstadoReintegroMasivo estado);
}
