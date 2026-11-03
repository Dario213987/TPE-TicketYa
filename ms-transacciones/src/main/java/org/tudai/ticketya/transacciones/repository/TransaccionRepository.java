package org.tudai.ticketya.transacciones.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.tudai.ticketya.contract.transacciones.model.TipoTransaccion;
import org.tudai.ticketya.transacciones.domain.Transaccion;

import java.util.Optional;

public interface TransaccionRepository extends JpaRepository<Transaccion, Long> {

    Optional<Transaccion> findFirstByTicketIdAndClienteIdAndOrganizadorIdAndTipo(Long ticketId, Long clienteId, Long organizadorId, TipoTransaccion tipo);}