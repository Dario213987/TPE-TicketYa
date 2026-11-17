package org.tudai.ticketya.notificaciones.service.plantilla;

import org.tudai.ticketya.contract.notificaciones.messaging.DetalleNotificacion;
import org.tudai.ticketya.contract.notificaciones.messaging.MotivoNotificacion;
import org.tudai.ticketya.notificaciones.client.dto.EventoClientDTO;
import org.tudai.ticketya.notificaciones.client.dto.UsuarioClientDTO;

public record DatosPlantilla(
        MotivoNotificacion motivo,
        EventoClientDTO evento,
        UsuarioClientDTO usuario,
        DetalleNotificacion detalle) {

    public String nombreEvento() {
        return evento != null && evento.getNombre() != null ? evento.getNombre() : "evento";
    }
}
