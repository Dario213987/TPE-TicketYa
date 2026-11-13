package org.tudai.ticketya.devoluciones.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.tudai.ticketya.contract.devoluciones.model.EstadoDevolucion;
import org.tudai.ticketya.devoluciones.domain.Devolucion;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface DevolucionRepository extends JpaRepository<Devolucion, Long> {
    boolean existsByTicketIdAndEstado(Long ticketId, EstadoDevolucion estado);
    boolean existsByTicketIdAndEstadoIn(Long ticketId, Collection<EstadoDevolucion> estados);
    Optional<Devolucion> findFirstByTicketIdOrderByIdDesc(Long ticketId);
    List<Devolucion> findAllByClienteId(Long clienteId);
    Page<Devolucion> findAllByEstado(EstadoDevolucion estado, Pageable pageable);
    Page<Devolucion> findAllByClienteId(Long clienteId, Pageable pageable);
    Page<Devolucion> findAllByClienteIdAndEstado(Long clienteId, EstadoDevolucion estado, Pageable pageable);
}
