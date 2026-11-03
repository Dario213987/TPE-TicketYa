package org.tudai.ticketya.tickets.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@DiscriminatorValue("NO_NUMERADO")
public class DisponibilidadNoNumerada extends Disponibilidad {

    @Column(name = "capacidad_total", nullable = false)
    private Integer capacidadTotal;

    public boolean puedeReservar(long reservasActivas) {
        return capacidadTotal != null && reservasActivas < capacidadTotal;
    }
}
