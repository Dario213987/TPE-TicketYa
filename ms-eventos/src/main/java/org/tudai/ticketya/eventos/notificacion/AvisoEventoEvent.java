package org.tudai.ticketya.eventos.notificacion;

import org.tudai.ticketya.contract.notificaciones.messaging.MotivoNotificacion;

public record AvisoEventoEvent(MotivoNotificacion motivo, Long eventoId, String mensajeId) {
}
