package org.tudai.ticketya.core.exception;

public class InvalidUserContextException extends BadRequestException {
    public InvalidUserContextException(String message) {
        super(message);
    }
}
