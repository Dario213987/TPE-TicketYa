package org.tudai.ticketya.eventos.domain;

import jakarta.persistence.*;
import lombok.*;
import java.util.HashSet;
import java.util.Set;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(name = "lugares")
@Entity
public class Lugar {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String nombre;
    private String tipo;
    private Integer capacidad;
    private String direccion;
    private String ciudad;
    @Embedded
    private Ubicacion ubicacion;
    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "lugar_id")
    @Builder.Default
    private Set<Sector> sectores = new HashSet<>();

    public void addSector(Sector sector) {
        this.sectores.add(sector);
    }

    public void removeSector(Sector sector) {
        this.sectores.remove(sector);
    }
}