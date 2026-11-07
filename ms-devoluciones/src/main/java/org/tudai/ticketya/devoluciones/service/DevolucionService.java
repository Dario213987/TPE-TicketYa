package org.tudai.ticketya.devoluciones.service;

import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.tudai.ticketya.contract.devoluciones.model.EstadoDevolucion;
import org.tudai.ticketya.contract.devoluciones.model.SolicitudDevolucionRequest;
import org.tudai.ticketya.core.Role;
import org.tudai.ticketya.core.UserContext;
import org.tudai.ticketya.core.exception.ConflictException;
import org.tudai.ticketya.core.exception.ForbiddenException;
import org.tudai.ticketya.core.exception.NotFoundException;
import org.tudai.ticketya.devoluciones.client.EventoClient;
import org.tudai.ticketya.devoluciones.client.EventoDTO;
import org.tudai.ticketya.devoluciones.client.TicketClient;
import org.tudai.ticketya.devoluciones.client.TicketDTO;
import org.tudai.ticketya.devoluciones.domain.Devolucion;
import org.tudai.ticketya.devoluciones.repository.DevolucionRepository;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class DevolucionService {

    private final DevolucionRepository devolucionRepository;
    private final TicketClient ticketClient;
    private final EventoClient eventoClient;
    private final UserContext userContext;

    @Setter
    private Clock clock = Clock.systemDefaultZone();

    public Devolucion solicitarDevolucion(SolicitudDevolucionRequest request) {
        Long clienteId = userContext.isPresent() ? userContext.userId() : request.getClienteId();
        if (request.getClienteId() != null && clienteId != null && !clienteId.equals(request.getClienteId())) {
            throw new ForbiddenException("El cliente indicado no coincide con el usuario autenticado");
        }

        TicketDTO ticket = ticketClient.obtenerTicket(request.getTicketId());
        if (ticket == null) {
            throw new NotFoundException("El ticket " + request.getTicketId() + " no fue encontrado");
        }

        return solicitarDevolucion(request, ticket, clienteId);
    }

    public Devolucion solicitarDevolucion(SolicitudDevolucionRequest request, TicketDTO ticket, Long clienteId) {

        if (clienteId == null) {
            throw new ForbiddenException("El ID del cliente no es valido");
        }

        if (ticket.getUsuarioId() == null) {
            throw new ForbiddenException("El ticket no incluye un cliente asociado");
        }

        if (!ticket.getUsuarioId().equals(clienteId)) {
            throw new ForbiddenException("El ticket no pertenece al cliente solicitante");
        }

        if (Boolean.TRUE.equals(ticket.getUtilizado())) {
            throw new ConflictException("El ticket ya ha sido utilizado");
        }

        if ("DEVUELTO".equalsIgnoreCase(ticket.getEstado())) {
            throw new ConflictException("El ticket ya fue devuelto");
        }

        if (!"VENDIDO".equalsIgnoreCase(ticket.getEstado())) {
            throw new ConflictException("El ticket no se encuentra en estado VENDIDO");
        }

        boolean tieneSolicitudPendiente = devolucionRepository.existsByTicketIdAndEstado(
                ticket.getId(), EstadoDevolucion.PENDIENTE);

        if (tieneSolicitudPendiente) {
            throw new ConflictException("El ticket ya posee una solicitud de devolución pendiente");
        }

        LocalDateTime ahora = LocalDateTime.now(clock);
        LocalDateTime fechaEvento = obtenerFechaEvento(ticket);

        if (fechaEvento == null) {
            throw new ConflictException("No es posible verificar el plazo del evento para la devolución");
        }

        if (fechaEvento.isBefore(ahora.plusHours(72))) {
            throw new ConflictException("No se pueden solicitar devoluciones con menos de 72 horas de anticipación al evento");
        }

        Devolucion devolucion = Devolucion.builder()
                .clienteId(clienteId)
                .ticketId(ticket.getId())
                .fecha(Instant.now(clock))
                .estado(EstadoDevolucion.PENDIENTE)
                .motivo(request.getMotivo())
                .build();

        try {
            return devolucionRepository.saveAndFlush(devolucion);
        } catch (DataIntegrityViolationException e) {
            throw new ConflictException("El ticket ya posee una solicitud de devolución pendiente");
        }
    }

    private LocalDateTime obtenerFechaEvento(TicketDTO ticket) {
        if (ticket.getFechaEvento() != null) {
            return ticket.getFechaEvento();
        }
        if (ticket.getEventoId() != null && eventoClient != null) {
            try {
                EventoDTO evento = eventoClient.obtenerEvento(ticket.getEventoId());
                if (evento != null && evento.getFecha() != null) {
                    return evento.getFecha().atStartOfDay();
                }
            } catch (Exception e) {
                log.warn("No se pudo obtener información del evento con id {}", ticket.getEventoId(), e);
            }
        }
        return null;
    }

    @Transactional(readOnly = true)
    public Page<Devolucion> listarDevoluciones(EstadoDevolucion estado, Long clienteId, Pageable pageable) {
        Role role = userContext.role();
        if (role == Role.CLIENTE) {
            Long authenticatedClientId = userContext.userId();
            if (clienteId != null && !clienteId.equals(authenticatedClientId)) {
                throw new ForbiddenException("Un cliente solo puede ver sus propias solicitudes");
            }
            clienteId = authenticatedClientId;
        } else if (role != Role.ADMINISTRADOR) {
            throw new ForbiddenException("No tenés permisos para realizar esta operación.");
        }

        if (clienteId != null) {
            if (estado != null) {
                return devolucionRepository.findAllByClienteIdAndEstado(clienteId, estado, pageable);
            } else {
                return devolucionRepository.findAllByClienteId(clienteId, pageable);
            }
        } else {
            if (estado != null) {
                return devolucionRepository.findAllByEstado(estado, pageable);
            } else {
                return devolucionRepository.findAll(pageable);
            }
        }
    }

    @Transactional(readOnly = true)
    public Devolucion findById(Long id) {
        Devolucion devolucion = devolucionRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("La devolución " + id + " no existe"));

        if (userContext.role() == Role.CLIENTE && !devolucion.getClienteId().equals(userContext.userId())) {
            throw new NotFoundException("La devolución " + id + " no existe");
        }
        return devolucion;
    }

    @Transactional(readOnly = true)
    public Devolucion findByTicketId(Long ticketId) {
        Devolucion devolucion = devolucionRepository.findFirstByTicketIdOrderByIdDesc(ticketId)
                .orElseThrow(() -> new NotFoundException("No existe devolución para el ticket " + ticketId));

        if (userContext.role() == Role.CLIENTE && !devolucion.getClienteId().equals(userContext.userId())) {
            throw new NotFoundException("No existe devolución para el ticket " + ticketId);
        }
        return devolucion;
    }
}
