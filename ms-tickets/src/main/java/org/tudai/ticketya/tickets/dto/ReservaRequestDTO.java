package org.tudai.ticketya.tickets.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReservaRequestDTO {

    @NotNull(message = "El id del sector es obligatorio")
    private Long sectorId;

    @NotNull(message = "La cantidad de entradas es obligatoria")
    @Min(value = 1, message = "La cantidad de entradas debe ser mayor a 0")
    private Integer cantidad;
}
