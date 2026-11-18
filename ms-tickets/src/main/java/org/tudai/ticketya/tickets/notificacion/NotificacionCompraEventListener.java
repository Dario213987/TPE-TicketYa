package org.tudai.ticketya.tickets.notificacion;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Escucha confirmaciones de compra y publica la notificación solo tras commit exitoso,
 * evitando enviar mails por compras revertidas y desacoplando la compra del servicio de notificaciones.
 */
@Component
public class NotificacionCompraEventListener {

    private final NotificacionCompraPublicador publicador;

    public NotificacionCompraEventListener(NotificacionCompraPublicador publicador) {
        this.publicador = publicador;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onCompraConfirmada(CompraConfirmadaNotificacionEvent event) {
        publicador.producirConfirmacionCompra(event);
    }
}
