package org.tudai.ticketya.notificaciones.controller;

import org.springframework.amqp.rabbit.annotation.RabbitHandler;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.tudai.ticketya.contract.notificaciones.messaging.ColasNotificacion;
import org.tudai.ticketya.contract.notificaciones.messaging.NotificacionEntranteDTO;
import org.tudai.ticketya.notificaciones.service.NotificacionService;

@Component
@RabbitListener(queues = ColasNotificacion.COLA_ENVIAR)
public class NotificacionListener {

    private final NotificacionService service;

    public NotificacionListener(NotificacionService service) {
        this.service = service;
    }

    @RabbitHandler
    public void receive(NotificacionEntranteDTO in) {
        service.procesarNotificacionEntrante(in);
    }
}
