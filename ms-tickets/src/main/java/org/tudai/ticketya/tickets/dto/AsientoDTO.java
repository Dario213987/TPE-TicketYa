package org.tudai.ticketya.tickets.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AsientoDTO {

    @NotNull(message = "La fila es obligatoria")
    @Min(value = 1, message = "La fila debe ser mayor a 0")
    private Integer fila;

    @NotNull(message = "El numero de asiento es obligatorio")
    @Min(value = 1, message = "El numero de asiento debe ser mayor a 0")
    private Integer numero;
}
