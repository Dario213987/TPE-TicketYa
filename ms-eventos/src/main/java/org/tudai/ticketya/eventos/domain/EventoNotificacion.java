package org.tudai.ticketya.eventos.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * Notificación pendiente de enviar a ms-tickets.
 * <p>
 * Se guarda en la misma transacción que el cambio de estado del evento para que
 * el aviso no se pierda si ms-tickets no está disponible. Un relay aparte la
 * envía y reintenta con backoff.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(
        name = "evento_notificacion",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_evento_notificacion_evento_tipo",
                columnNames = {"evento_id", "tipo"}))
public class EventoNotificacion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "evento_id", nullable = false)
    private Long eventoId;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo", nullable = false, length = 32)
    private TipoNotificacionTickets tipo;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "estado", nullable = false, length = 16)
    private EstadoNotificacion estado = EstadoNotificacion.PENDIENTE;

    @Builder.Default
    @Column(name = "intentos", nullable = false)
    private int intentos = 0;

    @Column(name = "proximo_intento")
    private Instant proximoIntento;

    @Column(name = "creado_el", nullable = false)
    private Instant creadoEl;

    @Column(name = "actualizado_el")
    private Instant actualizadoEl;

    @PrePersist
    void prePersist() {
        if (creadoEl == null) {
            creadoEl = Instant.now();
        }
        if (estado == null) {
            estado = EstadoNotificacion.PENDIENTE;
        }
    }

    @PreUpdate
    void preUpdate() {
        actualizadoEl = Instant.now();
    }
}
