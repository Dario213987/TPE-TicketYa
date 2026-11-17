package org.tudai.ticketya.eventos.scheduler;

import lombok.RequiredArgsConstructor;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.tudai.ticketya.eventos.service.NotificacionTicketsService;

/**
 * Relay de notificaciones pendientes hacia ms-tickets.
 * <p>
 * ShedLock evita que dos instancias procesen la misma tanda de notificaciones.
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        name = "ticketya.eventos.notificaciones.enabled",
        havingValue = "true",
        matchIfMissing = true)
public class NotificacionTicketsScheduler {

    private final NotificacionTicketsService notificacionTicketsService;

    @Scheduled(fixedDelayString = "${ticketya.eventos.notificaciones.fixed-delay-ms:60000}")
    @SchedulerLock(
            name = "notificacionTicketsRelay",
            lockAtMostFor = "${ticketya.eventos.notificaciones.lock-at-most-for:PT10M}",
            lockAtLeastFor = "${ticketya.eventos.notificaciones.lock-at-least-for:PT10S}")
    public void procesarPendientes() {
        notificacionTicketsService.procesarPendientes();
    }
}
