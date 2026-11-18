package org.tudai.ticketya.transacciones.service;

import org.tudai.ticketya.core.exception.RemoteServiceException;

/**
 * El circuito de la pasarela está abierto: la orden no se mandó a la pasarela, así que es seguro
 * que no se cobró ni acreditó nada (TKY-86).
 */
public class PasarelaNoDisponibleException extends RemoteServiceException {

    public static final String MOTIVO = "La pasarela de pagos no está disponible";

    public PasarelaNoDisponibleException(String idempotencyKey) {
        super(PasarelaResiliente.STATUS_NO_DISPONIBLE, MOTIVO + " (circuito abierto, orden " + idempotencyKey + ")");
    }
}
