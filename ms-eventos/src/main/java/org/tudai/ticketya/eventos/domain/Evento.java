package org.tudai.ticketya.eventos.domain;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
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
    @Builder.Default
    @Enumerated(EnumType.STRING)
    private EstadoEvento estado = EstadoEvento.BORRADOR;
    private String nombre;
	private String descripcion;
	@ManyToMany(cascade = CascadeType.PERSIST)
	@JoinTable(
			name = "evento_participante",
			joinColumns = @JoinColumn(name = "evento_id"),
			inverseJoinColumns = @JoinColumn(name = "participante_id")
	)
	@Builder.Default
	@Setter(AccessLevel.NONE)
	private Set<Participante> participantes = new HashSet<>();

	@ElementCollection
	@CollectionTable(
			name = "evento_sector_precios",
			joinColumns = @JoinColumn(name = "evento_id")
	)
	@MapKeyColumn(name = "sector_id")
	@Column(name = "precio", nullable = false)
	@Builder.Default
	private Map<Long, Double> preciosSectores = new HashMap<>();

	public void definirPrecioSector(Long sectorId, Double precio) {
		this.preciosSectores.put(sectorId, precio);
	}

	public void addParticipante(Participante part) {
		this.participantes.add(part);
	}

	public void removeParticipante(Participante part) {this.participantes.remove(part);
	}

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Evento other)) return false;
        return id != null && id.equals(other.getId());
    }

	@Override
	public int hashCode() {
		return getClass().hashCode();
	}

}