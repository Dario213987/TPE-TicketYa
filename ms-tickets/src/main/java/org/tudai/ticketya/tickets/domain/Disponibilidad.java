package org.tudai.ticketya.tickets.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.tudai.ticketya.persistence.Auditable;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(
        name = "disponibilidad_sectores",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_disponibilidad_sector",
                columnNames = {"evento_id", "sector_id_externo"}
        ),
        indexes = @Index(name = "idx_disponibilidad_sector_evento", columnList = "evento_id")
)
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@DiscriminatorColumn(name = "tipo", discriminatorType = DiscriminatorType.STRING)
public abstract class Disponibilidad extends Auditable {

    @Id
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "evento_id", nullable = false)
    private DisponibilidadEvento evento;

    @Column(name = "sector_id_externo", nullable = false)
    private Long sectorIdExterno;
}
