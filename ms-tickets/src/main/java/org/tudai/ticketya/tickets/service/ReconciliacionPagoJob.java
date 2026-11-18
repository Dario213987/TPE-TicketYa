package org.tudai.ticketya.tickets.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.tudai.ticketya.tickets.config.ReconciliacionProperties;

import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Job de reconciliación de la saga de compra (TKY-87): resuelve las reservas que quedaron en
 * PENDIENTE_PAGO (timeout o débito PENDIENTE en ms-transacciones) consultando el estado real de
 * sus débitos. Solo toma reservas vencidas hace más de {@code tickets.reconciliacion.margen}, así
 * no se cruza con un pago en línea. Con varias instancias corre en una sola (bloqueo en la base).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReconciliacionPagoJob {

    static final String BLOQUEO = "reconciliar-pagos";

    private final PagoService pagoService;
    private final ReservaPagoService reservaPagoService;
    private final BloqueoDistribuidoService bloqueoDistribuidoService;
    private final ReconciliacionProperties properties;

    @Scheduled(cron = "${tickets.reconciliacion.cron:0 * * * * *}")
    public void reconciliar() {
        Optional<BloqueoDistribuidoService.Bloqueo> bloqueo =
                bloqueoDistribuidoService.adquirir(BLOQUEO, properties.getBloqueo());
        if (bloqueo.isEmpty()) {
            log.debug("Otra instancia ya está reconciliando los pagos pendientes");
            return;
        }

        try {
            List<Long> reservas = reservaPagoService.pendientesVencidas(Instant.now().minus(properties.getMargen()));
            Map<PagoService.Reconciliacion, Integer> resultados = new EnumMap<>(PagoService.Reconciliacion.class);
            for (Long reservaId : reservas) {
                try {
                    resultados.merge(pagoService.reconciliar(reservaId), 1, Integer::sum);
                } catch (RuntimeException e) {
                    // ms-transacciones no respondió: la reserva queda igual para la próxima vuelta.
                    log.warn("Reconciliación: reserva={} sin cambios, ms-transacciones no respondió: {}",
                            reservaId, e.getMessage());
                }
            }
            if (!reservas.isEmpty()) {
                log.info("Reconciliación de pagos: {} reservas revisadas {}", reservas.size(), resultados);
            }
        } finally {
            bloqueoDistribuidoService.liberar(bloqueo.get());
        }
    }
}
