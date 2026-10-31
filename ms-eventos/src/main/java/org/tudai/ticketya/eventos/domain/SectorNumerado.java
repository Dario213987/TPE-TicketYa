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
    @CollectionTable(name = "sector_asientos", joinColumns = @JoinColumn(name = "sector_id"))
    @Builder.Default
    private Set<Asiento> asientos = new HashSet<>();

    public void addAsiento(Asiento asiento) {
        this.asientos.add(asiento);
    }

    public void removeEvento(Asiento asiento) {
        this.asientos.remove(asiento);
    }
}