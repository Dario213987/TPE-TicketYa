package org.tudai.ticketya.notificaciones.service;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.tudai.ticketya.contract.notificaciones.messaging.NotificacionEntranteDTO;
import org.tudai.ticketya.notificaciones.client.EventoClient;
import org.tudai.ticketya.notificaciones.client.UsuarioClient;
import org.tudai.ticketya.notificaciones.client.dto.EventoClientDTO;
import org.tudai.ticketya.notificaciones.client.dto.UsuarioClientDTO;
import org.tudai.ticketya.notificaciones.domain.EstadoNotificacion;
import org.tudai.ticketya.notificaciones.domain.Notificacion;
import org.tudai.ticketya.notificaciones.repository.NotificacionRepository;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class NotificacionService {

    private static final Logger log = LoggerFactory.getLogger(NotificacionService.class);

    private final EventoClient eventoClient;
    private final UsuarioClient usuarioClient;
    private final PlantillaEmailService plantillaEmailService;
    private final EmailService emailService;
    private final NotificacionRepository notificacionRepository;

    /**
     * Procesa un mensaje entrante de la cola: valida mensajeId, aplica idempotencia (ENVIADO/NO_ENTREGABLE
     * se descartan), permite reintentar mensajes ENVIANDO/FALLIDO, enriquece con evento/usuario via Feign,
     * arma la plantilla y marca ENVIANDO justo antes del envio SMTP. Ante fallo reintentable persiste
     * FALLIDO y relanza para activar reintentos/DLQ; si no hay email marca NO_ENTREGABLE y no reintenta.
     * No persiste detalleCompra.
     */
    public void procesarNotificacionEntrante(NotificacionEntranteDTO entrante) {
        if (entrante == null || entrante.getMensajeId() == null || entrante.getMensajeId().isBlank()) {
            throw new IllegalArgumentException("mensajeId es obligatorio para procesar la notificacion");
        }
        if (entrante.getMotivo() == null) {
            throw new IllegalArgumentException("motivo es obligatorio para procesar la notificacion");
        }
        if (entrante.getDestinatarioId() == null) {
            throw new UnsupportedOperationException(
                    "El aviso masivo sin destinatario todavia no esta soportado");
        }

        Notificacion notificacion = obtenerOCrearPendiente(entrante);
        EstadoNotificacion estadoActual = notificacion.getEstado();
        if (estadoActual == EstadoNotificacion.ENVIADO || estadoActual == EstadoNotificacion.NO_ENTREGABLE) {
            log.warn("Mensaje duplicado descartado: {}", entrante.getMensajeId());
            return;
        }

        try {
            EventoClientDTO evento = eventoClient.obtenerEvento(entrante.getEventoId());
            UsuarioClientDTO usuario = usuarioClient.obtenerUsuario(entrante.getDestinatarioId());

            String email = usuario != null ? usuario.getEmail() : null;
            if (email == null || email.isBlank()) {
                log.warn("Notificacion no entregable (sin email): mensajeId={}, destinatarioId={}",
                        entrante.getMensajeId(), entrante.getDestinatarioId());
                marcarNoEntregable(notificacion.getId());
                return;
            }

            String asunto = plantillaEmailService.asunto(entrante, evento);
            String cuerpo = plantillaEmailService.renderizar(entrante, evento, usuario);

            marcarEnviando(notificacion.getId());
            emailService.enviar(email, asunto, cuerpo);
            marcarEnviada(notificacion.getId());
        } catch (Exception e) {
            marcarFallida(notificacion.getId());
            throw e;
        }
    }

    @Transactional
    public Notificacion obtenerOCrearPendiente(NotificacionEntranteDTO entrante) {
        Optional<Notificacion> existente = notificacionRepository.findByMensajeId(entrante.getMensajeId());
        if (existente.isPresent()) {
            return existente.get();
        }
        try {
            Notificacion notificacion = new Notificacion();
            notificacion.setMensajeId(entrante.getMensajeId());
            notificacion.setMotivo(entrante.getMotivo());
            notificacion.setEventoId(entrante.getEventoId());
            notificacion.setDestinatarioId(entrante.getDestinatarioId());
            notificacion.setEstado(EstadoNotificacion.PENDIENTE);
            notificacion.setFechaCreacion(LocalDateTime.now());
            notificacion.setFechaEnvio(null);
            return notificacionRepository.saveAndFlush(notificacion);

        } catch (DataIntegrityViolationException e) {
            return notificacionRepository.findByMensajeId(entrante.getMensajeId())
                    .orElseThrow(() -> e);
        }
    }

    @Transactional
    public void marcarEnviando(Long id) {
        Notificacion notificacion = obtenerPorId(id);
        notificacion.setEstado(EstadoNotificacion.ENVIANDO);
        notificacionRepository.save(notificacion);
    }

    @Transactional
    public void marcarEnviada(Long id) {
        Notificacion notificacion = obtenerPorId(id);
        notificacion.setEstado(EstadoNotificacion.ENVIADO);
        notificacion.setFechaEnvio(LocalDateTime.now());
        notificacionRepository.save(notificacion);
    }

    @Transactional
    public void marcarFallida(Long id) {
        Notificacion notificacion = obtenerPorId(id);
        notificacion.setEstado(EstadoNotificacion.FALLIDO);
        notificacionRepository.save(notificacion);
    }

    @Transactional
    public void marcarNoEntregable(Long id) {
        Notificacion notificacion = obtenerPorId(id);
        notificacion.setEstado(EstadoNotificacion.NO_ENTREGABLE);
        notificacionRepository.save(notificacion);
    }

    private Notificacion obtenerPorId(Long id) {
        return notificacionRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Notificacion no encontrada: " + id));
    }
}
