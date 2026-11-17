package org.tudai.ticketya.contract.notificaciones.messaging;

/**
 * Asiento numerado incluido en el detalle de una confirmacion de compra.
 */
public class AsientoNotificacion {

    private Integer fila;
    private Integer numero;

    public AsientoNotificacion() {
    }

    public AsientoNotificacion(Integer fila, Integer numero) {
        this.fila = fila;
        this.numero = numero;
    }

    public Integer getFila() {
        return fila;
    }

    public void setFila(Integer fila) {
        this.fila = fila;
    }

    public Integer getNumero() {
        return numero;
    }

    public void setNumero(Integer numero) {
        this.numero = numero;
    }
}
