package org.tudai.ticketya.devoluciones.service;

import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.tudai.ticketya.contract.devoluciones.model.EstadoDevolucion;
import org.tudai.ticketya.contract.devoluciones.model.RechazarDevolucionRequest;
import org.tudai.ticketya.contract.devoluciones.model.SolicitudDevolucionRequest;
import org.tudai.ticketya.core.Role;
import org.tudai.ticketya.core.UserContext;
import org.tudai.ticketya.core.exception.BadRequestException;
import org.tudai.ticketya.core.exception.ConflictException;
import org.tudai.ticketya.core.exception.ForbiddenException;
import org.tudai.ticketya.core.exception.NotFoundException;
import org.tudai.ticketya.core.exception.RemoteServiceException;
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
public class DevolucionService {

    private final DevolucionRepository devolucionRepository;
    private final TicketClient ticketClient;
    private final EventoClient eventoClient;
    private final UserContext userContext;

    @Setter
    private Clock clock = Clock.systemDefaultZone();

    public Devolucion solicitarDevolucion(SolicitudDevolucionRequest request) {
        if (!userContext.isPresent() || userContext.userId() == null) {
            throw new ForbiddenException("Usuario no autenticado");
        }

        Long clienteAutenticadoId = userContext.userId();
        if (request.getClienteId() != null && !request.getClienteId().equals(clienteAutenticadoId)) {
            throw new ForbiddenException("El cliente indicado no coincide con el usuario autenticado");
        }

        if (request.getMotivo() != null && request.getMotivo().length() > 255) {
            throw new BadRequestException("El motivo no puede superar los 255 caracteres");
        }

        TicketDTO ticket;
        try {
            ticket = ticketClient.obtenerTicket(request.getTicketId());
        } catch (feign.FeignException.NotFound | NotFoundException e) {
            ticket = null;
        }

        if (ticket == null) {
            throw new NotFoundException("El ticket " + request.getTicketId() + " no fue encontrado");
        }

        return solicitarDevolucion(request, ticket, clienteAutenticadoId);
    }

