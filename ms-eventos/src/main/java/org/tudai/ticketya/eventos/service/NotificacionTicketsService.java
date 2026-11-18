package org.tudai.ticketya.eventos.service;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.tudai.ticketya.core.MdcKeys;
import org.tudai.ticketya.eventos.client.TicketsClient;
import org.tudai.ticketya.eventos.domain.EstadoNotificacion;
import org.tudai.ticketya.eventos.domain.EventoNotificacion;
import org.tudai.ticketya.eventos.domain.TipoNotificacionTickets;
import org.tudai.ticketya.eventos.repository.EventoNotificacionRepository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Gestiona las notificaciones pendientes hacia ms-tickets.
 * <p>
 * El alta de la notificación ocurre en la misma transacción que el cambio de
 * estado del evento. El envío se hace después, desde un relay, para no mantener
 * la transacción abierta mientras se llama por HTTP y para poder reintentar si
 * ms-tickets no está disponible.
 */
@Service
@RequiredArgsConstructor
public class NotificacionTicketsService {

    private static final Logger log = LoggerFactory.getLogger(NotificacionTicketsService.class);

    private final EventoNotificacionRepository notificacionRepository;
    private final TicketsClient ticketsClient;

    @Value("${ticketya.eventos.notificaciones.max-intentos:5}")
    private int maxIntentos = 5;

    @Value("${ticketya.eventos.notificaciones.batch-size:50}")
    private int batchSize = 50;

    /**
     * Encola una notificación si todavía no existe para el evento y tipo indicados.
     * Se ejecuta dentro de la transacción del cambio de estado.
     */
    @Transactional
    public void encolar(Long eventoId, TipoNotificacionTickets tipo) {
        if (notificacionRepository.existsByEventoIdAndTipo(eventoId, tipo)) {
            return;
        }

        notificacionRepository.save(EventoNotificacion.builder()
                .eventoId(eventoId)
                .tipo(tipo)
                .estado(EstadoNotificacion.PENDIENTE)
                .intentos(0)
                .proximoIntento(Instant.now())
                .build());
    }

    /**
     * Envía las notificaciones pendientes cuyo próximo intento ya venció.
     * No se ejecuta dentro de una transacción para no mantenerla abierta
     * durante las llamadas HTTP.
     */
    public void procesarPendientes() {
        List<EventoNotificacion> pendientes = notificacionRepository
                .findByEstadoAndProximoIntentoLessThanEqualOrderByCreadoElAsc(
                        EstadoNotificacion.PENDIENTE,
                        Instant.now(),
                        PageRequest.of(0, batchSize));

        for (EventoNotificacion notificacion : pendientes) {
            enviar(notificacion);
        }
    }

    private void enviar(EventoNotificacion notificacion) {
        MDC.put(MdcKeys.CORRELATION_ID, UUID.randomUUID().toString());
        try {
            Runnable envio = switch (notificacion.getTipo()) {
                case FINALIZAR -> () -> ticketsClient.finalizarEvento(notificacion.getEventoId());
            };
            envio.run();
        } catch (Exception e) {
            registrarFallo(notificacion, e);
            return;
        } finally {
            MDC.remove(MdcKeys.CORRELATION_ID);
        }

        notificacion.setEstado(EstadoNotificacion.ENVIADO);
        notificacion.setProximoIntento(null);
        notificacionRepository.save(notificacion);
    }

    private void registrarFallo(EventoNotificacion notificacion, Exception error) {
        int intentos = notificacion.getIntentos() + 1;
        notificacion.setIntentos(intentos);

        if (intentos >= maxIntentos) {
            notificacion.setEstado(EstadoNotificacion.FALLIDO);
            notificacion.setProximoIntento(null);
            log.error("No se pudo notificar a ms-tickets la finalización del evento {} tras {} intentos",
                    notificacion.getEventoId(), intentos, error);
        } else {
            notificacion.setEstado(EstadoNotificacion.PENDIENTE);
            notificacion.setProximoIntento(Instant.now().plusSeconds(backoffSegundos(intentos)));
            log.warn("No se pudo notificar a ms-tickets la finalización del evento {}. Reintento {} en {} segundos",
                    notificacion.getEventoId(), intentos, backoffSegundos(intentos), error);
        }

        notificacionRepository.save(notificacion);
    }

    private static long backoffSegundos(int intentos) {
        return switch (intentos) {
            case 1 -> 60;
            case 2 -> 300;
            case 3 -> 900;
            default -> 3600;
        };
    }
}
