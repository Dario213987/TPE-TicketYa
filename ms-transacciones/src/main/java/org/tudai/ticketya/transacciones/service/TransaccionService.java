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
import org.tudai.ticketya.core.exception.ForbiddenException;
import org.tudai.ticketya.core.exception.NotFoundException;
import org.tudai.ticketya.core.exception.RemoteServiceException;
import org.tudai.ticketya.transacciones.domain.Transaccion;
import org.tudai.ticketya.transacciones.repository.TransaccionRepository;
import org.tudai.ticketya.transacciones.service.temp_mock_gateway.PaymentGateway;

import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional
public class TransaccionService {

    private final TransaccionRepository repository;
    private final PasarelaResiliente pasarela;
    private final UserContext user;

    /**
     * Crea la transacción y la procesa en la pasarela.
     * <p>
     * Es idempotente por ticket, cliente, organizador y tipo: si ya existe una COMPLETA o FALLIDA,
     * la devuelve sin volver a llamar a la pasarela. Si existe una PENDIENTE (porque la pasarela
     * no respondió en un intento anterior), la vuelve a procesar con la misma clave de
     * idempotencia, así la pasarela no cobra dos veces.
     *
     * @throws RemoteServiceException si la pasarela no responde después de los reintentos; la
     *                                transacción queda PENDIENTE para reintentarla después
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public Transaccion crear(Transaccion pedido) {
        Long organizadorId = user.userId();

        var existente = find(pedido, organizadorId);
        if (existente.isPresent()) {
            Transaccion transaccion = existente.get();
            return transaccion.getEstado() == EstadoTransaccion.PENDIENTE
                    ? procesar(transaccion) : transaccion;
        }

        pedido.setOrganizadorId(organizadorId);
        pedido.setEstado(EstadoTransaccion.PENDIENTE);
        try {
            repository.saveAndFlush(pedido);
        } catch (DataIntegrityViolationException e) {
            return find(pedido, organizadorId).orElseThrow();
        }

        return procesar(pedido);
    }

    private Transaccion procesar(Transaccion transaccion) {
        var orden = new PaymentGateway.Orden(transaccion.getId(), claveIdempotencia(transaccion),
                transaccion.getTicketId(), transaccion.getClienteId(), transaccion.getMonto());

        // Si la pasarela no responde, lanza RemoteServiceException y la transacción queda PENDIENTE.
        PaymentGateway.Resultado resultado = pasarela.procesar(transaccion.getTipo(), orden);

        transaccion.setEstado(resultado.aprobado() ? EstadoTransaccion.COMPLETA : EstadoTransaccion.FALLIDA);
        transaccion.setMotivoFallo(resultado.aprobado() ? null : resultado.motivo());
        return repository.saveAndFlush(transaccion);
    }

    static String claveIdempotencia(Transaccion transaccion) {
        return "tx-" + transaccion.getId();
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