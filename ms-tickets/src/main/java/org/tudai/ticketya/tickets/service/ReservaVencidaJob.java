package org.tudai.ticketya.tickets.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.tudai.ticketya.tickets.config.ReservaProperties;

import java.time.Instant;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class ReservaVencidaJob {

    private static final String BLOQUEO = "liberar-reservas-vencidas";

    private final ReservaVencidaService reservaVencidaService;
    private final BloqueoDistribuidoService bloqueoDistribuidoService;
    private final ReservaProperties properties;

    @Scheduled(cron = "${tickets.reserva.liberacion-cron:0 * * * * *}")
    public void liberarReservasVencidas() {
        Optional<BloqueoDistribuidoService.Bloqueo> bloqueo =
                bloqueoDistribuidoService.adquirir(BLOQUEO, properties.getBloqueoLiberacion());

        if (bloqueo.isEmpty()) {
            log.debug("Otra instancia ya esta liberando las reservas vencidas");
            return;
        }

        try {
            int liberadas = reservaVencidaService.liberarVencidas(Instant.now());
            if (liberadas > 0) {
                log.info("Se liberaron {} reservas vencidas", liberadas);
            }
        } catch (RuntimeException e) {
            log.error("No se pudieron liberar las reservas vencidas", e);
            throw e;
        } finally {
            bloqueoDistribuidoService.liberar(bloqueo.get());
        }
    }
}
