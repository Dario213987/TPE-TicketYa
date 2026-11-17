package org.tudai.ticketya.contract.notificaciones.messaging;

public final class ColasNotificacion {

    public static final String COLA_ENVIAR = "enviar";
    public static final String EXCHANGE_DLX = "enviar.dlx";
    public static final String ROUTING_DLQ = "enviar.dlq";

    private ColasNotificacion() {
    }
}
