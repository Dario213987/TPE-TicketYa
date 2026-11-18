package org.tudai.ticketya.tickets.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
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

    @EntityGraph(attributePaths = {"evento", "sector", "asiento"})
    Page<Ticket> findByClienteIdAndEstadoTicketIn(Long clienteId, Collection<EstadoTicket> estados,
                                                  Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<Ticket> findByEvento_EventoIdAndEstadoTicketAndExpiraElLessThanEqual(
            Long eventoId, EstadoTicket estado, Instant ahora);

    List<Ticket> findByReserva_Id(Long reservaId);

    List<Ticket> findByEvento_EventoIdAndEstadoTicketInAndExpiraElGreaterThan(
            Long eventoId, Collection<EstadoTicket> estados, Instant ahora);

    @Query("""
            select t.id, t.clienteId, t.precio, t.estadoTicket, t.evento.organizadorId, vi.transaccionId
            from Ticket t
            left join VentaItem vi on vi.ticket.id = t.id
            where t.evento.eventoId = :eventoId
              and t.id > :desdeId
              and t.estadoTicket in :estados
            order by t.id asc
            """)
    List<Object[]> findEntradasParaReintegro(@Param("eventoId") Long eventoId,
                                              @Param("desdeId") Long desdeId,
                                              @Param("estados") Collection<EstadoTicket> estados,
                                              Pageable pageable);

    @Query("""
            select distinct t.evento.eventoId from Ticket t
            where t.estadoTicket = :estado
              and t.expiraEl <= :ahora
            """)
    List<Long> findEventosConReservasVencidas(@Param("estado") EstadoTicket estado,
                                              @Param("ahora") Instant ahora);

    /**
     * Devuelve los ids de usuario distintos que tienen al menos un ticket del evento
     * en alguno de los estados indicados.
     * <p>
     * Excluye tickets sin {@code clienteId} y ordena los ids de menor a mayor para que
     * la respuesta sea determinista. No valida la existencia del evento: si no hay
     * tickets, devuelve una lista vacia.
     *
     * @param eventoId id del evento
     * @param estados  estados de ticket que se consideran una compra
     * @return ids de usuario distintos, ordenados ascendentemente
     */
    @Query("""
            select distinct t.clienteId from Ticket t
            where t.evento.eventoId = :eventoId
              and t.clienteId is not null
              and t.estadoTicket in :estados
            order by t.clienteId asc
            """)
    List<Long> findClienteIdsDistintosPorEvento(@Param("eventoId") Long eventoId,
                                                @Param("estados") Collection<EstadoTicket> estados);
}
