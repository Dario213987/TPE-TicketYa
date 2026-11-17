package org.tudai.ticketya.tickets.domain;

/**
 * Medio con el que se pagó una reserva. Es un valor cerrado para que nunca se guarden datos
 * de la tarjeta (US-05.07, datos sensibles).
 */
public enum MedioPago {
    TARJETA_CREDITO, TARJETA_DEBITO
}
