package org.tudai.ticketya.tickets.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PublicarEventoRequestDTO {

    @NotNull(message = "El id del evento es obligatorio")
    private Long eventoId;

    @Min(value = 1, message = "El limite de entradas por cliente debe ser mayor a 0")
    private Integer limiteEntradasPorCliente;

    private Long organizadorId;

    @NotEmpty(message = "El evento debe tener al menos un sector")
    @Valid
    private List<SectorDisponibilidadDTO> sectores;
}
