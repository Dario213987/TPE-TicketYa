package org.tudai.ticketya.eventos.domain;


import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.tudai.ticketya.persistence.Auditable;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "eventos")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder // Generar eventos para tests
public class Evento extends Auditable{

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 150)
    private String nombre;

    @Column(nullable = false, length = 2000)
    private String descripcion;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TipoEvento tipo;

    @Column(name = "organizador_id", nullable = false)
    private Long organizadorId; // Cuando exista el MS actualizo

    @Column(name = "lugar_id", nullable = false)
    private Long lugarId; // Cuando exista el MS actualizo

    @Column(name = "fecha_hora", nullable = false)
    private Instant fechaHora; // Guarda la fecha y hora del evento con zona horaria

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EstadoEvento estado;

    @Version
    @Column(nullable = false)
    private Long version;

    @ManyToMany
    @JoinTable(
            name = "evento_participante",
            joinColumns = @JoinColumn(name = "evento_id"),
            inverseJoinColumns = @JoinColumn(name = "participante_id")
    )
    @Builder.Default
    private Set<Participante> participantes = new HashSet<>();
}
