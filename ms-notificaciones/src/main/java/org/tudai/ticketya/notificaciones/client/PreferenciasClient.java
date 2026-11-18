package org.tudai.ticketya.notificaciones.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@FeignClient(name = "ms-preferencias")
public interface PreferenciasClient {

    @GetMapping("/api/preferencias/internal/destinatarios")
    List<Long> obtenerDestinatarios(@RequestParam("ciudad") String ciudad,
                                    @RequestParam("participanteIds") List<Long> participanteIds);
}
