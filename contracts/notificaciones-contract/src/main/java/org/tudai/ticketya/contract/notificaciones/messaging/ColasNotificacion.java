package org.tudai.ticketya.contract.notificaciones.messaging;

/**
 * Nombres compartidos de las colas y el exchange de notificaciones.
 * <p>
 * Productores y consumidores usan las mismas constantes para evitar diferencias en
 * los argumentos de declaracion y en la DLQ.
 */
public final class ColasNotificacion {

    public static final String COLA_ENVIAR = "enviar";
    public static final String EXCHANGE_DLX = "enviar.dlx";
    public static final String ROUTING_DLQ = "enviar.dlq";

    private ColasNotificacion() {
    }
}
