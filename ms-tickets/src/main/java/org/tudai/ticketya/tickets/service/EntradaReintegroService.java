package org.tudai.ticketya.tickets.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.tudai.ticketya.tickets.domain.EstadoTicket;
import org.tudai.ticketya.tickets.dto.EntradaReintegroDTO;
import org.tudai.ticketya.tickets.repository.TicketRepository;

import java.util.List;

/**
 * Consultas y cambios de estado que ms-devoluciones necesita para reintegrar entradas
 * cuando se cancela un evento. Expone solo operaciones idempotentes porque el proceso
 * se ejecuta por lotes y puede reanudarse después de una interrupción.
 */
@Service
@RequiredArgsConstructor
public class EntradaReintegroService {

    private static final List<EstadoTicket> VENDIDAS = List.of(EstadoTicket.EMITIDO, EstadoTicket.VENDIDO);

    private final TicketRepository ticketRepository;

    /**
     * Devuelve una página de entradas vendidas de un evento, ordenadas por id. El cursor
     * {@code desdeId} permite retomar el proceso justo donde se interrumpió.
     */
    @Transactional(readOnly = true)
    public List<EntradaReintegroDTO> listarVendidas(Long eventoId, Long desdeId, int tamanio) {
        return ticketRepository.findEntradasParaReintegro(
                eventoId, desdeId, VENDIDAS, PageRequest.of(0, tamanio)).stream()
                .map(this::toDTO)
                .toList();
    }

    /**
     * Marca una entrada como DEVUELTO. Repetir la llamada no cambia el estado ni genera
     * nuevas operaciones.
     */
    @Transactional
    public void marcarDevuelto(Long ticketId) {
        ticketRepository.findById(ticketId).ifPresent(ticket -> {
            if (ticket.getEstadoTicket() != EstadoTicket.DEVUELTO) {
                ticket.setEstadoTicket(EstadoTicket.DEVUELTO);
                ticketRepository.save(ticket);
            }
        });
    }

    private EntradaReintegroDTO toDTO(Object[] fila) {
        EstadoTicket estado = (EstadoTicket) fila[3];
        return EntradaReintegroDTO.builder()
                .id((Long) fila[0])
                .clienteId((Long) fila[1])
                .precio((Double) fila[2])
                .estado(estado != null ? estado.name() : null)
                .organizadorId((Long) fila[4])
                .transaccionId((Long) fila[5])
                .build();
    }
}