    public Devolucion solicitarDevolucion(SolicitudDevolucionRequest request, TicketDTO ticket, Long clienteId) {
        if (clienteId == null) {
            throw new ForbiddenException("El ID del cliente no es valido");
        }

        if (request.getMotivo() != null && request.getMotivo().length() > 255) {
            throw new BadRequestException("El motivo no puede superar los 255 caracteres");
        }

        if (ticket.getUsuarioId() == null) {
            throw new ForbiddenException("El ticket no incluye un cliente asociado");
        }

        if (!ticket.getUsuarioId().equals(clienteId)) {
            throw new ForbiddenException("El ticket no pertenece al cliente solicitante");
        }

        if (Boolean.TRUE.equals(ticket.getUtilizado()) || "USADO".equalsIgnoreCase(ticket.getEstado())) {
            throw new ConflictException("El ticket ya ha sido utilizado");
        }

        if ("DEVUELTO".equalsIgnoreCase(ticket.getEstado())) {
            throw new ConflictException("El ticket ya fue devuelto");
        }

        if (!"EMITIDO".equalsIgnoreCase(ticket.getEstado()) && !"VENDIDO".equalsIgnoreCase(ticket.getEstado())) {
            if ("RESERVADO".equalsIgnoreCase(ticket.getEstado())) {
                throw new ConflictException("El ticket se encuentra reservado y no ha sido emitido");
            }
            if ("CANCELADO".equalsIgnoreCase(ticket.getEstado())) {
                throw new ConflictException("El ticket se encuentra cancelado");
            }
            throw new ConflictException("El ticket no se encuentra emitido (estado actual: " + ticket.getEstado() + ")");
        }

        boolean tieneSolicitudActiva = devolucionRepository.existsByTicketIdAndEstadoIn(
                ticket.getId(), java.util.List.of(EstadoDevolucion.PENDIENTE, EstadoDevolucion.APROBADA));

        if (tieneSolicitudActiva) {
            throw new ConflictException("El ticket ya posee una solicitud de devolución activa");
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
                .eventoId(ticket.getEventoId())
                .fecha(Instant.now(clock))
                .estado(EstadoDevolucion.PENDIENTE)
                .motivo(request.getMotivo())
                .transaccionId(ticket.getTransaccionId())
                .build();

        try {
            return persistirDevolucion(devolucion);
        } catch (DataIntegrityViolationException e) {
            String message = e.getMessage() != null ? e.getMessage().toLowerCase() : "";
            if (message.contains("uk_devoluciones_ticket_activa") || message.contains("uq_devoluciones") || message.contains("ticket_pendiente") || message.contains("unique") || message.contains("duplicate")) {
                throw new ConflictException("El ticket ya posee una solicitud de devolución pendiente");
            }
            throw e;
        }
    }

    @Transactional
    public Devolucion persistirDevolucion(Devolucion devolucion) {
        return devolucionRepository.saveAndFlush(devolucion);
    }

    private LocalDateTime obtenerFechaEvento(TicketDTO ticket) {
        if (ticket.getFechaEvento() != null) {
            return ticket.getFechaEvento();
        }
        if (ticket.getEventoId() != null && eventoClient != null) {
            try {
                EventoDTO evento = eventoClient.obtenerEvento(ticket.getEventoId());
                if (evento != null && evento.getFecha() != null) {
                    return evento.getFecha();
                }
            } catch (feign.FeignException.NotFound | NotFoundException e) {
                log.warn("Evento {} no encontrado", ticket.getEventoId());
                return null;
            } catch (RemoteServiceException e) {
                if (e.getRemoteStatus() == 404) {
                    log.warn("Evento {} no encontrado en servicio remoto", ticket.getEventoId());
                    return null;
                }
                throw e;
            }
        }
        return null;
    }

    @Transactional(readOnly = true)
    public Page<Devolucion> listarDevoluciones(EstadoDevolucion estado, Long clienteId, Long eventoId, Pageable pageable) {
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

        final Long finalClienteId = clienteId;
        Specification<Devolucion> spec = (root, query, cb) -> {
            java.util.List<Predicate> predicates = new java.util.ArrayList<>();
            if (finalClienteId != null) {
                predicates.add(cb.equal(root.get("clienteId"), finalClienteId));
            }
            if (estado != null) {
                predicates.add(cb.equal(root.get("estado"), estado));
            }
            if (eventoId != null) {
                predicates.add(cb.equal(root.get("eventoId"), eventoId));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };

        return devolucionRepository.findAll(spec, pageable);
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

<<<<<<< HEAD
    @Transactional
=======
>>>>>>> d325a89be31bcf8324ba8d614145ed5d17e96a66
    public Devolucion rechazarDevolucion(Long id, RechazarDevolucionRequest request) {
        if (request == null || request.getMotivo() == null || request.getMotivo().trim().isEmpty()) {
            throw new BadRequestException("El motivo de rechazo es obligatorio");
        }
        return rechazarDevolucion(id, request.getMotivo().trim());
    }

<<<<<<< HEAD
    @Transactional
=======
>>>>>>> d325a89be31bcf8324ba8d614145ed5d17e96a66
    public Devolucion rechazarDevolucion(Long id, String motivo) {
        if (motivo == null || motivo.trim().isEmpty()) {
            throw new BadRequestException("El motivo de rechazo es obligatorio");
        }

<<<<<<< HEAD
        if (motivo.length() > 255) {
            throw new BadRequestException("El motivo no puede superar los 255 caracteres");
        }

        if (!userContext.isPresent() || userContext.role() != Role.ADMINISTRADOR) {
            throw new ForbiddenException("No tenés permisos para realizar esta operación.");
        }

        Devolucion devolucion = devolucionRepository.findByIdForUpdate(id)
=======
        if (userContext.isPresent() && userContext.role() != Role.ADMINISTRADOR) {
            throw new ForbiddenException("No tenés permisos para realizar esta operación.");
        }

        Devolucion devolucion = devolucionRepository.findById(id)
>>>>>>> d325a89be31bcf8324ba8d614145ed5d17e96a66
                .orElseThrow(() -> new NotFoundException("La devolución " + id + " no existe"));

        if (devolucion.getEstado() != EstadoDevolucion.PENDIENTE) {
            throw new ConflictException("La solicitud de devolución ya fue resuelta (estado actual: " + devolucion.getEstado() + ")");
        }

        devolucion.setEstado(EstadoDevolucion.DENEGADA);
<<<<<<< HEAD
        devolucion.setMotivoRechazo(motivo.trim());
        devolucion.setResueltaPor(userContext.userId());
        devolucion.setFechaResolucion(Instant.now(clock));

        return devolucionRepository.saveAndFlush(devolucion);
=======
        devolucion.setMotivo(motivo.trim());

        try {
            return devolucionRepository.saveAndFlush(devolucion);
        } catch (DataIntegrityViolationException e) {
            throw new ConflictException("Ya existe una devolución en estado DENEGADA para este ticket");
        }
>>>>>>> d325a89be31bcf8324ba8d614145ed5d17e96a66
    }
}
