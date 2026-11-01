package org.tudai.ticketya.eventos.dto.sector;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SectorNoNumeradoRequestDTO {
    @NotBlank(message = "El nombre es obligatorio")
    private String nombre;

    @NotNull(message = "El precio es obligatorio")
    @Min(value = 0, message = "El precio no puede ser negativo")
    private Double precio;

    @NotNull(message = "La capacidad máxima es obligatoria")
    @Min(value = 1, message = "La capacidad máxima debe ser mayor a 0")
    private Integer capacidadMaxima;
}