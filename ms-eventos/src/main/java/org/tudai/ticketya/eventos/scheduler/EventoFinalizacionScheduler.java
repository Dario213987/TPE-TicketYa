package org.tudai.ticketya.eventos.scheduler;

import lombok.RequiredArgsConstructor;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.tudai.ticketya.eventos.service.EventoService;

/**
 * Dispara la finalización de eventos vencidos.
 * <p>
 * ShedLock garantiza que, aunque haya varias instancias de ms-eventos,
 * solo una ejecute el proceso en cada ventana.
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        name = "ticketya.eventos.finalizacion.enabled",
        havingValue = "true",
        matchIfMissing = true)
public class EventoFinalizacionScheduler {

    private final EventoService eventoService;

    @Scheduled(cron = "${ticketya.eventos.finalizacion.cron:0 0 * * * *}")
    @SchedulerLock(
            name = "eventoFinalizacionScheduler",
            lockAtMostFor = "${ticketya.eventos.finalizacion.lock-at-most-for:PT15M}",
            lockAtLeastFor = "${ticketya.eventos.finalizacion.lock-at-least-for:PT1M}")
    public void finalizarEventosVencidos() {
        eventoService.finalizarEventosVencidos();
    }
}
