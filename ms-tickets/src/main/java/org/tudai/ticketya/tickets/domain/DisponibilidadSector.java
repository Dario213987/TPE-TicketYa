package org.tudai.ticketya.tickets.domain;

import jakarta.persistence.*;
import lombok.*;

import java.util.HashSet;
import java.util.Set;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(
        name = "disponibilidad_sectores",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_disponibilidad_sector",
                columnNames = {"evento_id", "sector_id_externo"}
        ),
        indexes = @Index(name = "idx_disponibilidad_sector_evento", columnList = "evento_id")
)
public class DisponibilidadSector {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "evento_id", nullable = false)
    private DisponibilidadEvento evento;

    @Column(name = "sector_id_externo", nullable = false)
    private Long sectorIdExterno;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo", nullable = false)
    private TipoSector tipo;

    @Column(name = "capacidad_total", nullable = false)
    private Integer capacidadTotal;

    @Column(name = "disponibles", nullable = false)
    private Integer disponibles;

    @OneToMany(mappedBy = "sector", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private Set<DisponibilidadAsiento> asientos = new HashSet<>();

    public void addAsiento(DisponibilidadAsiento asiento) {
        asiento.setSector(this);
        this.asientos.add(asiento);
    }

    public void removeAsiento(DisponibilidadAsiento asiento) {
        asiento.setSector(null);
        this.asientos.remove(asiento);
    }
}
