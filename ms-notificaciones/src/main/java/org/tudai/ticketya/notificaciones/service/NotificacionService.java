package org.tudai.ticketya.notificaciones.service;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.tudai.ticketya.contract.notificaciones.messaging.NotificacionEntranteDTO;
import org.tudai.ticketya.notificaciones.client.EventoClient;
import org.tudai.ticketya.notificaciones.client.TicketsClient;
import org.tudai.ticketya.notificaciones.client.UsuarioClient;
import org.tudai.ticketya.notificaciones.client.dto.EventoClientDTO;
import org.tudai.ticketya.notificaciones.client.dto.UsuarioClientDTO;
import org.tudai.ticketya.notificaciones.domain.EstadoNotificacion;
import org.tudai.ticketya.notificaciones.domain.Notificacion;
import org.tudai.ticketya.notificaciones.repository.NotificacionRepository;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Orquesta el procesamiento de una notificacion: idempotencia, expansion de avisos
 * masivos, enriquecimiento con datos remotos, renderizado de la plantilla y envio por SMTP.
 */
@Service
@RequiredArgsConstructor
public class NotificacionService {

    private static final Logger log = LoggerFactory.getLogger(NotificacionService.class);

    private final EventoClient eventoClient;
    private final UsuarioClient usuarioClient;
    private final TicketsClient ticketsClient;
    private final PlantillaEmailService plantillaEmailService;
    private final EmailService emailService;
    private final NotificacionRepository notificacionRepository;
    private final TransactionTemplate transactionTemplate;

    /**
     * Procesa un mensaje entrante de la cola. Si trae {@code destinatarioId}, envia una
     * sola notificacion. Si no lo trae, consulta los compradores de {@code ms-tickets} y
     * genera una notificacion por comprador. Los avisos masivos se expanden en una unica
     * transaccion antes de enviar emails, de modo que un fallo de {@code ms-tickets} no
     * deja filas parciales.
     * <p>
     * Los estados {@code ENVIADO}, {@code ENVIANDO} y {@code NO_ENTREGABLE} se omiten
     * para no duplicar envios. {@code PENDIENTE} y {@code FALLIDO} se reintentan.
     *
     * @param entrante mensaje recibido de la cola
     * @throws IllegalArgumentException si falta mensajeId o motivo
     */
    public void procesarNotificacionEntrante(NotificacionEntranteDTO entrante) {
        validarEntrante(entrante);

        if (entrante.getDestinatarioId() != null) {
            Notificacion notificacion = obtenerOCrearPendiente(entrante, entrante.getDestinatarioId());
            procesarNotificacion(entrante, notificacion);
            return;
        }

        procesarAvisoMasivo(entrante);
    }

    private void validarEntrante(NotificacionEntranteDTO entrante) {
        if (entrante == null || entrante.getMensajeId() == null || entrante.getMensajeId().isBlank()) {
            throw new IllegalArgumentException("mensajeId es obligatorio para procesar la notificacion");
        }
        if (entrante.getMotivo() == null) {
            throw new IllegalArgumentException("motivo es obligatorio para procesar la notificacion");
        }
        entrante.validarDestinatario();
    }

    private void procesarAvisoMasivo(NotificacionEntranteDTO entrante) {
        List<Notificacion> notificaciones = obtenerOCrearNotificacionesMasivas(entrante);
        if (notificaciones.isEmpty()) {
            log.warn("Aviso masivo sin compradores para enviar: {}", entrante.getMensajeId());
            return;
        }

        List<RuntimeException> fallos = new ArrayList<>();
        for (Notificacion notificacion : notificaciones) {
            try {
                procesarNotificacion(entrante, notificacion);
            } catch (RuntimeException e) {
                fallos.add(e);
            }
        }

        if (!fallos.isEmpty()) {
            RuntimeException error = new RuntimeException(
                    "Fallo el envio de " + fallos.size() + " notificaciones del mensaje "
                            + entrante.getMensajeId(),
                    fallos.get(0));
            fallos.stream().skip(1).forEach(error::addSuppressed);
            throw error;
        }
    }

    /**
     * Recupera las notificaciones de un aviso masivo o las crea todas juntas. Si ya
     * existen filas para el mensaje, no vuelve a consultar {@code ms-tickets}; asi un
     * reintento puede continuar sin depender del servicio de tickets.
     *
     * @param entrante mensaje masivo
     * @return notificaciones existentes o recien creadas
     */
    private List<Notificacion> obtenerOCrearNotificacionesMasivas(NotificacionEntranteDTO entrante) {
        List<Notificacion> existentes = notificacionRepository
                .findByMensajeIdOrderByIdAsc(entrante.getMensajeId());
        if (!existentes.isEmpty()) {
            return existentes;
        }

        List<Long> compradores = ticketsClient.obtenerCompradores(entrante.getEventoId());
        if (compradores == null || compradores.isEmpty()) {
            return List.of();
        }

        List<Long> destinatarios = compradores.stream()
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (destinatarios.isEmpty()) {
            return List.of();
        }

        try {
            List<Notificacion> creadas = transactionTemplate.execute(
                    status -> crearPendientes(entrante, destinatarios));
            return creadas != null ? creadas : List.of();
        } catch (DataIntegrityViolationException e) {
            return notificacionRepository.findByMensajeIdOrderByIdAsc(entrante.getMensajeId());
        }
    }

