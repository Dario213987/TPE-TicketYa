package org.tudai.ticketya.devoluciones.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.tudai.ticketya.persistence.Auditable;

import java.time.Instant;

/**
 * Estado durable de un reintegro masivo. La fila se crea cuando se cancela el evento y
 * el job la va avanzando por lotes; si se interrumpe, el cursor permite retomar el trabajo.
 */
@Entity
@Table(name = "reintegros_masivos")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReintegroMasivo extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "evento_id", nullable = false, updatable = false, unique = true)
    private Long eventoId;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado", nullable = false, length = 20)
    private EstadoReintegroMasivo estado;

    @Column(name = "ultimo_ticket_id")
    private Long ultimoTicketId;

    @Builder.Default
    @Column(name = "procesados", nullable = false)
    private long procesados = 0;

    @Builder.Default
    @Column(name = "fallos", nullable = false)
    private int fallos = 0;

    @Builder.Default
    @Column(name = "intentos", nullable = false)
    private int intentos = 0;

    @Builder.Default
    @Column(name = "reservas_liberadas", nullable = false)
    private boolean reservasLiberadas = false;

    @Builder.Default
    @Column(name = "notificado", nullable = false)
    private boolean notificado = false;

    @Column(name = "reclamado_el")
    private Instant reclamadoEl;
}
