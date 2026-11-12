package org.tudai.ticketya.eventos.exception;

import org.tudai.ticketya.core.exception.BadRequestException;

public class FechaEventoInvalidaException extends BadRequestException {
    public FechaEventoInvalidaException(String message) {
        super(message);
    }
}
