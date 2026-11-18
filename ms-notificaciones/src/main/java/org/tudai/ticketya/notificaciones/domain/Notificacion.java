package org.tudai.ticketya.notificaciones.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import org.tudai.ticketya.contract.notificaciones.messaging.MotivoNotificacion;

import java.time.LocalDateTime;

/**
 * Registro persistido de cada intento de notificacion.
 * <p>
 * La clave de idempotencia es la combinacion {@code (mensajeId, destinatarioId)}: un aviso
 * masivo puede generar una fila por comprador sin duplicar el envio a cada uno.
 */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "notificacion")
public class Notificacion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "mensaje_id", nullable = false, updatable = false)
    private String mensajeId;

    @Enumerated(EnumType.STRING)
    @Column(name = "motivo", nullable = false)
    private MotivoNotificacion motivo;

    @Column(name = "evento_id", nullable = false)
    private long eventoId;

    @Column(name = "destinatario_id", nullable = false)
    private long destinatarioId;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado", nullable = false)
    private EstadoNotificacion estado;

    @Column(name = "fecha_creacion", nullable = false)
    private LocalDateTime fechaCreacion;

    @Column(name = "fecha_envio")
    private LocalDateTime fechaEnvio;

    @Column(name = "ultimo_error", length = 1000)
    private String ultimoError;
}
