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
    public static final String COLA_FALLIDAS = "notificaciones.fallidas";
    /**
     * Routing key de los mensajes fallidos. Se mantiene {@code enviar.dlq} porque la cola
     * {@code enviar} ya existe con ese {@code x-dead-letter-routing-key} y RabbitMQ no permite
     * redeclararla con otro argumento (PRECONDITION_FAILED); la cola de fallidos se bindea con
     * esta clave.
     */
    public static final String ROUTING_FALLIDAS = "enviar.dlq";

    private ColasNotificacion() {
    }
}
