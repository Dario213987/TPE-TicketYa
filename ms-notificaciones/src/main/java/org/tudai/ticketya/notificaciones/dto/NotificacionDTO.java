package org.tudai.ticketya.notificaciones.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import org.tudai.ticketya.notificaciones.domain.EstadoNotificacion;
import org.tudai.ticketya.contract.notificaciones.messaging.MotivoNotificacion;

import java.time.LocalDateTime;

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
