package org.tudai.ticketya.tickets.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReservaRequestDTO {

    @NotNull(message = "El id del sector es obligatorio")
    private Long sectorId;

    @Min(value = 1, message = "La cantidad de entradas debe ser mayor a 0")
    private Integer cantidad;

    @Valid
    private List<AsientoDTO> asientos;

    /**
     * Opcional. Si el evento usa habilitacion por tandas, el cliente puede
     * enviar el token del pase obtenido en la fila. La autorizacion principal
     * sigue siendo el X-User-Id.
     */
    private String paseToken;

    public boolean tieneAsientosEspecificos() {
        return asientos != null && !asientos.isEmpty();
    }
}
