package org.tudai.ticketya.tickets.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.tudai.ticketya.core.exception.ConflictException;
import org.tudai.ticketya.core.exception.ForbiddenException;
import org.tudai.ticketya.core.exception.NotFoundException;
import org.tudai.ticketya.tickets.domain.*;
import org.tudai.ticketya.tickets.dto.CompraResponseDTO;
import org.tudai.ticketya.tickets.dto.TicketEmitidoDTO;
import org.tudai.ticketya.tickets.exception.PagoRechazadoException;
import org.tudai.ticketya.tickets.repository.DisponibilidadEventoRepository;
import org.tudai.ticketya.tickets.repository.ReservaRepository;
import org.tudai.ticketya.tickets.repository.TicketRepository;
import org.tudai.ticketya.tickets.repository.VentaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Pasos transaccionales del pago de una reserva. Cada método corre en su propia transacción
 * para que {@link PagoService} pueda llamar a ms-transacciones sin tener locks tomados y
 * compensar si un paso posterior falla. Los estados están descriptos en
 * {@code ms-tickets/docs/estados-compra.md}.
 */
@Service
@RequiredArgsConstructor
public class ReservaPagoService {

    private final ReservaRepository reservaRepository;
    private final DisponibilidadEventoRepository eventoRepository;
    private final TicketRepository ticketRepository;
    private final ReservaVencidaService reservaVencidaService;
    private final VentaRepository ventaRepository;

    /**
     * Débito a generar por un ticket de la reserva.
     */
    public record Cobro(Long ticketId, Double monto) {}

    /**
     * Resultado de preparar el pago: o la compra ya terminada (reintento de un pago aprobado),
     * o los cobros a realizar a nombre del organizador.
     */
    public record Preparacion(CompraResponseDTO compraExistente, Long organizadorId, List<Cobro> cobros) {

        static Preparacion terminada(CompraResponseDTO compra) {
            return new Preparacion(compra, null, List.of());
        }

        public boolean yaTerminada() {
            return compraExistente != null;
        }
    }

    /**
     * Valida la reserva y la deja en PENDIENTE_PAGO junto con sus tickets, que a partir de ahí
     * no se liberan por vencimiento. Si la reserva ya estaba en PENDIENTE_PAGO con la misma
     * clave (reintento después de un timeout), devuelve los mismos cobros para consultar el
     * estado real en ms-transacciones.
     *
     * @throws NotFoundException      si la reserva no existe
     * @throws ForbiddenException     si la reserva no pertenece al cliente
     * @throws ConflictException      si la reserva venció, ya se procesó con otra clave,
     *                                o al evento le falta organizador o precio
     * @throws PagoRechazadoException si es un reintento de un pago rechazado o reintegrado
     */
    @Transactional
    public Preparacion preparar(Long reservaId, Long clienteId, String clave, String medioPago) {
        Reserva reserva = reservaRepository.findByIdForUpdate(reservaId)
                .orElseThrow(() -> new NotFoundException("No existe la reserva " + reservaId));

        if (!Objects.equals(reserva.getClienteId(), clienteId)) {
            throw new ForbiddenException("La reserva no pertenece al cliente");
        }

        if (reserva.getEstadoPago() != null) {
            return reintento(reserva, clave);
        }

        DisponibilidadEvento evento = eventoRepository.findByIdForUpdate(reserva.getEventoId())
                .orElseThrow(() -> new ConflictException(
                        "El evento " + reserva.getEventoId() + " no está publicado"));

        reservaVencidaService.liberarVencidas(evento.getEventoId(), Instant.now());

        List<Ticket> tickets = ticketsDe(reservaId);
        if (tickets.isEmpty() || tickets.stream().anyMatch(t -> t.getEstadoTicket() != EstadoTicket.RESERVADO)) {
            throw new ConflictException("La reserva " + reservaId + " venció o ya no está vigente");
        }
        if (evento.getOrganizadorId() == null) {
            throw new ConflictException("El evento " + evento.getEventoId() + " no tiene organizador informado");
        }
        for (Ticket ticket : tickets) {
            Double precio = ticket.getSector().getPrecio();
            if (precio == null || precio <= 0) {
                throw new ConflictException(
                        "El sector " + ticket.getSector().getSectorIdExterno() + " no tiene un precio válido");
            }
        }

        tickets.forEach(ticket -> {
            ticket.setEstadoTicket(EstadoTicket.PENDIENTE_PAGO);
            ticket.setPrecio(ticket.getSector().getPrecio());
        });
        ticketRepository.saveAll(tickets);

        reserva.setIdempotencyKey(clave);
        reserva.setMedioPago(medioPago);
        reserva.setEstadoPago(EstadoPago.PENDIENTE_PAGO);
        reservaRepository.save(reserva);

        return new Preparacion(null, evento.getOrganizadorId(), cobros(tickets));
    }

