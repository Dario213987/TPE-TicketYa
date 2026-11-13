package org.tudai.ticketya.tickets.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.tudai.ticketya.tickets.client.EventoClient;
import org.tudai.ticketya.tickets.client.dto.EventoResponseDto;
import org.tudai.ticketya.tickets.dto.ReporteEntradasVendidasDTO;
import org.tudai.ticketya.tickets.repository.TicketRepository;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ReporteService {

    private TicketRepository ticketRepository;
    private EventoClient eventoClient;

    public ReporteEntradasVendidasDTO obtenerReporteEntradasVendidas(
            long eventoId, Long solicitanteId, boolean esAdmin) {

        EventoResponseDto evento = eventoClient.obtenerEvento(eventoId);

        //Si no es administrador y no es el mismo que el organizador
        if(!esAdmin && !evento.getOrganizadorId().equals(solicitanteId)){
            //lanzo exepcion y 400
        }




    }
}
