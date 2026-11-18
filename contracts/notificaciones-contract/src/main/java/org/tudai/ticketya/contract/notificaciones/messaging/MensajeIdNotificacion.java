package org.tudai.ticketya.contract.notificaciones.messaging;

import java.util.UUID;

/**
 * Generadores de identificadores de mensajes de notificacion.
 * <p>
 * Los ids son la clave de idempotencia en {@code ms-notificaciones}. El formato
 * tradicional incluye timestamp y UUID; el derivado es estable por prefijo, motivo y
 * evento para que un reintento del mismo aviso masivo no genere un mensaje nuevo.
 */
public final class MensajeIdNotificacion {

    private MensajeIdNotificacion() {
    }

    /**
     * Genera un id unico con timestamp y UUID.
     *
     * @param prefijo prefijo del productor
     * @return id de mensaje unico
     */
    public static String generar(String prefijo) {
        validarPrefijo(prefijo);
        return prefijo + "-" + System.currentTimeMillis() + "-" + UUID.randomUUID();
    }

    /**
     * Genera un id estable a partir del prefijo, el motivo y el evento.
     *
     * @param prefijo prefijo del productor
     * @param motivo  motivo de la notificacion
     * @param eventoId id del evento
     * @return id derivado con formato {@code PREFIJO-MOTIVO-eventoId}
     */
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

    public static String generarDerivado(String prefijo, String entidad, Long id) {
        validarPrefijo(prefijo);
        if (entidad == null || entidad.isBlank()) {
            throw new IllegalArgumentException("La entidad es obligatoria");
        }
        if (id == null) {
            throw new IllegalArgumentException("El id es obligatorio");
        }
        return prefijo + "-" + entidad + "-" + id;
    }

    /**
     * Valida que el prefijo no este vacio.
     *
     * @param prefijo prefijo a validar
     */
    private static void validarPrefijo(String prefijo) {
        if (prefijo == null || prefijo.isBlank()) {
            throw new IllegalArgumentException("El prefijo es obligatorio");
        }
    }
}
