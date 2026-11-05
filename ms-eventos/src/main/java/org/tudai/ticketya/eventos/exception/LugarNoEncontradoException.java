package org.tudai.ticketya.eventos.exception;

import org.tudai.ticketya.core.exception.NotFoundException;

public class LugarNoEncontradoException extends NotFoundException {
    public LugarNoEncontradoException(String message) {
        super(message);
    }
}
