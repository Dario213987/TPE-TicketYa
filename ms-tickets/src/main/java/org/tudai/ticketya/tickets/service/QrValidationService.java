package org.tudai.ticketya.tickets.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.tudai.ticketya.core.exception.NotFoundException;
import org.tudai.ticketya.tickets.domain.Ticket;
import org.tudai.ticketya.tickets.dto.ValidacionQrResponseDTO;
import org.tudai.ticketya.tickets.repository.TicketRepository;

@Service
@RequiredArgsConstructor
public class QrValidationService {

    private final QrFirmaService qrFirmaService;
    private final TicketRepository ticketRepository;

    @Transactional(readOnly = true)
    public ValidacionQrResponseDTO validar(String codigoQr) {
        qrFirmaService.verificar(codigoQr);

        Ticket ticket = ticketRepository.findByCodigoQr(codigoQr)
                .orElseThrow(() -> new NotFoundException(
                        "No existe una entrada con el QR indicado"));

        return ValidacionQrResponseDTO.builder()
                .valido(true)
                .ticketId(ticket.getId())
                .eventoId(ticket.getEvento().getEventoId())
                .sectorId(ticket.getSector().getSectorIdExterno())
                .clienteId(ticket.getClienteId())
                .estado(ticket.getEstadoTicket())
                .build();
    }
}
