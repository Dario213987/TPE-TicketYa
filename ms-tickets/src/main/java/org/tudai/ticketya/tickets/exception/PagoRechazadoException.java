package org.tudai.ticketya.tickets.exception;

import org.tudai.ticketya.core.exception.ConflictException;

/**
 * El pago de una reserva fue rechazado. Responde 409 con el motivo.
 */
public class PagoRechazadoException extends ConflictException {

    public PagoRechazadoException(String motivo) {
        super("El pago fue rechazado: " + motivo);
    }
}
