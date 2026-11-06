package org.tudai.ticketya.tickets.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "disponibilidad_eventos")
public class DisponibilidadEvento {

    @Id
    @Column(name = "evento_id", nullable = false, updatable = false)
    private Long eventoId;

    @Column(name = "creado_el", nullable = false, updatable = false)
    private Instant creadoEl;

    @OneToMany(mappedBy = "evento", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private Set<DisponibilidadSector> sectores = new HashSet<>();

    public void addSector(DisponibilidadSector sector) {
        sector.setEvento(this);
        this.sectores.add(sector);
    }

    public void removeSector(DisponibilidadSector sector) {
        sector.setEvento(null);
        this.sectores.remove(sector);
    }

    @PrePersist
    void prePersist() {
        if (this.creadoEl == null) {
            this.creadoEl = Instant.now();
        }
    }
}
