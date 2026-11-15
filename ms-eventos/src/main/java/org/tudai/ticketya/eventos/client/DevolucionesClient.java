package org.tudai.ticketya.eventos.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;

@FeignClient(name = "ms-devoluciones")
public interface DevolucionesClient {

    @PutMapping("/api/devoluciones/eventos/{eventoId}/reintegro-masivo")
    void reintegrarEvento(@PathVariable("eventoId") Long eventoId);

}
