package org.tudai.ticketya.tickets.exception;

import org.springframework.http.HttpStatus;

public enum CodigoRechazoAcceso {
    NO_EXISTE(HttpStatus.NOT_FOUND),
    OTRO_EVENTO(HttpStatus.CONFLICT),
    YA_USADA(HttpStatus.CONFLICT),
    DEVUELTA(HttpStatus.CONFLICT);

    private final HttpStatus status;

    CodigoRechazoAcceso(HttpStatus status) {
        this.status = status;
    }

    public HttpStatus status() {
        return status;
    }
}
