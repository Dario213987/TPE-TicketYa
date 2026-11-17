package org.tudai.ticketya.notificaciones.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.tudai.ticketya.notificaciones.domain.Notificacion;

import java.util.Optional;

public interface NotificacionRepository extends JpaRepository<Notificacion, Long> {
    Optional<Notificacion> findByMensajeId(String mensajeId);
}
