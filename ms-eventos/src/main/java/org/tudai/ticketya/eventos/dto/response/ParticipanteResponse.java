package org.tudai.ticketya.eventos.dto.response;

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
public class ParticipanteResponse {

    private Long id;
    private String nombre;
    private TipoParticipante tipo;
}