    private List<Notificacion> crearPendientes(NotificacionEntranteDTO entrante, List<Long> destinatarios) {
        LocalDateTime ahora = LocalDateTime.now();
        List<Notificacion> nuevas = destinatarios.stream()
                .map(destinatarioId -> nuevaPendiente(entrante, destinatarioId, ahora))
                .toList();
        notificacionRepository.saveAllAndFlush(nuevas);
        return nuevas;
    }

    private void procesarNotificacion(NotificacionEntranteDTO entrante, Notificacion notificacion) {
        EstadoNotificacion estadoActual = notificacion.getEstado();
        if (estadoActual == EstadoNotificacion.ENVIADO
                || estadoActual == EstadoNotificacion.ENVIANDO
                || estadoActual == EstadoNotificacion.NO_ENTREGABLE) {
            log.warn("Mensaje duplicado omitido: mensajeId={}, destinatarioId={}, estado={}",
                    entrante.getMensajeId(), notificacion.getDestinatarioId(), estadoActual);
            return;
        }

        try {
            EventoClientDTO evento = eventoClient.obtenerEvento(entrante.getEventoId());
            UsuarioClientDTO usuario = usuarioClient.obtenerUsuario(notificacion.getDestinatarioId());

            String email = usuario != null ? usuario.getEmail() : null;
            if (email == null || email.isBlank()) {
                log.warn("Notificacion no entregable (sin email): mensajeId={}, destinatarioId={}",
                        entrante.getMensajeId(), notificacion.getDestinatarioId());
                marcarNoEntregable(notificacion.getId());
                return;
            }

            String asunto = plantillaEmailService.asunto(entrante, evento);
            String cuerpo = plantillaEmailService.renderizar(entrante, evento, usuario);

            marcarEnviando(notificacion.getId());
            emailService.enviar(email, asunto, cuerpo);
        } catch (RuntimeException e) {
            marcarFallida(notificacion.getId());
            throw e;
        }

        marcarEnviada(notificacion.getId());
    }

    /**
     * Busca la notificacion por {@code (mensajeId, destinatarioId)} o crea una nueva en
     * estado {@code PENDIENTE}. La restriccion unica compuesta protege la idempotencia
     * ante consumidores concurrentes.
     *
     * @param entrante       mensaje de notificacion
     * @param destinatarioId id del destinatario
     * @return notificacion existente o recien creada
     */
    @Transactional
    public Notificacion obtenerOCrearPendiente(NotificacionEntranteDTO entrante, long destinatarioId) {
        Optional<Notificacion> existente = notificacionRepository
                .findByMensajeIdAndDestinatarioId(entrante.getMensajeId(), destinatarioId);
        if (existente.isPresent()) {
            return existente.get();
        }
        try {
            Notificacion notificacion = nuevaPendiente(entrante, destinatarioId, LocalDateTime.now());
            return notificacionRepository.saveAndFlush(notificacion);

        } catch (DataIntegrityViolationException e) {
            return notificacionRepository
                    .findByMensajeIdAndDestinatarioId(entrante.getMensajeId(), destinatarioId)
                    .orElseThrow(() -> e);
        }
    }

    private Notificacion nuevaPendiente(NotificacionEntranteDTO entrante, long destinatarioId,
            LocalDateTime fechaCreacion) {
        Notificacion notificacion = new Notificacion();
        notificacion.setMensajeId(entrante.getMensajeId());
        notificacion.setMotivo(entrante.getMotivo());
        notificacion.setEventoId(entrante.getEventoId());
        notificacion.setDestinatarioId(destinatarioId);
        notificacion.setEstado(EstadoNotificacion.PENDIENTE);
        notificacion.setFechaCreacion(fechaCreacion);
        notificacion.setFechaEnvio(null);
        return notificacion;
    }

    /**
     * Marca la notificacion como {@code ENVIANDO} antes de llamar al servidor SMTP.
     *
     * @param id id de la notificacion
     */
    @Transactional
    public void marcarEnviando(Long id) {
        Notificacion notificacion = obtenerPorId(id);
        notificacion.setEstado(EstadoNotificacion.ENVIANDO);
        notificacionRepository.save(notificacion);
    }

    /**
     * Marca la notificacion como {@code ENVIADO} y registra la fecha de envio.
     *
     * @param id id de la notificacion
     */
    @Transactional
    public void marcarEnviada(Long id) {
        Notificacion notificacion = obtenerPorId(id);
        notificacion.setEstado(EstadoNotificacion.ENVIADO);
        notificacion.setFechaEnvio(LocalDateTime.now());
        notificacionRepository.save(notificacion);
    }

    /**
     * Marca la notificacion como {@code FALLIDO} para permitir reintentos posteriores.
     *
     * @param id id de la notificacion
     */
    @Transactional
    public void marcarFallida(Long id) {
        Notificacion notificacion = obtenerPorId(id);
        notificacion.setEstado(EstadoNotificacion.FALLIDO);
        notificacionRepository.save(notificacion);
    }

    /**
     * Marca la notificacion como {@code NO_ENTREGABLE} cuando el usuario no tiene email.
     *
     * @param id id de la notificacion
     */
    @Transactional
    public void marcarNoEntregable(Long id) {
        Notificacion notificacion = obtenerPorId(id);
        notificacion.setEstado(EstadoNotificacion.NO_ENTREGABLE);
        notificacionRepository.save(notificacion);
    }

    /**
     * Busca una notificacion por id o falla si no existe.
     *
     * @param id id de la notificacion
     * @return notificacion encontrada
     */
    private Notificacion obtenerPorId(Long id) {
        return notificacionRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Notificacion no encontrada: " + id));
    }
}
