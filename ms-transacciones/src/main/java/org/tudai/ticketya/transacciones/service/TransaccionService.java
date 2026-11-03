package org.tudai.ticketya.transacciones.service;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Example;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.tudai.ticketya.contract.transacciones.model.EstadoTransaccion;
import org.tudai.ticketya.contract.transacciones.model.TipoTransaccion;
import org.tudai.ticketya.core.UserContext;
import org.tudai.ticketya.core.exception.ConflictException;
import org.tudai.ticketya.core.exception.ForbiddenException;
import org.tudai.ticketya.core.exception.NotFoundException;
import org.tudai.ticketya.transacciones.domain.Transaccion;
import org.tudai.ticketya.transacciones.repository.TransaccionRepository;
import org.tudai.ticketya.transacciones.service.temp_mock_gateway.PaymentGateway;

import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional
public class TransaccionService {

    private final TransaccionRepository repository;
    private final PaymentGateway gateway;
    private final UserContext user;

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public Transaccion crear(Transaccion pedido) {
        Long organizadorId = user.userId();

        var existente = find(pedido, organizadorId);
        if (existente.isPresent()) {
            return existente.get();
        }

        pedido.setOrganizadorId(organizadorId);
        pedido.setEstado(EstadoTransaccion.PENDIENTE);
        try {
            repository.saveAndFlush(pedido);
        } catch (DataIntegrityViolationException e) {
            return find(pedido, organizadorId).orElseThrow();
        }

        var orden = new PaymentGateway.Orden(pedido.getId(), pedido.getTicketId(),
                pedido.getClienteId(), pedido.getMonto());
        PaymentGateway.Resultado resultado;
        try {
            resultado = pedido.getTipo() == TipoTransaccion.DEBITO
                    ? gateway.debitar(orden) : gateway.acreditar(orden);
        } catch (RuntimeException e) {
            repository.deleteById(pedido.getId());
            throw e;
        }

        pedido.setEstado(resultado.aprobado() ? EstadoTransaccion.COMPLETA : EstadoTransaccion.FALLIDA);
        pedido.setMotivoFallo(resultado.aprobado() ? null : resultado.motivo());
        return repository.saveAndFlush(pedido);
    }

    private Optional<Transaccion> find(Transaccion p, Long organizadorId) {
        return repository.findFirstByTicketIdAndClienteIdAndOrganizadorIdAndTipo(
                p.getTicketId(), p.getClienteId(), organizadorId, p.getTipo());
    }

    @Transactional(readOnly = true)
    public Transaccion findById(Long id) {
        return repository.findById(id)
                .filter(t -> t.getOrganizadorId().equals(user.userId()))
                .orElseThrow(() -> new NotFoundException("La transacción " + id + " no existe"));
    }

    @Transactional(readOnly = true)
    public Page<Transaccion> findByTicketIdAndType(Long ticketId, TipoTransaccion tipo, Pageable pageable) {
        Transaccion filtro = new Transaccion();
        filtro.setTicketId(ticketId);
        filtro.setTipo(tipo);
        filtro.setOrganizadorId(user.userId());
        return repository.findAll(Example.of(filtro), pageable);
    }

    @Transactional(readOnly = true)
    public Page<Transaccion> findAllByOrganizador(Long organizadorId, Pageable pageable) {
        if (!organizadorId.equals(user.userId())) {
            throw new ForbiddenException("Un organizador solo puede ver sus propias transacciones");
        }
        Transaccion filtro = new Transaccion();
        filtro.setOrganizadorId(organizadorId);
        return repository.findAll(Example.of(filtro), pageable);
    }
}