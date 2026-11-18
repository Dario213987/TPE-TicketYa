package org.tudai.ticketya.notificaciones.domain;

/**
 * Estados por los que pasa una notificacion durante su procesamiento.
 * <p>
 * {@code PENDIENTE} y {@code FALLIDO} admiten reintentos. {@code ENVIADO},
 * {@code ENVIANDO} y {@code NO_ENTREGABLE} se omiten para evitar duplicados.
 */
public enum EstadoNotificacion {
    PENDIENTE,
    ENVIANDO,
    ENVIADO,
    FALLIDO,
    NO_ENTREGABLE
}
