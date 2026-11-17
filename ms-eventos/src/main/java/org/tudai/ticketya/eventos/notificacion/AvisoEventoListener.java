package org.tudai.ticketya.eventos.notificacion;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.tudai.ticketya.eventos.service.NotificacionEventoPublisher;

@Component
public class AvisoEventoListener {

    private static final Logger log = LoggerFactory.getLogger(AvisoEventoListener.class);

    private final NotificacionEventoPublisher publicador;

    public AvisoEventoListener(NotificacionEventoPublisher publicador) {
        this.publicador = publicador;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onAviso(AvisoEventoEvent event) {
        try {
            publicador.publicar(event.motivo(), event.eventoId(), event.mensajeId());
        } catch (RuntimeException e) {
            log.error("No se pudo publicar el aviso {} del evento {} para ms-notificaciones",
                    event.motivo(), event.eventoId(), e);
        }
    }
}
