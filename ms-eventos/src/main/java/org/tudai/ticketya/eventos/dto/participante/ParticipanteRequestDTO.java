package org.tudai.ticketya.eventos.dto.participante;

import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ParticipanteRequestDTO {
    @Positive(message = "El id del participante es inválido")
    private Long id;

    @Size(max = 200, message = "El nombre del participante no puede superar los 200 caracteres")
    private String nombre;


}
