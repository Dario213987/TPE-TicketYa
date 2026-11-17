package org.tudai.ticketya.preferencias.messaging;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Configuración del consumidor de eventos que alimenta el historial (US-09.02).
 * <p>
 * Los nombres de los streams y del grupo siguen la convención de la arquitectura:
 * Redis Streams con grupo de consumidores y confirmación.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "preferencias.historial")
public class HistorialProperties {

    /** Permite apagar el consumidor (por ejemplo, en tests que no levantan Redis). */
    private boolean consumerEnabled = true;

    /** Stream que publica ms-tickets cuando se confirma una compra. */
    private String streamVentaPagada = "venta.pagada";

    /** Stream que publica ms-tickets cuando se usa una entrada. */
    private String streamTicketUtilizado = "ticket.utilizado";

    /** Grupo de consumidores de ms-preferencias. */
    private String grupo = "ms-preferencias";

    /** Nombre del consumidor dentro del grupo. */
    private String consumidor = "ms-preferencias-1";

    /** Prefijo de las claves de idempotencia por id de mensaje. */
    private String dedupePrefix = "preferencias:historial:procesados:";

    /** Cuánto se recuerda un mensaje ya procesado. */
    private Duration dedupeTtl = Duration.ofDays(7);

    /** Cada cuánto consulta el stream cuando no hay mensajes. */
    private Duration pollTimeout = Duration.ofSeconds(1);
}
