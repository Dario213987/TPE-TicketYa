package org.tudai.ticketya.eventos.dto.sector;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SectorNumeradoRequestDTO {
    @NotBlank(message = "El nombre es obligatorio")
    private String nombre;

    @NotNull(message = "El precio es obligatorio")
    @Min(value = 0, message = "El precio no puede ser negativo")
    private Double precio;

    @NotNull(message = "La cantidad de filas es obligatoria")
    @Min(value = 1, message = "Debe tener al menos 1 fila")
    private Integer filas;

    @NotNull(message = "La cantidad de asientos por fila es obligatoria")
    @Min(value = 1, message = "Debe tener al menos 1 asiento por fila")
    private Integer asientosPorFila;
}