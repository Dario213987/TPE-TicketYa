package org.tudai.ticketya.eventos.domain;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

@Entity
@DiscriminatorValue("NO_NUMERADO") // Valor que tomará el discriminador en la BD
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class SectorNoNumerado extends Sector {

    private Integer capacidadMaxima;
}