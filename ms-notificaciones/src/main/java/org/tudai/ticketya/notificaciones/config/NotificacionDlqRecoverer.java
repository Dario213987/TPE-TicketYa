package org.tudai.ticketya.notificaciones.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.retry.MessageRecoverer;
import org.springframework.stereotype.Component;
import org.tudai.ticketya.notificaciones.service.NotificacionService;

@Component
public class NotificacionDlqRecoverer implements MessageRecoverer {

    private static final Logger log = LoggerFactory.getLogger(NotificacionDlqRecoverer.class);

    private final ObjectMapper objectMapper;
    private final NotificacionService notificacionService;

    public NotificacionDlqRecoverer(ObjectMapper objectMapper, NotificacionService notificacionService) {
        this.objectMapper = objectMapper;
        this.notificacionService = notificacionService;
    }

    @Override
    public void recover(Message message, Throwable cause) {
        String mensajeId = obtenerMensajeId(message);
        if (mensajeId == null) {
            log.error("Reintentos agotados sin mensajeId: causa={}", cause.toString(), cause);
            throw new AmqpRejectAndDontRequeueException("Reintentos agotados sin mensajeId", cause);
        }

        notificacionService.marcarPendientesComoFallidos(mensajeId, cause);
        log.error("Reintentos agotados: mensajeId={}", mensajeId, cause);
        throw new AmqpRejectAndDontRequeueException("Reintentos agotados para " + mensajeId, cause);
    }

    private String obtenerMensajeId(Message message) {
        try {
            JsonNode body = objectMapper.readTree(message.getBody());
            JsonNode valor = body.get("mensajeId");
            if (valor != null && !valor.isNull() && !valor.asText().isBlank()) {
                return valor.asText();
            }
            return null;
        } catch (Exception e) {
            return null;
        }
    }
}
