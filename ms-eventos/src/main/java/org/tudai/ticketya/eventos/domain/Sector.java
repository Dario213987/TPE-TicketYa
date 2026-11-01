package org.tudai.ticketya.eventos.domain;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@DiscriminatorColumn(name = "tipo_sector", discriminatorType = DiscriminatorType.STRING)
@Table(name = "sectores")
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@Entity
public abstract class Sector {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String nombre;
    private Double precio;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Participante other)) return false;
        return id != null && id.equals(other.getId());
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}