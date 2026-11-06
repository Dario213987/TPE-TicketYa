package org.tudai.ticketya.eventos.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.tudai.ticketya.eventos.domain.EstadoNotificacion;
import org.tudai.ticketya.eventos.domain.EventoNotificacion;
import org.tudai.ticketya.eventos.domain.TipoNotificacionTickets;

import java.time.Instant;
import java.util.List;

@Repository
public interface EventoNotificacionRepository extends JpaRepository<EventoNotificacion, Long> {

    boolean existsByEventoIdAndTipo(Long eventoId, TipoNotificacionTickets tipo);

    List<EventoNotificacion> findByEstadoAndProximoIntentoLessThanEqualOrderByCreadoElAsc(
            EstadoNotificacion estado, Instant ahora, Pageable pageable);
}
