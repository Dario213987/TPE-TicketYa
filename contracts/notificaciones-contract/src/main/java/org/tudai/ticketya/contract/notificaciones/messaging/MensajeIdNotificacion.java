package org.tudai.ticketya.contract.notificaciones.messaging;

import java.util.UUID;

public final class MensajeIdNotificacion {

    private MensajeIdNotificacion() {
    }

    public static String generar(String prefijo) {
        if (prefijo == null || prefijo.isBlank()) {
            throw new IllegalArgumentException("El prefijo es obligatorio");
        }
        return prefijo + "-" + System.currentTimeMillis() + "-" + UUID.randomUUID();
    }
}
