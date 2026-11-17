package org.tudai.ticketya.tickets.notificacion;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.tudai.ticketya.contract.notificaciones.messaging.MensajeIdNotificacion;

import java.util.concurrent.Executor;

/**
 * Escucha confirmaciones de compra y publica la notificación solo tras commit exitoso,
 * evitando enviar mails por compras revertidas y desacoplando la compra del servicio de notificaciones.
 */
@Component
public class NotificacionCompraEventListener {

    private static final Logger log = LoggerFactory.getLogger(NotificacionCompraEventListener.class);

    private final NotificacionCompraPublicador publicador;
    private final Executor executor;

    public NotificacionCompraEventListener(NotificacionCompraPublicador publicador,
                                           @Qualifier("notificacionesAsyncExecutor") Executor executor) {
        this.publicador = publicador;
        this.executor = executor;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onCompraConfirmada(CompraConfirmadaNotificacionEvent event) {
        try {
            executor.execute(() -> {
                try {
                    publicador.producirConfirmacionCompra(event);
                } catch (RuntimeException e) {
                    log.error("No se pudo publicar notificación de compra (compra ya confirmada): mensajeId={}, reservaId={}",
                            mensajeId(event), event.getReservaId(), e);
                }
            });
        } catch (RuntimeException e) {
            log.error("No se pudo agendar notificación de compra (compra ya confirmada): mensajeId={}, reservaId={}",
                    mensajeId(event), event.getReservaId(), e);
        }
    }

    private String mensajeId(CompraConfirmadaNotificacionEvent event) {
        if (event.getReservaId() == null) {
            return null;
        }
        return MensajeIdNotificacion.generarDerivado(
                NotificacionCompraMensajeFactory.PREFIJO_NOTIFICACION, "RESERVA", event.getReservaId());
    }
}
