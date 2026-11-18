package org.tudai.ticketya.devoluciones.service;

import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.tudai.ticketya.contract.notificaciones.messaging.NotificacionEntranteDTO;

/**
 * Publica las notificaciones de cancelación en la cola que consume ms-notificaciones.
 * El mensajeId es determinístico (armado por el servicio de reintegro) para que un
 * reintento o una ejecución duplicada no envíe dos veces el mismo aviso.
 */
@Component
@RequiredArgsConstructor
public class NotificacionReintegroPublisher {

    private final RabbitTemplate rabbitTemplate;

    @Value("${ticketya.devoluciones.notificaciones.cola:enviar}")
    private String cola = "enviar";

    public void publicar(NotificacionEntranteDTO notificacion) {
        rabbitTemplate.convertAndSend(cola, notificacion);
    }
}
