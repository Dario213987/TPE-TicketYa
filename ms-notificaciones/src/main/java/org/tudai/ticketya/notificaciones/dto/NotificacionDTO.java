package org.tudai.ticketya.notificaciones.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import org.tudai.ticketya.notificaciones.domain.EstadoNotificacion;
import org.tudai.ticketya.contract.notificaciones.messaging.MotivoNotificacion;

import java.time.LocalDateTime;

/**
 * Vista de una notificacion con los datos principales de su procesamiento.
 */
@Getter
@Setter
@AllArgsConstructor
public class NotificacionDTO {
    private MotivoNotificacion motivo;
    private Long destinatarioId;
    private Long eventoId;
    private EstadoNotificacion estado;
    private LocalDateTime fechaCreacion;
}
