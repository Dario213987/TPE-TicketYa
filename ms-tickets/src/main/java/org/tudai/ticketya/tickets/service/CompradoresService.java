package org.tudai.ticketya.tickets.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.tudai.ticketya.tickets.domain.EstadoTicket;
import org.tudai.ticketya.tickets.repository.TicketRepository;

import java.util.List;

/**
 * Consultas internas sobre los compradores de un evento.
 * <p>
 * Un comprador es un usuario con al menos un ticket emitido, vendido, usado o devuelto. Las
 * reservas y los tickets cancelados, vencidos o liberados no representan una compra
 * confirmada y no se incluyen.
 */
@Service
@RequiredArgsConstructor
public class CompradoresService {

    private static final List<EstadoTicket> ESTADOS_COMPRADOR = List.of(
            EstadoTicket.EMITIDO,
            EstadoTicket.VENDIDO,
            EstadoTicket.USADO,
            EstadoTicket.DEVUELTO);

    private final TicketRepository ticketRepository;

    /**
     * Lista los ids de usuario distintos que compraron al menos una entrada del evento.
     * <p>
     * No valida que el evento exista: si no tiene tickets en los estados considerados,
     * devuelve una lista vacia para que el consumidor pueda tratarlo como "sin
     * compradores" sin distinguir entre evento inexistente y evento sin ventas.
     *
     * @param eventoId id del evento
     * @return ids de usuario distintos, ordenados de menor a mayor
     */
    @Transactional(readOnly = true)
    public List<Long> listarCompradores(Long eventoId) {
        return ticketRepository.findClienteIdsDistintosPorEvento(eventoId, ESTADOS_COMPRADOR);
    }
}
