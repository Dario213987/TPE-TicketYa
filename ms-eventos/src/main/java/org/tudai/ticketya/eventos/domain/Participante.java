package org.tudai.ticketya.eventos.domain;

import jakarta.persistence.*;
import lombok.*;

import java.util.HashSet;
import java.util.Set;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString
@Table(name="participantes")
@Entity
public class Participante {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String nombre;
    @ManyToMany(mappedBy = "participantes")
    @Builder.Default
    private Set<Evento> eventos = new HashSet<>();

    public void addEvento(Evento evento) {
        this.eventos.add(evento);
    }

    public void removeEvento(Evento evento) {
        this.eventos.remove(evento);
    }
}