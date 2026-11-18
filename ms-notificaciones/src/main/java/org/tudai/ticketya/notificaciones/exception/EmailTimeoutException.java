package org.tudai.ticketya.notificaciones.exception;

public class EmailTimeoutException extends RuntimeException {

    public EmailTimeoutException(String message, Throwable cause) {
        super(message, cause);
    }
}
