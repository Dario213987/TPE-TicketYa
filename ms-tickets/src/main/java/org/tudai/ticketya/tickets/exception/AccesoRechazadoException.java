package org.tudai.ticketya.tickets.exception;

import org.tudai.ticketya.core.exception.ConflictException;

/**
 * El ticket fue rechazado al validar el acceso. Responde 409 y lleva el id del
 * ticket para registrar la operación sin exponer el código QR en los logs.
 */
public class AccesoRechazadoException extends ConflictException {

    private final Long ticketId;

    public AccesoRechazadoException(String motivo, Long ticketId) {
        super(motivo);
        this.ticketId = ticketId;
    }

    public Long getTicketId() {
        return ticketId;
    }
}
