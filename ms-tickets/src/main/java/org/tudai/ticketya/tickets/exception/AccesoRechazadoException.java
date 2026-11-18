package org.tudai.ticketya.tickets.exception;

/**
 * El ticket fue rechazado al validar el acceso. Responde 404 o 409 segun el
 * codigo y lleva el id del ticket para registrar la operacion sin exponer el
 * codigo QR en los logs.
 */
public class AccesoRechazadoException extends RuntimeException {

    private final CodigoRechazoAcceso codigo;
    private final Long ticketId;

    public AccesoRechazadoException(CodigoRechazoAcceso codigo, String mensaje) {
        this(codigo, mensaje, null);
    }

    public AccesoRechazadoException(CodigoRechazoAcceso codigo, String mensaje, Long ticketId) {
        super(mensaje);
        this.codigo = codigo;
        this.ticketId = ticketId;
    }

    public CodigoRechazoAcceso getCodigo() {
        return codigo;
    }

    public Long getTicketId() {
        return ticketId;
    }
}
