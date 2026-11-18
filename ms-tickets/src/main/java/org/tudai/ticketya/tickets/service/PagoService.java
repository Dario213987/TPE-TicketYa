package org.tudai.ticketya.tickets.service;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import feign.RetryableException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.tudai.ticketya.contract.transacciones.api.TransaccionesApiClient;
import org.tudai.ticketya.contract.transacciones.model.EstadoTransaccion;
import org.tudai.ticketya.contract.transacciones.model.GenerarTransaccionRequest;
import org.springframework.data.web.PagedModel;
import org.tudai.ticketya.contract.transacciones.model.TipoTransaccion;
import org.tudai.ticketya.contract.transacciones.model.TransaccionResponse;
import org.tudai.ticketya.core.Operation;
import org.tudai.ticketya.core.OperationLogger;
import org.tudai.ticketya.core.exception.BadRequestException;
import org.tudai.ticketya.core.exception.ConflictException;
import org.tudai.ticketya.core.exception.ForbiddenException;
import org.tudai.ticketya.core.exception.NotFoundException;
import org.tudai.ticketya.core.exception.RemoteServiceException;
import org.tudai.ticketya.tickets.client.ReintentoTransacciones;
import org.tudai.ticketya.tickets.client.TransaccionesHeadersInterceptor;
import org.tudai.ticketya.tickets.domain.EstadoPago;
import org.tudai.ticketya.tickets.dto.CompraResponseDTO;
import org.tudai.ticketya.tickets.dto.PagoRequestDTO;
import org.tudai.ticketya.tickets.exception.PagoRechazadoException;
import org.tudai.ticketya.tickets.service.ReservaPagoService.Cobro;
import org.tudai.ticketya.tickets.service.ReservaPagoService.Pendiente;
import org.tudai.ticketya.tickets.service.ReservaPagoService.Preparacion;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Orquesta el pago de una reserva: prepara (transacción local), cobra en ms-transacciones
 * (sin transacción abierta), emite (transacción local) y compensa si algo falla.
 * Los estados y transiciones están en {@code ms-tickets/docs/estados-compra.md}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PagoService {

    private static final int MAX_IDEMPOTENCY_KEY = 100;
    static final String MOTIVO_FALLA_EMISION = "no se pudieron emitir las entradas, se reintegró el pago";
    static final String MOTIVO_PAGO_NO_CONFIRMADO = "el pago no se confirmó en ms-transacciones";
    private static final int MAX_TRANSACCIONES_POR_TICKET = 100;
    /** Entidad del log de operaciones críticas (TKY-35): la compra se registra por reserva. */
    static final String ENTIDAD_COMPRA = "Reserva";
    static final String REINTEGRO_PENDIENTE = "; el reintegro quedó pendiente";
    private static final ObjectMapper JSON = new ObjectMapper().findAndRegisterModules()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    /** Cómo terminó la reconciliación de una reserva (TKY-87). */
    public enum Reconciliacion { EMITIDA, RECHAZADA, REINTEGRADA, SIGUE_PENDIENTE, SIN_CAMBIOS }

    private final ReservaPagoService reservaPagoService;
    private final TransaccionesApiClient transaccionesClient;
    private final ReintentoTransacciones reintento;

    /**
     * Paga una reserva vigente y emite sus entradas.
     * <ul>
     *   <li>Todos los débitos aprobados: emite los tickets VENDIDO y devuelve la compra APROBADO.</li>
     *   <li>Algún débito FALLIDA (rechazado por el procesador): reintegra los aprobados, libera
     *       la reserva y lanza {@link PagoRechazadoException} (409).</li>
     *   <li>Timeout o 5xx de ms-transacciones: se reintenta con backoff ({@link ReintentoTransacciones}).
     *       Si sigue fallando, o el débito queda PENDIENTE en ms-transacciones, la reserva queda
     *       PENDIENTE_PAGO, no se libera y se devuelve ese estado. Un reintento con la misma clave
     *       consulta el estado real (los débitos son idempotentes en ms-transacciones).</li>
     *   <li>ms-transacciones rechaza el pedido (4xx: headers, monto, organizador): es un error de
     *       integración, no un rechazo del pago. La reserva queda PENDIENTE_PAGO, sin compensar,
     *       y se lanza {@link RemoteServiceException} (502).</li>
     *   <li>Falla la emisión después de cobrar: reintegra todo, libera la reserva y lanza
     *       {@link ConflictException} (409).</li>
     * </ul>
     * Un reintento con la misma {@code Idempotency-Key} de un pago terminado devuelve el
     * resultado original sin volver a cobrar ni emitir.
     *
     * @throws BadRequestException    si falta la clave de idempotencia o es demasiado larga
     * @throws NotFoundException      si la reserva no existe
     * @throws ForbiddenException     si la reserva no pertenece al cliente
     * @throws ConflictException      si la reserva venció, ya se procesó con otra clave, al evento
     *                                le falta organizador o precio, o falló la emisión
     * @throws PagoRechazadoException si el pago fue rechazado
     * @throws RemoteServiceException si ms-transacciones rechazó el pedido (502)
     */
    public CompraResponseDTO pagar(Long reservaId, Long clienteId, String idempotencyKey,
                                   PagoRequestDTO request) {
        Preparacion preparacion;
        try {
            String clave = validarClave(idempotencyKey);
            preparacion = reservaPagoService.preparar(reservaId, clienteId, clave, request.getMedioPago());
        } catch (RuntimeException e) {
            OperationLogger.failed(Operation.PURCHASE, clienteId, ENTIDAD_COMPRA, reservaId, e.getMessage());
            throw e;
        }
        // Un reintento con la misma clave de un pago terminado no es una compra nueva: no se registra.
        if (preparacion.yaTerminada()) {
            return preparacion.compraExistente();
        }

        List<Cobro> aprobados = new ArrayList<>();
        Map<Long, Long> transacciones = new LinkedHashMap<>();
        String motivoRechazo = null;
        for (Cobro cobro : preparacion.cobros()) {
            TransaccionResponse debito;
            try {
                debito = transaccion(preparacion.organizadorId(), clienteId, cobro, true);
            } catch (RetryableException | RemoteServiceException e) {
                log.warn("Pago pendiente: reserva={} ticket={} sin respuesta de ms-transacciones ({}). "
                        + "La reserva queda PENDIENTE_PAGO hasta consultar el estado real",
                        reservaId, cobro.ticketId(), e.getMessage());
                return reservaPagoService.consultar(reservaId);
            } catch (RuntimeException e) {
                log.error("Error de integración con ms-transacciones: reserva={} ticket={} ({}). "
                        + "La reserva queda PENDIENTE_PAGO, no se compensa", reservaId, cobro.ticketId(),
                        e.getMessage());
                RemoteServiceException error = new RemoteServiceException(HttpStatus.BAD_GATEWAY.value(),
                        "ms-transacciones no aceptó el débito de la reserva " + reservaId + ": " + e.getMessage());
                OperationLogger.failed(Operation.PURCHASE, clienteId, ENTIDAD_COMPRA, reservaId, error.getMessage());
                throw error;
            }

            if (debito != null && debito.getEstado() == EstadoTransaccion.PENDIENTE) {
                log.warn("Pago pendiente: reserva={} ticket={} el débito {} sigue PENDIENTE en ms-transacciones",
                        reservaId, cobro.ticketId(), debito.getId());
                return reservaPagoService.consultar(reservaId);
            }
            if (debito != null && debito.getEstado() == EstadoTransaccion.COMPLETA) {
                aprobados.add(cobro);
                transacciones.put(cobro.ticketId(), debito.getId());
            } else {
                motivoRechazo = motivo(debito);
                break;
            }
        }

        if (motivoRechazo != null) {
            EstadoPago estado = compensar(reservaId, preparacion.organizadorId(), clienteId, aprobados,
                    EstadoPago.RECHAZADO, motivoRechazo);
            registrarRechazo(reservaId, clienteId, estado, motivoRechazo);
            throw new PagoRechazadoException(motivoRechazo);
        }

        CompraResponseDTO compra;
        try {
            compra = reservaPagoService.emitir(reservaId, transacciones);
        } catch (RuntimeException e) {
            log.error("Falló la emisión de la reserva {} con el pago aprobado: {}", reservaId, e.getMessage(), e);
            EstadoPago estado = compensar(reservaId, preparacion.organizadorId(), clienteId, aprobados,
                    EstadoPago.REINTEGRADO, MOTIVO_FALLA_EMISION);
            registrarReintegro(reservaId, clienteId, estado);
            throw new ConflictException("No se pudieron emitir las entradas de la reserva " + reservaId
                    + "; se reintegró el pago");
        }
        OperationLogger.ok(Operation.PURCHASE, clienteId, ENTIDAD_COMPRA, reservaId);
        return compra;
    }

    /**
     * Resuelve una reserva que quedó en PENDIENTE_PAGO consultando el estado real de sus débitos
     * en ms-transacciones (job de reconciliación, TKY-87). Hace lo mismo que el pago en línea:
     * <ul>
     *   <li>Todos los débitos COMPLETA: emite las entradas.</li>
     *   <li>Algún débito FALLIDA: reintegra los aprobados y libera la reserva (RECHAZADO).</li>
     *   <li>Algún débito PENDIENTE: lo vuelve a pedir (es idempotente: se reprocesa con la misma
     *       clave). Si sigue PENDIENTE, no cambia nada y queda para la próxima vuelta.</li>
     *   <li>Algún ticket sin débito: ese cobro nunca se registró; reintegra los aprobados y
     *       libera la reserva (RECHAZADO, "pago no confirmado").</li>
     * </ul>
     * Si ms-transacciones no responde, lanza la excepción y la reserva queda como estaba.
     */
    public Reconciliacion reconciliar(Long reservaId) {
        Pendiente pendiente = reservaPagoService.pendiente(reservaId).orElse(null);
        if (pendiente == null || pendiente.organizadorId() == null) {
            return Reconciliacion.SIN_CAMBIOS;
        }

        List<Cobro> aprobados = new ArrayList<>();
        Map<Long, Long> transacciones = new LinkedHashMap<>();
        String motivoRechazo = null;
        boolean sinDebito = false;
        for (Cobro cobro : pendiente.cobros()) {
            TransaccionResponse debito = debitoRegistrado(pendiente, cobro.ticketId());
            if (debito == null) {
                sinDebito = true;
                continue;
            }
            if (debito.getEstado() == EstadoTransaccion.PENDIENTE) {
                debito = transaccion(pendiente.organizadorId(), pendiente.clienteId(), cobro, true);
            }
            if (debito == null || debito.getEstado() == EstadoTransaccion.PENDIENTE) {
                log.warn("Reconciliación: reserva={} ticket={} el débito sigue PENDIENTE en ms-transacciones",
                        reservaId, cobro.ticketId());
                return Reconciliacion.SIGUE_PENDIENTE;
            }
            if (debito.getEstado() == EstadoTransaccion.COMPLETA) {
                aprobados.add(cobro);
                transacciones.put(cobro.ticketId(), debito.getId());
            } else if (motivoRechazo == null) {
                motivoRechazo = motivo(debito);
            }
        }

        if (motivoRechazo != null || sinDebito) {
            String motivo = motivoRechazo != null ? motivoRechazo : MOTIVO_PAGO_NO_CONFIRMADO;
            log.warn("Reconciliación: reserva={} el pago no se completó ({}); se compensa", reservaId, motivo);
            EstadoPago estado = compensar(reservaId, pendiente.organizadorId(), pendiente.clienteId(), aprobados,
                    EstadoPago.RECHAZADO, motivo);
            registrarRechazo(reservaId, pendiente.clienteId(), estado, motivo);
            return Reconciliacion.RECHAZADA;
        }

        try {
            reservaPagoService.emitir(reservaId, transacciones);
            log.warn("Reconciliación: reserva={} con todos los débitos aprobados; entradas emitidas", reservaId);
            OperationLogger.ok(Operation.PURCHASE, pendiente.clienteId(), ENTIDAD_COMPRA, reservaId);
            return Reconciliacion.EMITIDA;
        } catch (RuntimeException e) {
            log.error("Reconciliación: falló la emisión de la reserva {} con el pago aprobado: {}",
                    reservaId, e.getMessage(), e);
            EstadoPago estado = compensar(reservaId, pendiente.organizadorId(), pendiente.clienteId(), aprobados,
                    EstadoPago.REINTEGRADO, MOTIVO_FALLA_EMISION);
            registrarReintegro(reservaId, pendiente.clienteId(), estado);
            return Reconciliacion.REINTEGRADA;
        }
    }

    /** El débito del cliente para el ticket en ms-transacciones, o {@code null} si no hay. */
    private TransaccionResponse debitoRegistrado(Pendiente pendiente, Long ticketId) {
        PagedModel<?> pagina = reintento.ejecutar(() -> TransaccionesHeadersInterceptor.comoOrganizador(
                pendiente.organizadorId(), () -> transaccionesClient.buscarPorTicket(
                        ticketId, TipoTransaccion.DEBITO, 0, MAX_TRANSACCIONES_POR_TICKET, null)));
        if (pagina == null || pagina.getContent() == null) {
            return null;
        }
        // El contrato mapea la página a org.springframework.data.web.PagedModel sin tipo: los
        // elementos llegan como mapas y se convierten a TransaccionResponse.
        return pagina.getContent().stream()
                .map(elemento -> elemento instanceof TransaccionResponse transaccion
                        ? transaccion : JSON.convertValue(elemento, TransaccionResponse.class))
                .filter(transaccion -> pendiente.clienteId().equals(transaccion.getClienteId()))
                .findFirst()
                .orElse(null);
    }

    /**
     * Reintegra (crédito) cada débito aprobado y libera la reserva. Si algún reintegro falla,
     * la reserva queda REINTEGRO_PENDIENTE para resolverlo a mano.
     *
     * @return el estado final de la reserva
     */
    private EstadoPago compensar(Long reservaId, Long organizadorId, Long clienteId, List<Cobro> aprobados,
                           EstadoPago estadoSiReintegra, String motivo) {
        boolean reintegroCompleto = true;
        for (Cobro cobro : aprobados) {
            try {
                TransaccionResponse credito = transaccion(organizadorId, clienteId, cobro, false);
                if (credito == null || credito.getEstado() != EstadoTransaccion.COMPLETA) {
                    reintegroCompleto = false;
                    log.error("Compensación fallida: reserva={} ticket={} monto={} el reintegro quedó {}",
                            reservaId, cobro.ticketId(), cobro.monto(),
                            credito == null ? "sin respuesta" : credito.getEstado());
                } else {
                    log.warn("Compensación: reserva={} ticket={} monto={} reintegrado (transacción {}). Motivo: {}",
                            reservaId, cobro.ticketId(), cobro.monto(), credito.getId(), motivo);
                }
            } catch (RuntimeException e) {
                reintegroCompleto = false;
                log.error("Compensación fallida: reserva={} ticket={} monto={} no se pudo reintegrar: {}",
                        reservaId, cobro.ticketId(), cobro.monto(), e.getMessage());
            }
        }

        EstadoPago estadoFinal = reintegroCompleto ? estadoSiReintegra : EstadoPago.REINTEGRO_PENDIENTE;
        reservaPagoService.liberar(reservaId, estadoFinal, motivo);
        log.warn("Compensación: reserva={} liberada, estado {}. Motivo: {}", reservaId, estadoFinal, motivo);
        return estadoFinal;
    }

    /** Log de operaciones (TKY-35): pago rechazado; si no se pudo reintegrar lo cobrado, se aclara. */
    private void registrarRechazo(Long reservaId, Long clienteId, EstadoPago estado, String motivo) {
        String razon = estado == EstadoPago.REINTEGRO_PENDIENTE ? motivo + REINTEGRO_PENDIENTE : motivo;
        OperationLogger.failed(Operation.PURCHASE, clienteId, ENTIDAD_COMPRA, reservaId, razon);
    }

    /**
     * Log de operaciones (TKY-35): se cobró pero no se pudieron emitir las entradas. Si se reintegró
     * todo queda COMPENSATED; si el reintegro quedó pendiente, FAILED.
     */
    private void registrarReintegro(Long reservaId, Long clienteId, EstadoPago estado) {
        if (estado == EstadoPago.REINTEGRO_PENDIENTE) {
            OperationLogger.failed(Operation.PURCHASE, clienteId, ENTIDAD_COMPRA, reservaId,
                    MOTIVO_FALLA_EMISION + REINTEGRO_PENDIENTE);
        } else {
            OperationLogger.compensated(Operation.PURCHASE, clienteId, ENTIDAD_COMPRA, reservaId,
                    MOTIVO_FALLA_EMISION);
        }
    }

    private TransaccionResponse transaccion(Long organizadorId, Long clienteId, Cobro cobro, boolean debito) {
        GenerarTransaccionRequest request = new GenerarTransaccionRequest()
                .ticketId(cobro.ticketId())
                .clienteId(clienteId)
                .monto(cobro.monto());
        return reintento.ejecutar(() -> TransaccionesHeadersInterceptor.comoOrganizador(organizadorId, () -> debito
                ? transaccionesClient.generarDebito(request)
                : transaccionesClient.generarCredito(request)));
    }

    private String validarClave(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new BadRequestException("El header Idempotency-Key es obligatorio");
        }
        String clave = idempotencyKey.trim();
        if (clave.length() > MAX_IDEMPOTENCY_KEY) {
            throw new BadRequestException(
                    "El header Idempotency-Key no puede tener más de " + MAX_IDEMPOTENCY_KEY + " caracteres");
        }
        return clave;
    }

    private String motivo(TransaccionResponse transaccion) {
        if (transaccion != null && transaccion.getMotivoFallo() != null
                && !transaccion.getMotivoFallo().isBlank()) {
            return transaccion.getMotivoFallo();
        }
        return "el procesador de pagos no aprobó la transacción";
    }
}
