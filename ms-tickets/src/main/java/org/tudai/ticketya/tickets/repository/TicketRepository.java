package org.tudai.ticketya.tickets.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.tudai.ticketya.tickets.domain.EstadoTicket;
import org.tudai.ticketya.tickets.domain.Ticket;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface TicketRepository extends JpaRepository<Ticket, Long> {

    long countByEvento_EventoIdAndClienteIdAndEstadoTicketIn(
            Long eventoId, Long clienteId, Collection<EstadoTicket> estados);

    @Query("""
            select count(t) from Ticket t
            where t.evento.eventoId = :eventoId
              and t.clienteId = :clienteId
              and t.estadoTicket = :estado
              and t.expiraEl > :ahora
            """)
    long contarReservasVigentes(@Param("eventoId") Long eventoId,
                                @Param("clienteId") Long clienteId,
                                @Param("estado") EstadoTicket estado,
                                @Param("ahora") Instant ahora);

    List<Ticket> findByReserva_IdOrderByIdAsc(Long reservaId);

    @Query("""
            select t from Ticket t
            join fetch t.evento
            where t.codigo_qr = :codigoQr
            """)
    Optional<Ticket> buscarPorCodigoQr(@Param("codigoQr") String codigoQr);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update Ticket t
               set t.estadoTicket = :usado
             where t.codigo_qr = :codigoQr
               and t.evento.eventoId = :eventoId
               and t.estadoTicket = :vendido
            """)
    int marcarComoUsado(@Param("codigoQr") String codigoQr,
                        @Param("eventoId") Long eventoId,
                        @Param("vendido") EstadoTicket vendido,
                        @Param("usado") EstadoTicket usado);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<Ticket> findByEvento_EventoIdAndEstadoTicketAndExpiraElLessThanEqual(
            Long eventoId, EstadoTicket estado, Instant ahora);

    List<Ticket> findByReserva_Id(Long reservaId);

    @Query("""
            select distinct t.evento.eventoId from Ticket t
            where t.estadoTicket = :estado
              and t.expiraEl <= :ahora
            """)
    List<Long> findEventosConReservasVencidas(@Param("estado") EstadoTicket estado,
                                              @Param("ahora") Instant ahora);
}
