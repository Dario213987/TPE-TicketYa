package org.tudai.ticketya.contract.notificaciones.messaging;

import java.util.UUID;

public final class MensajeIdNotificacion {

    private MensajeIdNotificacion() {
    }

    public static String generar(String prefijo) {
        validarPrefijo(prefijo);
        return prefijo + "-" + System.currentTimeMillis() + "-" + UUID.randomUUID();
    }

    public static String generarDerivado(String prefijo, MotivoNotificacion motivo, long eventoId) {
        validarPrefijo(prefijo);
        if (motivo == null) {
            throw new IllegalArgumentException("El motivo es obligatorio");
        }
        if (eventoId <= 0) {
            throw new IllegalArgumentException("El eventoId debe ser mayor a cero");
        }
        return prefijo + "-" + motivo.name() + "-" + eventoId;
    }

    private static void validarPrefijo(String prefijo) {
        if (prefijo == null || prefijo.isBlank()) {
            throw new IllegalArgumentException("El prefijo es obligatorio");
        }
    }
}
