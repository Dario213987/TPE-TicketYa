package org.tudai.ticketya.eventos.dto.participante;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ParticipanteResponseDTO {

    private Long id;
    private String nombre;

}
