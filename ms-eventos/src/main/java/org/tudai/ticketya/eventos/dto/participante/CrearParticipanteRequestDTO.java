package org.tudai.ticketya.eventos.dto.participante;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.tudai.ticketya.eventos.domain.TipoParticipante;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CrearParticipanteRequestDTO {

    @NotBlank(message = "El nombre es obligatorio")
    @Size(max = 150, message = "El nombre no puede superar los 150 caracteres")
    private String nombre;

    @NotNull(message = "El tipo de participante es obligatorio")
    private TipoParticipante tipo;
}
