package org.tudai.ticketya.tickets.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.tudai.ticketya.tickets.domain.DisponibilidadEvento;

import java.time.Instant;
import java.util.Optional;

public interface DisponibilidadEventoRepository extends JpaRepository<DisponibilidadEvento, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from DisponibilidadEvento e where e.eventoId = :eventoId")
    Optional<DisponibilidadEvento> findByIdForUpdate(@Param("eventoId") Long eventoId);

    @Modifying
    @Query(value = """
            INSERT INTO disponibilidad_eventos
                (evento_id, creado_el, limite_entradas_por_cliente, alta_demanda)
            VALUES (:eventoId, :creadoEl, :limiteEntradasPorCliente, :altaDemanda)
            ON CONFLICT (evento_id) DO NOTHING
            """, nativeQuery = true)
    int insertarSiNoExiste(@Param("eventoId") Long eventoId,
                           @Param("creadoEl") Instant creadoEl,
                           @Param("limiteEntradasPorCliente") Integer limiteEntradasPorCliente,
                           @Param("altaDemanda") boolean altaDemanda);
}
