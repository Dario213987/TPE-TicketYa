package org.tudai.ticketya.tickets.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.HashSet;
import java.util.Set;

@Getter
@Setter
@NoArgsConstructor
@Entity
@DiscriminatorValue("NUMERADO")
public class DisponibilidadNumerada extends Disponibilidad {

    @OneToMany(mappedBy = "disponibilidad", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private Set<DisponibilidadAsiento> asientos = new HashSet<>();

    public void addAsiento(DisponibilidadAsiento asiento) {
        asiento.setDisponibilidad(this);
        this.asientos.add(asiento);
    }

    public void removeAsiento(DisponibilidadAsiento asiento) {
        asiento.setDisponibilidad(null);
        this.asientos.remove(asiento);
    }
}
