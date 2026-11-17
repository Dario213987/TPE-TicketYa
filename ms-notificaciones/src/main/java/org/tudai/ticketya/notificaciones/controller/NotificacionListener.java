package org.tudai.ticketya.notificaciones.controller;

import org.springframework.amqp.rabbit.annotation.RabbitHandler;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.tudai.ticketya.contract.notificaciones.messaging.ColasNotificacion;
import org.tudai.ticketya.contract.notificaciones.messaging.NotificacionEntranteDTO;
import org.tudai.ticketya.notificaciones.service.NotificacionService;

/**
 * Listener de la cola principal de notificaciones.
 * <p>
 * Delega el procesamiento en {@link NotificacionService}; los errores se propagan
 * para que actue la politica de reintentos y la DLQ.
 */
@Component
@RabbitListener(queues = ColasNotificacion.COLA_ENVIAR)
public class NotificacionListener {

    private final NotificacionService service;

    /**
     * Crea el listener con el servicio que procesa los mensajes.
     *
     * @param service servicio de notificaciones
     */
    public NotificacionListener(NotificacionService service) {
        this.service = service;
    }

    /**
     * Recibe un mensaje de la cola y lo procesa.
     *
     * @param in mensaje entrante
     */
    @RabbitHandler
    public void receive(NotificacionEntranteDTO in) {
        service.procesarNotificacionEntrante(in);
    }
}
