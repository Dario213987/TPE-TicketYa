package org.tudai.ticketya.tickets.service;

import feign.RetryableException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.tudai.ticketya.contract.transacciones.api.TransaccionesApiClient;
import org.tudai.ticketya.contract.transacciones.model.EstadoTransaccion;
import org.tudai.ticketya.contract.transacciones.model.GenerarTransaccionRequest;
import org.tudai.ticketya.contract.transacciones.model.TransaccionResponse;
import org.tudai.ticketya.core.exception.BadRequestException;
import org.tudai.ticketya.core.exception.ConflictException;
import org.tudai.ticketya.core.exception.ForbiddenException;
import org.tudai.ticketya.core.exception.NotFoundException;
import org.tudai.ticketya.core.exception.RemoteServiceException;
import org.tudai.ticketya.tickets.client.TransaccionesHeadersInterceptor;
import org.tudai.ticketya.tickets.domain.EstadoPago;
import org.tudai.ticketya.tickets.dto.CompraResponseDTO;
import org.tudai.ticketya.tickets.dto.PagoRequestDTO;
import org.tudai.ticketya.tickets.exception.PagoRechazadoException;
import org.tudai.ticketya.tickets.service.ReservaPagoService.Cobro;
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

    private final ReservaPagoService reservaPagoService;
    private final TransaccionesApiClient transaccionesClient;

    /**
     * Paga una reserva vigente y emite sus entradas.
     * <ul>
     *   <li>Todos los débitos aprobados: emite los tickets VENDIDO y devuelve la compra APROBADO.</li>
     *   <li>Algún débito rechazado: reintegra los aprobados, libera la reserva y lanza
     *       {@link PagoRechazadoException} (409).</li>
     *   <li>Timeout o error de ms-transacciones: la reserva queda PENDIENTE_PAGO, no se libera y
     *       se devuelve ese estado. Un reintento con la misma clave consulta el estado real
     *       (los débitos son idempotentes en ms-transacciones).</li>
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
     */
    public CompraResponseDTO pagar(Long reservaId, Long clienteId, String idempotencyKey,
                                   PagoRequestDTO request) {
        String clave = validarClave(idempotencyKey);

        Preparacion preparacion = reservaPagoService.preparar(
                reservaId, clienteId, clave, request.getMedioPago().trim());
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
                motivoRechazo = e.getMessage();
                break;
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
            compensar(reservaId, preparacion, clienteId, aprobados, EstadoPago.RECHAZADO, motivoRechazo);
            throw new PagoRechazadoException(motivoRechazo);
        }

        try {
            return reservaPagoService.emitir(reservaId, transacciones);
        } catch (RuntimeException e) {
            log.error("Falló la emisión de la reserva {} con el pago aprobado: {}", reservaId, e.getMessage(), e);
            compensar(reservaId, preparacion, clienteId, aprobados, EstadoPago.REINTEGRADO, MOTIVO_FALLA_EMISION);
            throw new ConflictException("No se pudieron emitir las entradas de la reserva " + reservaId
                    + "; se reintegró el pago");
        }
    }

    /**
     * Reintegra (crédito) cada débito aprobado y libera la reserva. Si algún reintegro falla,
     * la reserva queda REINTEGRO_PENDIENTE para resolverlo a mano (en la E2, con la Saga).
     */
    private void compensar(Long reservaId, Preparacion preparacion, Long clienteId, List<Cobro> aprobados,
                           EstadoPago estadoSiReintegra, String motivo) {
        boolean reintegroCompleto = true;
        for (Cobro cobro : aprobados) {
            try {
                TransaccionResponse credito = transaccion(preparacion.organizadorId(), clienteId, cobro, false);
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
    }

    private TransaccionResponse transaccion(Long organizadorId, Long clienteId, Cobro cobro, boolean debito) {
        GenerarTransaccionRequest request = new GenerarTransaccionRequest()
                .ticketId(cobro.ticketId())
                .clienteId(clienteId)
                .monto(cobro.monto());
        return TransaccionesHeadersInterceptor.comoOrganizador(organizadorId, () -> debito
                ? transaccionesClient.generarDebito(request)
                : transaccionesClient.generarCredito(request));
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
