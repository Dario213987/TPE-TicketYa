package org.tudai.ticketya.tickets.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.tudai.ticketya.contract.transacciones.api.TransaccionesApiClient;
import org.tudai.ticketya.contract.transacciones.model.EstadoTransaccion;
import org.tudai.ticketya.contract.transacciones.model.GenerarTransaccionRequest;
import org.tudai.ticketya.contract.transacciones.model.TransaccionResponse;
import org.tudai.ticketya.core.exception.BadRequestException;
import org.tudai.ticketya.core.exception.ConflictException;
import org.tudai.ticketya.core.exception.ForbiddenException;
import org.tudai.ticketya.core.exception.NotFoundException;
import org.tudai.ticketya.tickets.client.TransaccionesHeadersInterceptor;
import org.tudai.ticketya.tickets.domain.*;
import org.tudai.ticketya.tickets.dto.CompraResponseDTO;
import org.tudai.ticketya.tickets.dto.PagoRequestDTO;
import org.tudai.ticketya.tickets.dto.TicketEmitidoDTO;
import org.tudai.ticketya.tickets.exception.PagoRechazadoException;
import org.tudai.ticketya.tickets.repository.DisponibilidadEventoRepository;
import org.tudai.ticketya.tickets.repository.ReservaRepository;
import org.tudai.ticketya.tickets.repository.TicketRepository;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class PagoService {

    private static final int MAX_IDEMPOTENCY_KEY = 100;

    private final ReservaRepository reservaRepository;
    private final DisponibilidadEventoRepository eventoRepository;
    private final TicketRepository ticketRepository;
    private final ReservaVencidaService reservaVencidaService;
    private final TransaccionesApiClient transaccionesClient;

    /**
     * Paga una reserva vigente y emite sus entradas.
     * <p>
     * Genera un débito en ms-transacciones por cada ticket de la reserva, a nombre del
     * organizador del evento. Si todos quedan COMPLETA, los tickets pasan a VENDIDO (su
     * código QR es el id único que se generó al reservar) y se registra el medio de pago.
     * Si alguno queda FALLIDA, la reserva se libera y se lanza {@link PagoRechazadoException}
     * sin deshacer la liberación.
     * <p>
     * Es idempotente: un reintento con la misma {@code Idempotency-Key} devuelve el resultado
     * original sin volver a cobrar ni emitir.
     *
     * @param reservaId      reserva a pagar
     * @param clienteId      cliente autenticado
     * @param idempotencyKey clave de idempotencia enviada por el cliente
     * @param request        medio de pago
     *
     * @return la compra con las entradas emitidas
     *
     * @throws BadRequestException     si falta la clave de idempotencia o es demasiado larga
     * @throws NotFoundException       si la reserva no existe
     * @throws ForbiddenException      si la reserva no pertenece al cliente
     * @throws ConflictException       si la reserva venció, ya fue procesada con otra clave,
     *                                 o al evento le falta organizador o precio
     * @throws PagoRechazadoException  si ms-transacciones rechaza el pago
     */
    @Transactional(noRollbackFor = PagoRechazadoException.class)
    public CompraResponseDTO pagar(Long reservaId, Long clienteId, String idempotencyKey,
                                   PagoRequestDTO request) {
        String clave = validarClave(idempotencyKey);

        Reserva reserva = reservaRepository.findByIdForUpdate(reservaId)
                .orElseThrow(() -> new NotFoundException("No existe la reserva " + reservaId));

        if (!Objects.equals(reserva.getClienteId(), clienteId)) {
            throw new ForbiddenException("La reserva no pertenece al cliente");
        }

        if (reserva.getEstadoPago() != null) {
            return resultadoOriginal(reserva, clave);
        }

        DisponibilidadEvento evento = eventoRepository.findByIdForUpdate(reserva.getEventoId())
                .orElseThrow(() -> new ConflictException(
                        "El evento " + reserva.getEventoId() + " no está publicado"));

        Instant ahora = Instant.now();
        reservaVencidaService.liberarVencidas(evento.getEventoId(), ahora);

        List<Ticket> tickets = ticketRepository.findByReserva_IdOrderByIdAsc(reservaId);
        if (tickets.isEmpty() || tickets.stream().anyMatch(t -> t.getEstadoTicket() != EstadoTicket.RESERVADO)) {
            throw new ConflictException("La reserva " + reservaId + " venció o ya no está vigente");
        }

        if (evento.getOrganizadorId() == null) {
            throw new ConflictException("El evento " + evento.getEventoId() + " no tiene organizador informado");
        }
        for (Ticket ticket : tickets) {
            if (ticket.getSector().getPrecio() == null) {
                throw new ConflictException(
                        "El sector " + ticket.getSector().getSectorIdExterno() + " no tiene precio");
            }
        }

        reserva.setIdempotencyKey(clave);
        reserva.setMedioPago(request.getMedioPago().trim());

        for (Ticket ticket : tickets) {
            TransaccionResponse transaccion = debitar(evento.getOrganizadorId(), clienteId, ticket);
            if (transaccion == null || transaccion.getEstado() != EstadoTransaccion.COMPLETA) {
                // Los débitos ya aprobados de esta reserva los revierte la compensación (TKY-57).
                rechazar(reserva, tickets, motivo(transaccion));
            }
        }

        for (Ticket ticket : tickets) {
            ticket.setEstadoTicket(EstadoTicket.VENDIDO);
            ticket.setPrecio(ticket.getSector().getPrecio());
            ticket.setExpiraEl(null);
            if (ticket.getAsiento() != null) {
                ticket.getAsiento().setEstado(EstadoDisponibilidad.VENDIDO);
            }
        }
        ticketRepository.saveAll(tickets);

        reserva.setEstadoPago(EstadoPago.APROBADO);
        reserva.setPagadoEl(ahora);
        reservaRepository.save(reserva);

        return toResponse(reserva, tickets);
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

    private CompraResponseDTO resultadoOriginal(Reserva reserva, String clave) {
        if (!clave.equals(reserva.getIdempotencyKey())) {
            throw new ConflictException("La reserva " + reserva.getId() + " ya fue procesada");
        }
        if (reserva.getEstadoPago() == EstadoPago.RECHAZADO) {
            throw new PagoRechazadoException(reserva.getMotivoRechazo());
        }
        return toResponse(reserva, ticketRepository.findByReserva_IdOrderByIdAsc(reserva.getId()));
    }

    private TransaccionResponse debitar(Long organizadorId, Long clienteId, Ticket ticket) {
        GenerarTransaccionRequest debito = new GenerarTransaccionRequest()
                .ticketId(ticket.getId())
                .clienteId(clienteId)
                .monto(ticket.getSector().getPrecio());
        return TransaccionesHeadersInterceptor.comoOrganizador(
                organizadorId, () -> transaccionesClient.generarDebito(debito));
    }

    private void rechazar(Reserva reserva, List<Ticket> tickets, String motivo) {
        reservaVencidaService.liberar(tickets, EstadoTicket.CANCELADO);
        reserva.setEstadoPago(EstadoPago.RECHAZADO);
        reserva.setMotivoRechazo(motivo);
        reservaRepository.save(reserva);
        throw new PagoRechazadoException(motivo);
    }

    private String motivo(TransaccionResponse transaccion) {
        if (transaccion != null && transaccion.getMotivoFallo() != null
                && !transaccion.getMotivoFallo().isBlank()) {
            return transaccion.getMotivoFallo();
        }
        return "el procesador de pagos no aprobó la transacción";
    }

    private CompraResponseDTO toResponse(Reserva reserva, List<Ticket> tickets) {
        List<TicketEmitidoDTO> emitidos = tickets.stream()
                .map(ticket -> TicketEmitidoDTO.builder()
                        .id(ticket.getId())
                        .codigoQr(ticket.getCodigo_qr())
                        .estado(ticket.getEstadoTicket())
                        .sectorId(ticket.getSector().getSectorIdExterno())
                        .fila(ticket.getAsiento() == null ? null : ticket.getAsiento().getFila())
                        .numero(ticket.getAsiento() == null ? null : ticket.getAsiento().getNumero())
                        .precio(ticket.getPrecio())
                        .build())
                .toList();

        return CompraResponseDTO.builder()
                .reservaId(reserva.getId())
                .eventoId(reserva.getEventoId())
                .clienteId(reserva.getClienteId())
                .medioPago(reserva.getMedioPago())
                .estadoPago(reserva.getEstadoPago())
                .total(emitidos.stream()
                        .map(TicketEmitidoDTO::getPrecio)
                        .filter(Objects::nonNull)
                        .mapToDouble(Double::doubleValue)
                        .sum())
                .pagadoEl(reserva.getPagadoEl())
                .tickets(emitidos)
                .build();
    }
}
