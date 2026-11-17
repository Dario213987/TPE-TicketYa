package org.tudai.ticketya.tickets.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.tudai.ticketya.core.exception.ConflictException;
import org.tudai.ticketya.core.exception.NotFoundException;
import org.tudai.ticketya.tickets.domain.EstadoTicket;
import org.tudai.ticketya.tickets.domain.Ticket;
import org.tudai.ticketya.tickets.dto.MiEntradaDTO;
import org.tudai.ticketya.tickets.dto.QrEntradaDTO;
import org.tudai.ticketya.tickets.repository.TicketRepository;

import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

/**
 * Entradas del cliente: los tickets que ya compró (no las reservas en curso ni las canceladas).
 */
@Service
@RequiredArgsConstructor
public class EntradaService {

    /** Estados en los que el ticket es una entrada del cliente. */
    static final Set<EstadoTicket> ESTADOS_ENTRADA =
            EnumSet.of(EstadoTicket.EMITIDO, EstadoTicket.VENDIDO, EstadoTicket.USADO, EstadoTicket.DEVUELTO);

    /** Estados en los que la entrada sirve para ingresar y por lo tanto tiene QR. */
    static final Set<EstadoTicket> ESTADOS_CON_QR = EnumSet.of(EstadoTicket.EMITIDO, EstadoTicket.VENDIDO);

    private static final Sort ORDEN = Sort.by(Sort.Direction.DESC, "fechaEmision")
            .and(Sort.by(Sort.Direction.DESC, "id"));

    private final TicketRepository ticketRepository;

    /**
     * Lista las entradas del cliente, de la más reciente a la más antigua.
     *
     * @param clienteId cliente autenticado
     * @param pageable  página y tamaño (el orden lo define el servicio)
     */
    @Transactional(readOnly = true)
    public Page<MiEntradaDTO> listar(Long clienteId, Pageable pageable) {
        Pageable pagina = pageable.isUnpaged()
                ? Pageable.unpaged(ORDEN)
                : PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), ORDEN);
        return ticketRepository.findByClienteIdAndEstadoTicketIn(clienteId, ESTADOS_ENTRADA, pagina)
                .map(this::toDTO);
    }

    /**
     * Devuelve el contenido del QR de una entrada del cliente, derivado del código único del ticket.
     *
     * @throws NotFoundException si el ticket no existe o es de otro cliente
     * @throws ConflictException si la entrada no está vigente (usada, devuelta o sin pagar)
     */
    @Transactional(readOnly = true)
    public QrEntradaDTO qr(Long ticketId, Long clienteId) {
        Ticket ticket = ticketRepository.findById(ticketId)
                .filter(t -> Objects.equals(t.getClienteId(), clienteId))
                .orElseThrow(() -> new NotFoundException("No existe la entrada " + ticketId));

        if (!ESTADOS_CON_QR.contains(ticket.getEstadoTicket())) {
            throw new ConflictException(
                    "La entrada " + ticketId + " no está vigente (" + ticket.getEstadoTicket() + ")");
        }

        return QrEntradaDTO.builder()
                .ticketId(ticket.getId())
                .contenido(ticket.getCodigo_qr())
                .build();
    }

    private MiEntradaDTO toDTO(Ticket ticket) {
        return MiEntradaDTO.builder()
                .id(ticket.getId())
                .eventoId(ticket.getEvento().getEventoId())
                .sectorId(ticket.getSector().getSectorIdExterno())
                .fila(ticket.getAsiento() == null ? null : ticket.getAsiento().getFila())
                .numero(ticket.getAsiento() == null ? null : ticket.getAsiento().getNumero())
                .estado(ticket.getEstadoTicket())
                .utilizado(ticket.getEstadoTicket() == EstadoTicket.USADO)
                .precio(ticket.getPrecio())
                .fechaEmision(ticket.getFechaEmision())
                .build();
    }
}
