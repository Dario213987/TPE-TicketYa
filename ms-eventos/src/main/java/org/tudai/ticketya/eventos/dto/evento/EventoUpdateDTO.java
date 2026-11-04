package org.tudai.ticketya.eventos.dto.evento;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.Set;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EventoUpdateDTO {

    @Size(max = 200, message = "El nombre del evento no puede tener más de 200 caracteres")
    private String nombre;

    @Size(max = 255, message = "La descripción del evento no puede tener más de 255 caracteres")
    private String descripcion;

    private String tipo;

    private LocalDate fecha;

    private Long lugarId;

    @JsonAlias("sectorIds")
    private Set<Long> sectores;
}
