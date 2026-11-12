package org.tudai.ticketya.tickets.domain;

/**
 * Estado de venta de la disponibilidad de un evento.
 * <p>
 * Solo un evento con la venta {@link #ABIERTA} admite nuevas reservas y el ingreso
 * a la fila virtual. Los estados se actualizan desde los endpoints internos que
 * consume {@code ms-eventos} cuando cancela, suspende, reactiva o finaliza el evento.
 */
public enum EstadoVenta {
    ABIERTA,
    AGOTADA,
    SUSPENDIDA,
    CANCELADA,
    FINALIZADA
}
