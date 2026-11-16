package org.tudai.ticketya.notificaciones.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.tudai.ticketya.notificaciones.domain.MotivoNotificacion;

@Getter
@Setter
@NoArgsConstructor
public class NotificacionEntranteDTO {
    private String mensajeId;
    private MotivoNotificacion motivo;
    private long eventoId;
    private long destinatarioId;
}
