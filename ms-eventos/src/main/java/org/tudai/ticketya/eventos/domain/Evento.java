package org.tudai.ticketya.eventos.domain;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@EqualsAndHashCode
@ToString
@Table(name="eventos")
@Entity
public class Evento {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

	private String nombre;
	private String descripcion;
	private TipoEvento tipo;
	private Long organizadorId;

	@ManyToOne
	@JoinColumn(name = "lugar_id")
	private Lugar lugar;

	private LocalDate fecha;
	private LocalDate hora;
	private EstadoEvento estado;
	private int limiteEntradasPorCliente;

	@ManyToMany
	@JoinTable(
			name = "evento_participante",
			joinColumns = @JoinColumn(name = "evento_id"),
			inverseJoinColumns = @JoinColumn(name = "participante_id")
	)
	@Builder.Default
	private Set<Participante> participantes = new HashSet<>();

}