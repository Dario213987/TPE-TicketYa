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

    public static String generarDerivado(String prefijo, String entidad, Long id) {
        if (prefijo == null || prefijo.isBlank()) {
            throw new IllegalArgumentException("El prefijo es obligatorio");
        }
        if (entidad == null || entidad.isBlank()) {
            throw new IllegalArgumentException("La entidad es obligatoria");
        }
        if (id == null) {
            throw new IllegalArgumentException("El id es obligatorio");
        }
        return prefijo + "-" + entidad + "-" + id;
    }
}
