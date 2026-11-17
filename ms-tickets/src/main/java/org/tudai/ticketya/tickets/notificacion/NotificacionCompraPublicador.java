package org.tudai.ticketya.tickets.notificacion;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import org.tudai.ticketya.contract.notificaciones.messaging.AsientoNotificacion;
import org.tudai.ticketya.contract.notificaciones.messaging.ColasNotificacion;
import org.tudai.ticketya.contract.notificaciones.messaging.NotificacionEntranteDTO;

import java.math.BigDecimal;
import java.util.List;

/**
 * Único punto de publicación de notificaciones de confirmación de compra hacia la cola "enviar".
 * No propaga errores de publicación para no afectar la confirmación de la compra ya commiteada.
 */
@Component
public class NotificacionCompraPublicador {

    private static final Logger log = LoggerFactory.getLogger(NotificacionCompraPublicador.class);

    private final NotificacionCompraMensajeFactory factory;
    private final RabbitTemplate rabbitTemplate;

    public NotificacionCompraPublicador(NotificacionCompraMensajeFactory factory,
                                        RabbitTemplate rabbitTemplate) {
        this.factory = factory;
        this.rabbitTemplate = rabbitTemplate;
    }

    /**
     * Arma el mensaje de confirmación y lo publica en la cola de envío. Ante fallos (ej. broker caído)
     * registra el error y no relanza, preservando la compra ya confirmada.
     */
    public void producirConfirmacionCompra(Long eventoId, Long destinatarioId, Long sectorId,
                                           List<AsientoNotificacion> asientos, Integer cantidad,
                                           BigDecimal monto, String moneda) {
        NotificacionEntranteDTO mensaje = null;
        try {
            mensaje = factory.crear(eventoId, destinatarioId, sectorId, asientos, cantidad, monto, moneda);
            rabbitTemplate.convertAndSend(ColasNotificacion.COLA_ENVIAR, mensaje);
            log.info("Notificación de confirmación de compra publicada: mensajeId={}, eventoId={}, destinatarioId={}, motivo={}",
                    mensaje.getMensajeId(), eventoId, destinatarioId, mensaje.getMotivo());
        } catch (Exception e) {
            String mensajeId = mensaje != null ? mensaje.getMensajeId() : null;
            log.error("No se pudo publicar notificación de confirmación de compra (compra ya confirmada): mensajeId={}, eventoId={}, destinatarioId={}",
                    mensajeId, eventoId, destinatarioId, e);
        }
    }

    /**
     * Variante que recibe el evento de confirmación para uso desde listeners AFTER_COMMIT.
     */
    public void producirConfirmacionCompra(CompraConfirmadaNotificacionEvent event) {
        if (event == null) {
            return;
        }
        producirConfirmacionCompra(event.getEventoId(), event.getDestinatarioId(), event.getSectorId(),
                event.getAsientos(), event.getCantidad(), event.getMonto(), event.getMoneda());
    }
}
