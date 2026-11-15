package org.tudai.ticketya.tickets.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.tudai.ticketya.tickets.domain.HabilitacionEvento;

import java.time.Instant;
import java.util.List;

public interface HabilitacionEventoRepository extends JpaRepository<HabilitacionEvento, Long> {

    @Query(value = "SELECT CURRENT_TIMESTAMP", nativeQuery = true)
    Object ahora();

    @Query(value = """
            SELECT h.evento_id
              FROM habilitacion_eventos h
              JOIN disponibilidad_eventos e ON e.evento_id = h.evento_id
             WHERE h.habilitada = TRUE
               AND e.alta_demanda = TRUE
               AND e.estado_venta = 'ABIERTA'
               AND h.proxima_tanda_el <= :ahora
             ORDER BY h.proxima_tanda_el
            """, nativeQuery = true)
    List<Long> buscarVencidos(@Param("ahora") Instant ahora);

    /**
     * Reclama la tanda del evento. Solo la instancia que actualiza la fila
     * obtiene 1; las demas obtienen 0 y no deben emitir pases.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @Modifying
    @Query(value = """
            UPDATE habilitacion_eventos
               SET proxima_tanda_el = :proxima,
                   ultima_tanda_el = :ahora
             WHERE evento_id = :eventoId
               AND habilitada = TRUE
               AND proxima_tanda_el <= :ahora
            """, nativeQuery = true)
    int reclamar(@Param("eventoId") Long eventoId,
                 @Param("ahora") Instant ahora,
                 @Param("proxima") Instant proxima);

    /**
     * Devuelve la proxima tanda al instante actual. Se usa cuando falla la
     * emision en Redis para reintentar en el siguiente tick.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @Modifying
    @Query(value = """
            UPDATE habilitacion_eventos
               SET proxima_tanda_el = :ahora
             WHERE evento_id = :eventoId
            """, nativeQuery = true)
    int reprogramar(@Param("eventoId") Long eventoId, @Param("ahora") Instant ahora);

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @Modifying
    @Query(value = """
            UPDATE habilitacion_eventos
               SET total_habilitados = total_habilitados + :cantidad
             WHERE evento_id = :eventoId
            """, nativeQuery = true)
    int incrementarHabilitados(@Param("eventoId") Long eventoId,
                               @Param("cantidad") int cantidad);

    @Transactional
    @Modifying
    @Query(value = """
            INSERT INTO habilitacion_eventos
                (evento_id, habilitada, tamano_tanda, intervalo_segundos,
                 ttl_pase_segundos, proxima_tanda_el, total_habilitados)
            SELECT :eventoId, TRUE, :tamanoTanda, :intervaloSegundos,
                   :ttlPaseSegundos, :proximaTandaEl, 0
             WHERE NOT EXISTS (SELECT 1
                                 FROM habilitacion_eventos h
                                WHERE h.evento_id = :eventoId)
            """, nativeQuery = true)
    int insertarSiNoExiste(@Param("eventoId") Long eventoId,
                           @Param("tamanoTanda") int tamanoTanda,
                           @Param("intervaloSegundos") int intervaloSegundos,
                           @Param("ttlPaseSegundos") int ttlPaseSegundos,
                           @Param("proximaTandaEl") Instant proximaTandaEl);

    /**
     * Crea la configuracion para eventos de alta demanda publicados antes de
     * esta funcionalidad. Es idempotente.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @Modifying
    @Query(value = """
            INSERT INTO habilitacion_eventos
                (evento_id, habilitada, tamano_tanda, intervalo_segundos,
                 ttl_pase_segundos, proxima_tanda_el, total_habilitados)
            SELECT e.evento_id, TRUE, :tamanoTanda, :intervaloSegundos,
                   :ttlPaseSegundos, :proximaTandaEl, 0
              FROM disponibilidad_eventos e
             WHERE e.alta_demanda = TRUE
               AND NOT EXISTS (SELECT 1
                                 FROM habilitacion_eventos h
                                WHERE h.evento_id = e.evento_id)
            """, nativeQuery = true)
    int insertarFaltantes(@Param("tamanoTanda") int tamanoTanda,
                          @Param("intervaloSegundos") int intervaloSegundos,
                          @Param("ttlPaseSegundos") int ttlPaseSegundos,
                          @Param("proximaTandaEl") Instant proximaTandaEl);

    @Transactional
    @Modifying
    @Query(value = """
            UPDATE habilitacion_eventos
               SET habilitada = FALSE
             WHERE evento_id = :eventoId
            """, nativeQuery = true)
    int deshabilitar(@Param("eventoId") Long eventoId);

    @Transactional
    @Modifying
    @Query(value = """
            UPDATE habilitacion_eventos
               SET habilitada = TRUE,
                   proxima_tanda_el = :ahora
             WHERE evento_id = :eventoId
            """, nativeQuery = true)
    int reactivar(@Param("eventoId") Long eventoId, @Param("ahora") Instant ahora);
}
