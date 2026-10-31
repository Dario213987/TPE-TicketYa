package org.tudai.ticketya.eventos.dto.lugar;

import jakarta.validation.constraints.*;
import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LugarCreateDTO {

    @NotBlank(message = "El nombre es obligatorio")
    private String nombre;

    @NotNull(message = "El tipo de lugar es obligatorio")
    private String tipo;

    @NotBlank(message = "La dirección es obligatoria")
    private String direccion;

    @NotBlank(message = "La ciudad es obligatoria")
    private String ciudad;

    @NotNull(message = "La latitud es obligatoria")
    @Min(value = -90, message = "La latitud debe ser mayor o igual a -90")
    @Max(value = 90, message = "La latitud debe ser menor o igual a 90")
    private Double latitud;

    @NotNull(message = "La longitud es obligatoria")
    @Min(value = -180, message = "La longitud debe ser mayor o igual a -180")
    @Max(value = 180, message = "La longitud debe ser menor o igual a 180")
    private Double longitud;

    private Integer capacidad;
}
