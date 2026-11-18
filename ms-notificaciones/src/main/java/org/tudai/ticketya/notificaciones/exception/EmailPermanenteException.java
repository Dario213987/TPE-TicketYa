package org.tudai.ticketya.notificaciones.exception;

public class EmailPermanenteException extends RuntimeException {

    public enum Tipo {
        NO_ENTREGABLE,
        FALLIDO
    }

    private final Tipo tipo;

    public EmailPermanenteException(String message, Throwable cause, Tipo tipo) {
        super(message, cause);
        this.tipo = tipo;
    }

    public Tipo getTipo() {
        return tipo;
    }
}
