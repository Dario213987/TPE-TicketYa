package org.tudai.ticketya.notificaciones.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.List;

/**
 * Cliente Feign para consultar datos internos de {@code ms-tickets}.
 */
@FeignClient(name = "ms-tickets")
public interface TicketsClient {

    /**
     * Obtiene los compradores vigentes de un evento para expandir un aviso masivo.
     *
     * @param eventoId id del evento
     * @return ids de clientes con al menos una entrada vigente
     */
    @GetMapping("/api/tickets/internal/eventos/{eventoId}/compradores")
    List<Long> obtenerCompradores(@PathVariable("eventoId") Long eventoId);
}
