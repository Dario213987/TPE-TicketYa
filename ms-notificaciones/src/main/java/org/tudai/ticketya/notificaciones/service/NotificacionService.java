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
import org.tudai.ticketya.notificaciones.client.UsuarioClient;
import org.tudai.ticketya.notificaciones.client.dto.EventoClientDTO;
import org.tudai.ticketya.notificaciones.client.dto.UsuarioClientDTO;
import org.tudai.ticketya.notificaciones.domain.EstadoNotificacion;
import org.tudai.ticketya.notificaciones.exception.EmailConexionException;
import org.tudai.ticketya.notificaciones.exception.EmailPermanenteException;
import org.tudai.ticketya.notificaciones.exception.EmailTimeoutException;
import org.tudai.ticketya.notificaciones.service.destinatarios.DestinatariosResolverRegistry;
import org.tudai.ticketya.notificaciones.domain.Notificacion;
import org.tudai.ticketya.notificaciones.repository.NotificacionRepository;
import org.tudai.ticketya.notificaciones.util.ResumidorErrores;

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
    private final DestinatariosResolverRegistry destinatariosResolverRegistry;
    private final PlantillaEmailService plantillaEmailService;
    private final EmailService emailService;
    private final NotificacionRepository notificacionRepository;
    private final TransactionTemplate transactionTemplate;

    /**
     * Procesa un mensaje entrante de la cola. Si trae {@code destinatarioId}, envia una
     * sola notificacion. Si no lo trae, resuelve los destinatarios segun el motivo y
     * genera una notificacion por destinatario. Los avisos masivos se expanden en una
     * unica transaccion antes de enviar emails, de modo que un fallo del resolvedor no
     * deja filas parciales.
     * <p>
     * Los estados {@code ENVIADO}, {@code ENVIANDO} y {@code NO_ENTREGABLE} se omiten
     * para no duplicar envios. {@code PENDIENTE} y {@code FALLIDO} se reintentan. Los
     * fallos de conexion SMTP devuelven la fila a {@code PENDIENTE}; los permanentes no
     * se reintentan y los timeouts dejan la fila en {@code ENVIANDO}.
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
            log.warn("Aviso masivo sin destinatarios para enviar: {}", entrante.getMensajeId());
            return;
        }

        List<RuntimeException> fallos = new ArrayList<>();
        for (Notificacion notificacion : notificaciones) {
            try {
                procesarNotificacion(entrante, notificacion);
            } catch (EmailConexionException e) {
                throw e;
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
     * existen filas para el mensaje, no vuelve a resolver destinatarios; asi un reintento
     * puede continuar sin depender de servicios remotos.
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

        List<Long> resueltos = destinatariosResolverRegistry
                .resolver(entrante.getMotivo())
                .resolver(entrante);
        if (resueltos == null || resueltos.isEmpty()) {
            return List.of();
        }

        List<Long> destinatarios = resueltos.stream()
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

        EventoClientDTO evento;
        UsuarioClientDTO usuario;
        try {
            evento = eventoClient.obtenerEvento(entrante.getEventoId());
            usuario = usuarioClient.obtenerUsuario(notificacion.getDestinatarioId());
        } catch (RuntimeException e) {
            marcarFallida(notificacion.getId(), e);
            throw e;
        }

        String email = usuario != null ? usuario.getEmail() : null;
        if (email == null || email.isBlank()) {
            log.warn("Notificacion no entregable (sin email): mensajeId={}, destinatarioId={}",
                    entrante.getMensajeId(), notificacion.getDestinatarioId());
            marcarNoEntregable(notificacion.getId(), null);
            return;
        }

        String asunto;
        String cuerpo;
        try {
            asunto = plantillaEmailService.asunto(entrante, evento);
            cuerpo = plantillaEmailService.renderizar(entrante, evento, usuario);
        } catch (RuntimeException e) {
            marcarFallida(notificacion.getId(), e);
            log.error("Error permanente de plantilla: mensajeId={}, destinatarioId={}",
                    entrante.getMensajeId(), notificacion.getDestinatarioId(), e);
            return;
        }

        try {
            marcarEnviando(notificacion.getId());
        } catch (RuntimeException e) {
            marcarFallida(notificacion.getId(), e);
            throw e;
        }

        try {
            emailService.enviar(email, asunto, cuerpo);
        } catch (EmailConexionException e) {
            marcarPendiente(notificacion.getId(), e);
            log.error("Fallo de conexion SMTP: mensajeId={}, destinatarioId={}",
                    entrante.getMensajeId(), notificacion.getDestinatarioId(), e);
            throw e;
        } catch (EmailTimeoutException e) {
            guardarConEstado(notificacion.getId(), EstadoNotificacion.ENVIANDO, e);
            log.error("Timeout de SMTP: mensajeId={}, destinatarioId={}",
                    entrante.getMensajeId(), notificacion.getDestinatarioId(), e);
            return;
        } catch (EmailPermanenteException e) {
            if (e.getTipo() == EmailPermanenteException.Tipo.NO_ENTREGABLE) {
                marcarNoEntregable(notificacion.getId(), e);
            } else {
                marcarFallida(notificacion.getId(), e);
            }
            log.error("Error permanente de SMTP: mensajeId={}, destinatarioId={}",
                    entrante.getMensajeId(), notificacion.getDestinatarioId(), e);
            return;
        } catch (RuntimeException e) {
            marcarFallida(notificacion.getId(), e);
            throw e;
        }

        try {
            marcarEnviada(notificacion.getId());
        } catch (RuntimeException e) {
            guardarConEstado(notificacion.getId(), EstadoNotificacion.ENVIANDO, e);
            log.error("No se pudo registrar el envio: mensajeId={}, destinatarioId={}",
                    entrante.getMensajeId(), notificacion.getDestinatarioId(), e);
        }
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
        guardarConEstado(id, EstadoNotificacion.ENVIANDO, null);
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
        notificacion.setUltimoError(null);
        notificacionRepository.save(notificacion);
    }

    /**
     * Marca la notificacion como {@code FALLIDO} para permitir reintentos posteriores.
     *
     * @param id id de la notificacion
     */
    @Transactional
    public void marcarFallida(Long id) {
        marcarFallida(id, null);
    }

    @Transactional
    public void marcarFallida(Long id, Throwable causa) {
        guardarConEstado(id, EstadoNotificacion.FALLIDO, causa);
    }

    /**
     * Marca la notificacion como {@code NO_ENTREGABLE} cuando el usuario no tiene email.
     *
     * @param id id de la notificacion
     */
    @Transactional
    public void marcarNoEntregable(Long id) {
        marcarNoEntregable(id, null);
    }

    @Transactional
    public void marcarNoEntregable(Long id, Throwable causa) {
        guardarConEstado(id, EstadoNotificacion.NO_ENTREGABLE, causa);
    }

    @Transactional
    public void marcarPendientesComoFallidos(String mensajeId, Throwable causa) {
        String error = ResumidorErrores.resumir(causa);
        List<Notificacion> pendientes = notificacionRepository.findByMensajeIdOrderByIdAsc(mensajeId).stream()
                .filter(notificacion -> notificacion.getEstado() == EstadoNotificacion.PENDIENTE)
                .map(notificacion -> {
                    notificacion.setEstado(EstadoNotificacion.FALLIDO);
                    notificacion.setUltimoError(error);
                    return notificacion;
                })
                .toList();
        if (!pendientes.isEmpty()) {
            notificacionRepository.saveAllAndFlush(pendientes);
        }
    }

    private void marcarPendiente(Long id, Throwable causa) {
        guardarConEstado(id, EstadoNotificacion.PENDIENTE, causa);
    }

    private void guardarConEstado(Long id, EstadoNotificacion estado, Throwable causa) {
        Notificacion notificacion = obtenerPorId(id);
        notificacion.setEstado(estado);
        notificacion.setUltimoError(causa == null ? null : ResumidorErrores.resumir(causa));
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
