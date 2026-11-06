package org.tudai.ticketya.tickets.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.*;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PublicarEventoRequestDTO {

    @NotEmpty(message = "El evento debe tener al menos un sector")
    @Valid
    private List<SectorDisponibilidadDTO> sectores;
}
