package org.tudai.ticketya.tickets.domain;

import jakarta.persistence.*;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(
        name = "disponibilidad_asientos",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_disponibilidad_asiento",
                columnNames = {"evento_id", "sector_id", "fila", "numero"}
        ),
        indexes = {
                @Index(name = "idx_disponibilidad_asiento_evento_estado", columnList = "evento_id, estado"),
                @Index(name = "idx_disponibilidad_asiento_sector_estado", columnList = "sector_id, estado")
        }
)
public class DisponibilidadAsiento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "evento_id", nullable = false)
    private DisponibilidadEvento evento;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sector_id", nullable = false)
    private DisponibilidadSector sector;

    @Column(name = "fila", nullable = false)
    private Integer fila;

    @Column(name = "numero", nullable = false)
    private Integer numero;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado", nullable = false)
    @Builder.Default
    private EstadoDisponibilidad estado = EstadoDisponibilidad.LIBRE;
}
