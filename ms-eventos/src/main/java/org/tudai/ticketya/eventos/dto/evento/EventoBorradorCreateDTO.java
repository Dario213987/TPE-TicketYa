package org.tudai.ticketya.eventos.dto.evento;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.tudai.ticketya.eventos.dto.participante.ParticipanteRequestDTO;

import java.time.LocalDateTime;
import java.util.Set;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EventoBorradorCreateDTO {

    @NotBlank(message = "El nombre del evento es obligatorio")
    @Size(max = 200, message = "El nombre del evento no puede tener más de 200 caracteres")
    private String nombre;

    @NotBlank(message = "La descripción del evento es obligatoria")
    @Size(max = 500, message = "La descripción del evento no puede tener más de 500 caracteres")
    private String descripcion;

    @NotNull(message = "El tipo del evento es obligatorio")
    private String tipo;

    @NotNull(message = "La fecha del evento es obligatoria")
    @Future(message = "La fecha del evento debe ser una fecha futura")
    private LocalDateTime fechaHora;

    @NotNull(message = "El lugar del evento es obligatorio")
    private Long lugarId;

    @NotEmpty(message = "Los participantes son obligatorios")
    @Valid
    private Set<ParticipanteRequestDTO> participantes;

    @NotNull(message = "Debe indicar si es alta demanda")
    private Boolean altaDemanda;
}

