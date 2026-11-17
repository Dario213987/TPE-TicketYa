package org.tudai.ticketya.tickets.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.tudai.ticketya.core.exception.BadRequestException;
import org.tudai.ticketya.core.exception.NotFoundException;
import org.tudai.ticketya.tickets.domain.EstadoTicket;
import org.tudai.ticketya.tickets.domain.Ticket;
import org.tudai.ticketya.tickets.dto.ResultadoAccesoDTO;
import org.tudai.ticketya.tickets.exception.AccesoRechazadoException;
import org.tudai.ticketya.tickets.messaging.TicketUtilizadoPublisher;
import org.tudai.ticketya.tickets.repository.TicketRepository;

import java.util.Objects;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class AccesoService {

    private static final String RESULTADO_OK = "OK";

    private final TicketRepository ticketRepository;
    private final TicketUtilizadoPublisher ticketUtilizadoPublisher;

    @Transactional
    public ResultadoAccesoDTO validar(Long eventoId, String codigoQr) {
        if (eventoId == null) {
            throw new BadRequestException("El evento es obligatorio");
        }

        String qr = normalizar(codigoQr);

        int actualizados = ticketRepository.marcarComoUsado(
                qr, eventoId, EstadoTicket.VENDIDO, EstadoTicket.USADO);

        if (actualizados == 1) {
            Optional<Ticket> encontrado = ticketRepository.buscarPorCodigoQr(qr);
            encontrado.ifPresent(this::publicarAlConfirmar);
            return ResultadoAccesoDTO.builder()
                    .resultado(RESULTADO_OK)
                    .valido(true)
                    .codigoQr(qr)
                    .eventoId(eventoId)
                    .estadoTicket(EstadoTicket.USADO)
                    .ticketId(encontrado.map(Ticket::getId).orElse(null))
                    .build();
        }

        Ticket ticket = ticketRepository.buscarPorCodigoQr(qr)
                .orElseThrow(() -> new NotFoundException("No existe un ticket con ese codigo QR"));

        if (!Objects.equals(ticket.getEvento().getEventoId(), eventoId)) {
            throw new AccesoRechazadoException(
                    "El ticket no pertenece al evento indicado", ticket.getId());
        }
        if (ticket.getEstadoTicket() == EstadoTicket.USADO) {
            throw new AccesoRechazadoException("El ticket ya fue utilizado", ticket.getId());
        }
        if (ticket.getEstadoTicket() == EstadoTicket.DEVUELTO) {
            throw new AccesoRechazadoException("El ticket fue devuelto", ticket.getId());
        }
        throw new AccesoRechazadoException("El ticket no esta habilitado para ingresar (estado "
                + ticket.getEstadoTicket() + ")", ticket.getId());
    }

    private void publicarAlConfirmar(Ticket ticket) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    ticketUtilizadoPublisher.publicar(ticket);
                }
            });
        } else {
            ticketUtilizadoPublisher.publicar(ticket);
        }
    }

    private String normalizar(String codigoQr) {
        if (codigoQr == null || codigoQr.isBlank()) {
            throw new BadRequestException("El codigo QR es obligatorio");
        }
        return codigoQr.trim();
    }
}
