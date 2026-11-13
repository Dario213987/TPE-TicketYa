package org.tudai.ticketya.tickets.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(
        name = "reservas",
        indexes = @Index(name = "idx_reservas_evento_cliente", columnList = "evento_id, cliente_id")
)
public class Reserva {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "evento_id", nullable = false, updatable = false)
    private Long eventoId;

    @Column(name = "cliente_id", nullable = false, updatable = false)
    private Long clienteId;

    @Column(name = "sector_id_externo", nullable = false, updatable = false)
    private Long sectorIdExterno;

    @Column(name = "expira_el", nullable = false, updatable = false)
    private Instant expiraEl;

    @Column(name = "creado_el", nullable = false, updatable = false)
    private Instant creadoEl;

    @Column(name = "idempotency_key", length = 100)
    private String idempotencyKey;

    @Column(name = "medio_pago", length = 30)
    private String medioPago;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado_pago", length = 20)
    private EstadoPago estadoPago;

    @Column(name = "motivo_rechazo")
    private String motivoRechazo;

    @Column(name = "pagado_el")
    private Instant pagadoEl;

    @Column(name = "pago_en_curso", nullable = false)
    @Builder.Default
    private boolean pagoEnCurso = false;

    @PrePersist
    void prePersist() {
        if (this.creadoEl == null) {
            this.creadoEl = Instant.now();
        }
    }
}
