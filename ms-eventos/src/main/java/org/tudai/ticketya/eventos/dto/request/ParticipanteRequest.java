package org.tudai.ticketya.eventos.dto.request;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;
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
public class ParticipanteRequest {
    @Positive(message = "El id del participante es inválido")
    private Long id;

    @Size(max = 200, message = "El nombre del participante no puede superar los 200 caracteres")
    private String nombre;
    

}
