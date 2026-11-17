package org.tudai.ticketya.tickets.exception;

public class AccesoRechazadoException extends RuntimeException {

    private final CodigoRechazoAcceso codigo;

    public AccesoRechazadoException(CodigoRechazoAcceso codigo, String mensaje) {
        super(mensaje);
        this.codigo = codigo;
    }

    public CodigoRechazoAcceso getCodigo() {
        return codigo;
    }
}
