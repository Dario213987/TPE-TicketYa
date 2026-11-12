package org.tudai.ticketya.tickets.domain;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;


@Entity
@Table(
        name = "tickets",
        uniqueConstraints = @UniqueConstraint(name = "uq_tickets_codigo_qr", columnNames = "codigo_qr"),
        indexes = @Index(
                name = "idx_tickets_evento_cliente_estado",
                columnList = "evento_id, cliente_id, estado_ticket")
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Ticket {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "codigo_qr", nullable = false)
    private String codigo_qr;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado_ticket", nullable = false, length = 20)
    @Builder.Default
    private EstadoTicket estadoTicket = EstadoTicket.EMITIDO;

    private Double precio;

    @Column(name = "fecha_emision", nullable = false, updatable = false)
    private Instant fechaEmision;

    @Column(name = "cliente_id")
    private Long clienteId;

    @Column(name = "expira_el")
    private Instant expiraEl;

    @Column(name = "pago_en_curso", nullable = false)
    @Builder.Default
    private boolean pagoEnCurso = false;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reserva_id")
    private Reserva reserva;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "venta_id")
    private Venta venta;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "evento_id", nullable = false)
    private DisponibilidadEvento evento;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sector_id", nullable = false)
    private DisponibilidadSector sector;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "asiento_id", unique = true)
    private DisponibilidadAsiento asiento;

    @PrePersist
    void prePersist() {
        if (this.fechaEmision == null) {
            this.fechaEmision = Instant.now();
        }
        if (this.estadoTicket == null) {
            this.estadoTicket = EstadoTicket.EMITIDO;
        }
    }
}
