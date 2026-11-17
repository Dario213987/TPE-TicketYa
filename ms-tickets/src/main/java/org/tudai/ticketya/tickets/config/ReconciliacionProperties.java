package org.tudai.ticketya.tickets.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Job de reconciliación de pagos pendientes (TKY-87).
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "tickets.reconciliacion")
public class ReconciliacionProperties {

    /** Tiempo después del vencimiento de la reserva antes de reconciliarla. */
    private Duration margen = Duration.ofMinutes(1);

    /** Duración máxima del bloqueo entre instancias. */
    private Duration bloqueo = Duration.ofMinutes(5);
}
