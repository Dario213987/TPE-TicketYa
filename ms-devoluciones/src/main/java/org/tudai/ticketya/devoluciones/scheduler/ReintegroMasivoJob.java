package org.tudai.ticketya.devoluciones.scheduler;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.tudai.ticketya.devoluciones.service.ReintegroMasivoService;

/**
 * Relay del reintegro masivo. Ejecuta lotes periódicamente; la concurrencia entre
 * instancias se resuelve en la BD al reclamar el proceso.
 */
@Component
@ConditionalOnProperty(name = "ticketya.devoluciones.reintegro.enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
public class ReintegroMasivoJob {

    private final ReintegroMasivoService reintegroMasivoService;

    @Scheduled(fixedDelayString = "${ticketya.devoluciones.reintegro.intervalo-ms:5000}")
    public void procesarPendientes() {
        reintegroMasivoService.procesarPendientes();
    }
}
