package org.tudai.ticketya.tickets.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.tudai.ticketya.tickets.domain.DisponibilidadEvento;

import java.time.Instant;

public interface DisponibilidadEventoRepository extends JpaRepository<DisponibilidadEvento, Long> {

    @Modifying
    @Query(value = """
            INSERT INTO disponibilidad_eventos (evento_id, creado_el)
            VALUES (:eventoId, :creadoEl)
            ON CONFLICT (evento_id) DO NOTHING
            """, nativeQuery = true)
    int insertarSiNoExiste(@Param("eventoId") Long eventoId, @Param("creadoEl") Instant creadoEl);
}
