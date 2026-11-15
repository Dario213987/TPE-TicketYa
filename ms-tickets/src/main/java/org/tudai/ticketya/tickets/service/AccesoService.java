package org.tudai.ticketya.tickets.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.tudai.ticketya.core.exception.BadRequestException;
import org.tudai.ticketya.core.exception.ConflictException;
import org.tudai.ticketya.tickets.domain.EstadoTicket;
import org.tudai.ticketya.tickets.domain.Ticket;
import org.tudai.ticketya.tickets.dto.ResultadoAccesoDTO;
import org.tudai.ticketya.tickets.exception.AccesoRechazadoException;
import org.tudai.ticketya.tickets.exception.CodigoRechazoAcceso;
import org.tudai.ticketya.tickets.repository.TicketRepository;

import java.util.Objects;

@Service
@RequiredArgsConstructor
public class AccesoService {

    private static final String RESULTADO_OK = "OK";

    private final TicketRepository ticketRepository;

    @Transactional
    public ResultadoAccesoDTO validar(Long eventoId, String codigoQr) {
        if (eventoId == null) {
            throw new BadRequestException("El evento es obligatorio");
        }

        String qr = normalizar(codigoQr);

        int actualizados = ticketRepository.marcarComoUsado(
                qr, eventoId, EstadoTicket.VENDIDO, EstadoTicket.USADO);

        if (actualizados == 1) {
            return ResultadoAccesoDTO.builder()
                    .resultado(RESULTADO_OK)
                    .valido(true)
                    .codigoQr(qr)
                    .eventoId(eventoId)
                    .estadoTicket(EstadoTicket.USADO)
                    .build();
        }

        Ticket ticket = ticketRepository.buscarPorCodigoQr(qr)
                .orElseThrow(() -> new AccesoRechazadoException(
                        CodigoRechazoAcceso.NO_EXISTE,
                        "No existe un ticket con ese codigo QR"));

        if (!Objects.equals(ticket.getEvento().getEventoId(), eventoId)) {
            throw new AccesoRechazadoException(
                    CodigoRechazoAcceso.OTRO_EVENTO,
                    "El ticket no pertenece al evento indicado");
        }
        if (ticket.getEstadoTicket() == EstadoTicket.USADO) {
            throw new AccesoRechazadoException(
                    CodigoRechazoAcceso.YA_USADA,
                    "El ticket ya fue utilizado");
        }
        if (ticket.getEstadoTicket() == EstadoTicket.DEVUELTO) {
            throw new AccesoRechazadoException(
                    CodigoRechazoAcceso.DEVUELTA,
                    "El ticket fue devuelto");
        }
        throw new ConflictException("El ticket no esta habilitado para ingresar (estado "
                + ticket.getEstadoTicket() + ")");
    }

    private String normalizar(String codigoQr) {
        if (codigoQr == null || codigoQr.isBlank()) {
            throw new BadRequestException("El codigo QR es obligatorio");
        }
        return codigoQr.trim();
    }
}
