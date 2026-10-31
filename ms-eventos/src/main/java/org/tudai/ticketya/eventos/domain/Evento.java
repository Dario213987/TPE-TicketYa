package org.tudai.ticketya.eventos.domain;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Table(name="eventos")
@Entity
public class Evento {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String tipo;
	private LocalDate fecha;
	@ManyToOne
	@JoinColumn(name = "lugar_id")
	private Lugar lugar;
	private Long organizadorId;
	private EstadoEvento estado;
	private String nombre;
	private String descripcion;
	@ManyToMany
	@JoinTable(
			name = "evento_participante",
			joinColumns = @JoinColumn(name = "evento_id"),
			inverseJoinColumns = @JoinColumn(name = "participante_id")
	)
	@Builder.Default
	@Setter(AccessLevel.NONE)
	private Set<Participante> artistas = new HashSet<>();

	public void addArtista(Participante part) {
		this.artistas.add(part);
	}

	public void removeArtista(Participante part) {this.artistas.remove(part);
	}

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