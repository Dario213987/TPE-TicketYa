package org.tudai.ticketya.notificaciones.service.plantilla;

import org.tudai.ticketya.contract.notificaciones.messaging.DetalleNotificacion;
import org.tudai.ticketya.contract.notificaciones.messaging.MotivoNotificacion;
import org.tudai.ticketya.notificaciones.client.dto.EventoClientDTO;
import org.tudai.ticketya.notificaciones.client.dto.UsuarioClientDTO;

/**
 * Contexto comun que reciben las estrategias de plantilla.
 *
 * @param motivo  motivo de la notificacion
 * @param evento  datos del evento, si se pudieron obtener
 * @param usuario datos del destinatario, si se pudieron obtener
 * @param detalle detalle polimorfico del mensaje, si aplica
 */
public record DatosPlantilla(
        MotivoNotificacion motivo,
        EventoClientDTO evento,
        UsuarioClientDTO usuario,
        DetalleNotificacion detalle) {

    /**
     * Devuelve el nombre del evento o un texto por defecto cuando no esta disponible.
     *
     * @return nombre del evento o {@code "evento"}
     */
    public String nombreEvento() {
        return evento != null && evento.getNombre() != null ? evento.getNombre() : "evento";
    }
}
