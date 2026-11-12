package org.tudai.ticketya.eventos.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.tudai.ticketya.eventos.client.dto.DisponibilidadEventoResponse;
import org.tudai.ticketya.eventos.client.dto.DisponibilidadResponse;
import org.tudai.ticketya.eventos.client.dto.PublicarEventoRequest;

@FeignClient(name = "ms-tickets")
public interface TicketsClient {

    @PostMapping("/api/tickets/eventos/{eventoId}/disponibilidad")
    DisponibilidadResponse crearDisponibilidad(
            @PathVariable("eventoId") Long eventoId,
            @RequestBody PublicarEventoRequest request);

    @GetMapping("/api/tickets/eventos/{eventoId}/disponibilidad")
    DisponibilidadEventoResponse consultarDisponibilidad(@PathVariable("eventoId") Long eventoId);

    @PutMapping("/api/tickets/internal/eventos/{eventoId}/alta-demanda")
    void actualizarAltaDemanda(@PathVariable("eventoId") Long eventoId,
                               @RequestParam("altaDemanda") boolean altaDemanda);


    @PutMapping("/api/tickets/internal/eventos/{eventoId}/cancelar")
    void cancelarEvento(@PathVariable("eventoId") Long eventoId);

    @PutMapping("/api/tickets/internal/eventos/{eventoId}/suspender")
    void suspenderEvento(@PathVariable("eventoId") Long eventoId);

    @PutMapping("/api/tickets/internal/eventos/{eventoId}/finalizar")
    void finalizarEvento(@PathVariable("eventoId") Long eventoId);

    @PutMapping("/api/tickets/internal/eventos/{eventoId}/reactivar")
    void reactivarEvento(@PathVariable("eventoId") Long eventoId);

}
