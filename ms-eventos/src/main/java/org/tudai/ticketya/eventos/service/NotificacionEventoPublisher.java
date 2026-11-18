package org.tudai.ticketya.eventos.service;

import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;
import org.tudai.ticketya.contract.notificaciones.messaging.ColasNotificacion;
import org.tudai.ticketya.contract.notificaciones.messaging.MotivoNotificacion;
import org.tudai.ticketya.contract.notificaciones.messaging.NotificacionEntranteDTO;

/**
 * Publica en RabbitMQ los avisos de evento que consume {@code ms-notificaciones}.
 * <p>
 * El mensaje es masivo ({@code destinatarioId} nulo); el {@code mensajeId} lo define
 * quien origina el cambio para que reintentos del mismo aviso no generen emails nuevos.
 */
@Service
@RequiredArgsConstructor
public class NotificacionEventoPublisher {

    private final RabbitTemplate rabbitTemplate;

    public void publicar(MotivoNotificacion motivo, Long eventoId, String mensajeId) {
        NotificacionEntranteDTO mensaje = new NotificacionEntranteDTO();
        mensaje.setMensajeId(mensajeId);
        mensaje.setMotivo(motivo);
        mensaje.setEventoId(eventoId);
        mensaje.setDestinatarioId(null);
        mensaje.setDetalle(null);

        rabbitTemplate.convertAndSend(ColasNotificacion.COLA_ENVIAR, mensaje);
    }
}
