package org.tudai.ticketya.tickets.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.tudai.ticketya.tickets.domain.EstadoTicket;
import org.tudai.ticketya.tickets.domain.Ticket;

import java.util.Collection;
import java.util.List;

public interface ReporteRepository extends JpaRepository<Ticket, Long> {

    @Query("""
            select t.sector.sectorIdExterno,
                   sum(case when t.estadoTicket in :vendidos then 1 else 0 end),
                   sum(case when t.estadoTicket = :devuelto then 1 else 0 end)
            from Ticket t
            where t.evento.eventoId = :eventoId
              and t.estadoTicket in :estados
            group by t.sector.sectorIdExterno
            """)
    List<Object[]> resumirPorSector(@Param("eventoId") Long eventoId,
                                    @Param("vendidos") Collection<EstadoTicket> vendidos,
                                    @Param("devuelto") EstadoTicket devuelto,
                                    @Param("estados") Collection<EstadoTicket> estados);
}
