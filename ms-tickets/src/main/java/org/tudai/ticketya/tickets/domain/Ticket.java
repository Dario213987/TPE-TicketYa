package org.tudai.ticketya.tickets.domain;

import jakarta.persistence.*;
import lombok.*;
import org.tudai.ticketya.persistence.Auditable;


@Entity
@Table(
        name = "tickets",
        uniqueConstraints = @UniqueConstraint(name = "uq_tickets_codigo_qr", columnNames = "codigo_qr")
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Ticket extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "codigo_qr", nullable = false)
    private String codigo_qr;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado_ticket", nullable = false)
    @Builder.Default
    private EstadoTicket estadoTicket = EstadoTicket.EMITIDO;

    private Double precio;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "evento_id", nullable = false)
    private DisponibilidadEvento evento;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sector_id", nullable = false)
    private Disponibilidad disponibilidad;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "asiento_id", unique = true)
    private DisponibilidadAsiento asiento;
}
