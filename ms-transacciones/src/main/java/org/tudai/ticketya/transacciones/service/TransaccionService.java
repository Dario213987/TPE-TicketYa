package org.tudai.ticketya.transacciones.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Example;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
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

@Service
@RequiredArgsConstructor
@Transactional
public class TransaccionService {

    private final TransaccionRepository repository;
    private final PaymentGateway gateway;
    private final UserContext user;

    public Transaccion crear(Transaccion pedido) {

        var existente = repository.findFirstByTicketIdAndClienteIdAndOrganizadorIdOrderById(
                pedido.getTicketId(), pedido.getClienteId(), user.userId());
        if (existente.isPresent()) {
            if (existente.get().getTipo() != pedido.getTipo()) {
                throw new ConflictException("El ticket " + pedido.getTicketId()
                        + " ya tiene una transacción de tipo " + existente.get().getTipo());
            }
            return existente.get();
        }

        pedido.setOrganizadorId(user.userId());
        pedido.setEstado(EstadoTransaccion.PENDIENTE);
        repository.saveAndFlush(pedido);

        var orden = new PaymentGateway.Orden(pedido.getId(), pedido.getTicketId(), pedido.getClienteId(),
                pedido.getMonto());
        var resultado = pedido.getTipo() == TipoTransaccion.DEBITO ? gateway.debitar(orden) : gateway.acreditar(orden);
        pedido.setEstado(resultado.aprobado() ? EstadoTransaccion.COMPLETA : EstadoTransaccion.FALLIDA);
        pedido.setMotivoFallo(resultado.aprobado() ? null : resultado.motivo());
        return repository.saveAndFlush(pedido);
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