package org.tudai.ticketya.tickets.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.tudai.ticketya.tickets.domain.BloqueoJob;

import java.time.Instant;

public interface BloqueoJobRepository extends JpaRepository<BloqueoJob, String> {

    // INSERT ... WHERE NOT EXISTS funciona tanto en PostgreSQL como en H2 (modo PostgreSQL),
    // a diferencia de ON CONFLICT, que H2 no soporta. La PK de nombre resuelve la carrera
    // entre instancias: la que pierde recibe una violacion de unicidad.
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @Modifying
    @Query(value = """
            INSERT INTO bloqueos_job (nombre, propietario, adquirido_el, expira_el)
            SELECT :nombre, :propietario, :adquiridoEl, :expiraEl
             WHERE NOT EXISTS (SELECT 1 FROM bloqueos_job WHERE nombre = :nombre)
            """, nativeQuery = true)
    int insertarSiNoExiste(@Param("nombre") String nombre,
                           @Param("propietario") String propietario,
                           @Param("adquiridoEl") Instant adquiridoEl,
                           @Param("expiraEl") Instant expiraEl);

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @Modifying
    @Query(value = """
            UPDATE bloqueos_job
               SET propietario = :propietario,
                   adquirido_el = :adquiridoEl,
                   expira_el = :expiraEl
             WHERE nombre = :nombre
               AND expira_el <= :ahora
            """, nativeQuery = true)
    int tomarSiExpirado(@Param("nombre") String nombre,
                        @Param("propietario") String propietario,
                        @Param("adquiridoEl") Instant adquiridoEl,
                        @Param("expiraEl") Instant expiraEl,
                        @Param("ahora") Instant ahora);

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @Modifying
    @Query(value = """
            DELETE FROM bloqueos_job
             WHERE nombre = :nombre
               AND propietario = :propietario
            """, nativeQuery = true)
    int liberar(@Param("nombre") String nombre,
                @Param("propietario") String propietario);
}
