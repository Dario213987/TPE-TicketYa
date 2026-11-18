package org.tudai.ticketya.eventos.notificacion;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.tudai.ticketya.eventos.service.NotificacionEventoPublisher;

import java.util.concurrent.Executor;

@Component
public class AvisoEventoListener {

    private static final Logger log = LoggerFactory.getLogger(AvisoEventoListener.class);

    private final NotificacionEventoPublisher publicador;
    private final Executor executor;

    public AvisoEventoListener(NotificacionEventoPublisher publicador,
                               @Qualifier("notificacionesAsyncExecutor") Executor executor) {
        this.publicador = publicador;
        this.executor = executor;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onAviso(AvisoEventoEvent event) {
        try {
            executor.execute(() -> {
                try {
                    publicador.publicar(event.motivo(), event.eventoId(), event.mensajeId());
                } catch (RuntimeException e) {
                    log.error("No se pudo publicar el aviso {} del evento {} para ms-notificaciones: mensajeId={}",
                            event.motivo(), event.eventoId(), event.mensajeId(), e);
                }
            });
        } catch (RuntimeException e) {
            log.error("No se pudo agendar el aviso {} del evento {} para ms-notificaciones: mensajeId={}",
                    event.motivo(), event.eventoId(), event.mensajeId(), e);
        }
    }
}