    /**
     * Emite las entradas de una reserva cuyo pago se aprobó: tickets y asientos pasan a VENDIDO
     * y se registra la venta con sus ítems (sector, asiento, precio cobrado y transacción).
     * Si otro intento ya la emitió, devuelve esa compra.
     *
     * @param transacciones id de la transacción de débito de cada ticket (ticketId → transaccionId)
     *
     * @throws ConflictException si la reserva no está en PENDIENTE_PAGO ni APROBADO
     */
    @Transactional
    public CompraResponseDTO emitir(Long reservaId, Map<Long, Long> transacciones) {
        Reserva reserva = reservaBloqueada(reservaId);
        if (reserva.getEstadoPago() == EstadoPago.APROBADO) {
            return toResponse(reserva, ticketsDe(reservaId));
        }
        if (reserva.getEstadoPago() != EstadoPago.PENDIENTE_PAGO) {
            throw new ConflictException("La reserva " + reservaId + " no tiene un pago pendiente");
        }

        List<Ticket> tickets = ticketsDe(reservaId);
        for (Ticket ticket : tickets) {
            ticket.setEstadoTicket(EstadoTicket.VENDIDO);
            ticket.setExpiraEl(null);
            if (ticket.getAsiento() != null) {
                ticket.getAsiento().setEstado(EstadoDisponibilidad.VENDIDO);
            }
        }
        Instant ahora = Instant.now();
        Venta venta = registrarVenta(reserva, tickets, transacciones, ahora);
        tickets.forEach(ticket -> ticket.setVenta(venta));
        ticketRepository.saveAll(tickets);

        reserva.setEstadoPago(EstadoPago.APROBADO);
        reserva.setPagadoEl(ahora);
        reservaRepository.save(reserva);

        return toResponse(reserva, tickets);
    }

    private Venta registrarVenta(Reserva reserva, List<Ticket> tickets, Map<Long, Long> transacciones,
                                 Instant ahora) {
        Venta venta = Venta.builder()
                .reservaId(reserva.getId())
                .clienteId(reserva.getClienteId())
                .eventoId(reserva.getEventoId())
                .medioPago(reserva.getMedioPago())
                .total(tickets.stream().mapToDouble(Ticket::getPrecio).sum())
                .creadoEl(ahora)
                .build();

        for (Ticket ticket : tickets) {
            venta.agregarItem(VentaItem.builder()
                    .ticket(ticket)
                    .sectorIdExterno(ticket.getSector().getSectorIdExterno())
                    .fila(ticket.getAsiento() == null ? null : ticket.getAsiento().getFila())
                    .numero(ticket.getAsiento() == null ? null : ticket.getAsiento().getNumero())
                    .precio(ticket.getPrecio())
                    .transaccionId(transacciones.get(ticket.getId()))
                    .build());
        }
        return ventaRepository.save(venta);
    }

    /**
     * Libera los lugares de una reserva con el pago pendiente (tickets CANCELADO) y la deja en
     * el estado final indicado. Es la parte local de la compensación.
     *
     * @param estadoFinal RECHAZADO, REINTEGRADO o REINTEGRO_PENDIENTE
     * @param motivo      motivo que se devuelve en los reintentos
     */
    @Transactional
    public void liberar(Long reservaId, EstadoPago estadoFinal, String motivo) {
        Reserva reserva = reservaBloqueada(reservaId);
        if (reserva.getEstadoPago() != EstadoPago.PENDIENTE_PAGO) {
            return;
        }

        List<Ticket> pendientes = ticketsDe(reservaId).stream()
                .filter(ticket -> ticket.getEstadoTicket() == EstadoTicket.PENDIENTE_PAGO)
                .toList();
        if (!pendientes.isEmpty()) {
            reservaVencidaService.liberar(pendientes, EstadoTicket.CANCELADO);
        }

        reserva.setEstadoPago(estadoFinal);
        reserva.setMotivoRechazo(motivo);
        reservaRepository.save(reserva);
    }

    /**
     * Estado actual de la compra, para responder un pago que quedó pendiente.
     */
    @Transactional(readOnly = true)
    public CompraResponseDTO consultar(Long reservaId) {
        Reserva reserva = reservaRepository.findById(reservaId)
                .orElseThrow(() -> new NotFoundException("No existe la reserva " + reservaId));
        return toResponse(reserva, ticketsDe(reservaId));
    }

    private Preparacion reintento(Reserva reserva, String clave) {
        if (!clave.equals(reserva.getIdempotencyKey())) {
            throw new ConflictException("La reserva " + reserva.getId() + " ya fue procesada");
        }
        return switch (reserva.getEstadoPago()) {
            case APROBADO -> Preparacion.terminada(toResponse(reserva, ticketsDe(reserva.getId())));
            case PENDIENTE_PAGO -> {
                DisponibilidadEvento evento = eventoRepository.findById(reserva.getEventoId())
                        .orElseThrow(() -> new ConflictException(
                                "El evento " + reserva.getEventoId() + " no está publicado"));
                yield new Preparacion(null, evento.getOrganizadorId(), cobros(ticketsDe(reserva.getId())));
            }
            case RECHAZADO, REINTEGRADO, REINTEGRO_PENDIENTE -> throw new PagoRechazadoException(reserva.getMotivoRechazo());
        };
    }

    private Reserva reservaBloqueada(Long reservaId) {
        return reservaRepository.findByIdForUpdate(reservaId)
                .orElseThrow(() -> new NotFoundException("No existe la reserva " + reservaId));
    }

    private List<Ticket> ticketsDe(Long reservaId) {
        return ticketRepository.findByReserva_IdOrderByIdAsc(reservaId);
    }

    private List<Cobro> cobros(List<Ticket> tickets) {
        return tickets.stream()
                .map(ticket -> new Cobro(ticket.getId(), ticket.getPrecio()))
                .toList();
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
                .ventaId(tickets.stream()
                        .map(Ticket::getVenta)
                        .filter(Objects::nonNull)
                        .map(Venta::getId)
                        .filter(Objects::nonNull)
                        .findFirst()
                        .orElse(null))
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
