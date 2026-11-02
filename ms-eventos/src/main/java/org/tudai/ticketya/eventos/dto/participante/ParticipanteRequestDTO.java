package org.tudai.ticketya.eventos.dto.participante;


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

    // Se usa si el participante ya existe en la base de datos
    private Long id;

    // Se usa si es un participante nuevo que hay que crear
    private String nombre;
}
