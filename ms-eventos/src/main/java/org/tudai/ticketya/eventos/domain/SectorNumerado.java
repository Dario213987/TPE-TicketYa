package org.tudai.ticketya.eventos.domain;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;
import java.util.HashSet;
import java.util.Set;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@Entity
@DiscriminatorValue(TipoSector.NUMERADO)
public class SectorNumerado extends Sector {

    @ElementCollection
    @CollectionTable(
            name = "sector_asientos",
            joinColumns = @JoinColumn(name = "sector_id"),
            uniqueConstraints = @UniqueConstraint(columnNames = {"sector_id", "fila", "numero"}))
    @Builder.Default
    @Setter(AccessLevel.NONE)
    private Set<Asiento> asientos = new HashSet<>();

    public void generarAsientos(Integer filas, Integer asientosPorFila) {
        for (int fila = 1; fila <= filas; fila++) {
            for (int numero = 1; numero <= asientosPorFila; numero++) {
                this.addAsiento(Asiento.builder().fila(fila).numero(numero).build());
            }
        }
    }

    public void addAsiento(Asiento asiento) {
        this.asientos.add(asiento);
    }

    public void removeEvento(Asiento asiento) {
        this.asientos.remove(asiento);
    }
}