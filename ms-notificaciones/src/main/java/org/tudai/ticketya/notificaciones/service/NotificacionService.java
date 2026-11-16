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
     * Procesa un mensaje entrante de la cola: valida mensajeId, aplica idempotencia por mensajeId
     * (si ya está ENVIADO/ENVIANDO descarta y registra warn), enriquece con datos de evento/usuario
     * vía Feign, renderiza la plantilla según motivo pasando detalleCompra (si aplica) y envía el email.
     * No persiste detalleCompra; ante fallos propaga la excepción para activar reintentos/DLQ.
     */
    public void procesarNotificacionEntrante(NotificacionEntranteDTO entrante) {
        if (entrante == null || entrante.getMensajeId() == null || entrante.getMensajeId().isBlank()) {
            throw new IllegalArgumentException("mensajeId es obligatorio para procesar la notificación");
        }

        Notificacion notificacion = obtenerOCrearPendiente(entrante);
        if (notificacion.getEstado() == EstadoNotificacion.ENVIADO
                || notificacion.getEstado() == EstadoNotificacion.ENVIANDO) {
            log.warn("Mensaje duplicado descartado: {}", entrante.getMensajeId());
            return;
        }

        marcarEnviando(notificacion.getId());

        EventoClientDTO evento = eventoClient.obtenerEvento(entrante.getEventoId());
        UsuarioClientDTO usuario = usuarioClient.obtenerUsuario(entrante.getDestinatarioId());

        String asunto = plantillaEmailService.asunto(entrante.getMotivo(), evento, entrante.getDetalleCompra());
        String cuerpo = plantillaEmailService.renderizar(entrante.getMotivo(), evento, usuario, entrante.getDetalleCompra());

        emailService.enviar(usuario != null ? usuario.getEmail() : null, asunto, cuerpo);

        marcarEnviada(notificacion.getId());
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

    private Notificacion obtenerPorId(Long id) {
        return notificacionRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Notificación no encontrada: " + id));
    }
}
