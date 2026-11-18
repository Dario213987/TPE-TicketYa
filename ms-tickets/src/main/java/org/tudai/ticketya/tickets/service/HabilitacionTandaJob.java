package org.tudai.ticketya.tickets.service;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Job que habilita a los siguientes N clientes de cada evento de alta demanda.
 * <p>
 * Puede correr en todas las instancias de {@code ms-tickets}: el reclamo de la
 * tanda en la base y el {@code ZPOPMIN} atomico en Redis evitan duplicados y
 * saltos.
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        name = "tickets.habilitacion.enabled",
        havingValue = "true",
        matchIfMissing = true)
public class HabilitacionTandaJob {

    private final HabilitacionTandaService habilitacionTandaService;

    @Scheduled(cron = "${tickets.habilitacion.tick-cron:*/5 * * * * *}")
    public void ejecutar() {
        habilitacionTandaService.ejecutarTandas();
    }
}
