package org.tudai.ticketya.eventos.scheduler;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.tudai.ticketya.eventos.service.EventoService;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        name = "ticketya.eventos.finalizacion.enabled",
        havingValue = "true",
        matchIfMissing = true)
public class EventoFinalizacionScheduler {

    private final EventoService eventoService;

    @Scheduled(cron = "${ticketya.eventos.finalizacion.cron:0 0 * * * *}")
    public void finalizarEventosVencidos() {
        eventoService.finalizarEventosVencidos();
    }
}
