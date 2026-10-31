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
@DiscriminatorValue("NUMERADO")
public class SectorNumerado extends Sector {

    @ElementCollection
    @CollectionTable(name = "sector_asientos", joinColumns = @JoinColumn(name = "sector_id"))
    @Builder.Default
    private Set<Asiento> asientos = new HashSet<>();
}
