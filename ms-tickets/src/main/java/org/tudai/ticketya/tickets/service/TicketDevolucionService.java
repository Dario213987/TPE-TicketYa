package org.tudai.ticketya.tickets.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.tudai.ticketya.contract.transacciones.api.TransaccionesApiClient;
import org.tudai.ticketya.contract.transacciones.model.EstadoTransaccion;
import org.tudai.ticketya.contract.transacciones.model.GenerarTransaccionRequest;
import org.tudai.ticketya.contract.transacciones.model.TransaccionResponse;
import org.tudai.ticketya.core.Operation;
import org.tudai.ticketya.core.OperationLogger;
import org.tudai.ticketya.core.exception.ConflictException;
import org.tudai.ticketya.core.exception.NotFoundException;
import org.tudai.ticketya.tickets.client.TransaccionesHeadersInterceptor;
import org.tudai.ticketya.tickets.domain.DisponibilidadSector;
import org.tudai.ticketya.tickets.domain.EstadoDisponibilidad;
import org.tudai.ticketya.tickets.domain.EstadoTicket;
import org.tudai.ticketya.tickets.domain.Ticket;
import org.tudai.ticketya.tickets.domain.VentaItem;
import org.tudai.ticketya.tickets.dto.DevolucionTicketResponseDTO;
import org.tudai.ticketya.tickets.dto.EntradaDetalleDTO;
import org.tudai.ticketya.tickets.repository.TicketRepository;

@Slf4j
@Service
public class TicketDevolucionService {

    private final TicketRepository ticketRepository;
    private final TransaccionesApiClient transaccionesClient;

    public TicketDevolucionService(TicketRepository ticketRepository) {
        this(ticketRepository, null);
    }

    @Autowired
    public TicketDevolucionService(TicketRepository ticketRepository,
                                  @Autowired(required = false) TransaccionesApiClient transaccionesClient) {
        this.ticketRepository = ticketRepository;
        this.transaccionesClient = transaccionesClient;
    }

    @Transactional(readOnly = true)
    public EntradaDetalleDTO obtenerDetalle(Long id) {
        Ticket ticket = ticketRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("No existe el ticket " + id));

        Long transaccionId = null;
        if (ticket.getVenta() != null && ticket.getVenta().getItems() != null) {
            transaccionId = ticket.getVenta().getItems().stream()
                    .filter(item -> item.getTicket() != null && item.getTicket().getId().equals(ticket.getId()))
                    .map(VentaItem::getTransaccionId)
                    .findFirst()
                    .orElse(null);
        }

        return EntradaDetalleDTO.builder()
                .id(ticket.getId())
                .eventoId(ticket.getEvento() != null ? ticket.getEvento().getEventoId() : null)
                .precio(ticket.getPrecio())
                .clienteId(ticket.getClienteId())
                .usuarioId(ticket.getClienteId())
                .transaccionId(transaccionId)
                .idAsiento(ticket.getAsiento() != null ? ticket.getAsiento().getId() : null)
                .idSector(ticket.getSector() != null ? ticket.getSector().getSectorIdExterno() : null)
                .estado(ticket.getEstadoTicket() != null ? ticket.getEstadoTicket().name() : null)
                .utilizado(ticket.getEstadoTicket() == EstadoTicket.USADO)
                .build();
    }

    @Transactional
    public DevolucionTicketResponseDTO devolverTicket(Long id) {
        Ticket ticket = ticketRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("No existe el ticket " + id));

        if (ticket.getEstadoTicket() == EstadoTicket.DEVUELTO) {
            throw new ConflictException("El ticket " + id + " ya fue devuelto");
        }

        if (ticket.getEstadoTicket() == EstadoTicket.USADO) {
            throw new ConflictException("El ticket " + id + " ya fue utilizado");
        }

        if (ticket.getEstadoTicket() != EstadoTicket.EMITIDO && ticket.getEstadoTicket() != EstadoTicket.VENDIDO) {
            throw new ConflictException("El ticket no se encuentra emitido ni vendido (estado actual: " + ticket.getEstadoTicket() + ")");
        }

        // Liberar asiento si era numerado
        if (ticket.getAsiento() != null) {
            ticket.getAsiento().setEstado(EstadoDisponibilidad.LIBRE);
            ticket.setAsiento(null);
        }

        // Liberar cupo del sector
        DisponibilidadSector sector = ticket.getSector();
        if (sector != null && sector.getDisponibles() < sector.getCapacidadTotal()) {
            sector.setDisponibles(sector.getDisponibles() + 1);
        }

        ticket.setEstadoTicket(EstadoTicket.DEVUELTO);
        ticketRepository.saveAndFlush(ticket);

        // Solicitar reintegro a la pasarela (ms-transacciones)
        Long transaccionId = null;
        boolean reintegroPendiente = false;

        Long organizadorId = ticket.getEvento() != null ? ticket.getEvento().getOrganizadorId() : null;
        if (organizadorId != null && ticket.getPrecio() != null && ticket.getPrecio() > 0 && transaccionesClient != null) {
            try {
                GenerarTransaccionRequest request = new GenerarTransaccionRequest()
                        .ticketId(ticket.getId())
                        .clienteId(ticket.getClienteId())
                        .monto(ticket.getPrecio());

                TransaccionResponse transaccion = TransaccionesHeadersInterceptor.comoOrganizador(
                        organizadorId,
                        () -> transaccionesClient.generarCredito(request));

                if (transaccion != null && transaccion.getEstado() == EstadoTransaccion.COMPLETA) {
                    transaccionId = transaccion.getId();
                    reintegroPendiente = false;
                    OperationLogger.ok(Operation.REFUND, ticket.getClienteId(), "Ticket", ticket.getId());
                } else {
                    reintegroPendiente = true;
                    if (transaccion != null) {
                        transaccionId = transaccion.getId();
                    }
                    String motivo = transaccion != null ? transaccion.getMotivoFallo() : "sin respuesta";
                    OperationLogger.failed(Operation.REFUND, ticket.getClienteId(), "Ticket", ticket.getId(), motivo);
                    log.warn("Reintegro no aprobado para ticket {}: estado {}", ticket.getId(),
                            transaccion != null ? transaccion.getEstado() : "null");
                }
            } catch (Exception e) {
                log.error("Fallo al solicitar reintegro a ms-transacciones para ticket {}: {}", ticket.getId(), e.getMessage());
                reintegroPendiente = true;
                OperationLogger.failed(Operation.REFUND, ticket.getClienteId(), "Ticket", ticket.getId(), e.getMessage());
            }
        } else if (ticket.getPrecio() != null && ticket.getPrecio() > 0 && organizadorId == null) {
            log.warn("El evento del ticket {} no tiene organizador asignado, el reintegro queda pendiente", ticket.getId());
            reintegroPendiente = true;
            OperationLogger.failed(Operation.REFUND, ticket.getClienteId(), "Ticket", ticket.getId(), "sin organizador");
        }

        return DevolucionTicketResponseDTO.builder()
                .ticketId(ticket.getId())
                .estado(EstadoTicket.DEVUELTO.name())
                .transaccionId(transaccionId)
                .reintegroPendiente(reintegroPendiente)
                .build();
    }
}
