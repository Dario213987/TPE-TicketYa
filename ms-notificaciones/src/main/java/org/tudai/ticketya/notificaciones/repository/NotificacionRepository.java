package org.tudai.ticketya.notificaciones.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.tudai.ticketya.notificaciones.domain.Notificacion;

import java.util.List;
import java.util.Optional;

/**
 * Repositorio de notificaciones persistidas.
 */
public interface NotificacionRepository extends JpaRepository<Notificacion, Long> {

    /**
     * Busca una notificacion por su clave de idempotencia completa.
     *
     * @param mensajeId     id del mensaje
     * @param destinatarioId id del destinatario
     * @return notificacion existente, si la hay
     */
    Optional<Notificacion> findByMensajeIdAndDestinatarioId(String mensajeId, long destinatarioId);

    /**
     * Lista las notificaciones generadas por un mensaje masivo.
     *
     * @param mensajeId id del mensaje
     * @return notificaciones del mensaje ordenadas por id
     */
    List<Notificacion> findByMensajeIdOrderByIdAsc(String mensajeId);
}
