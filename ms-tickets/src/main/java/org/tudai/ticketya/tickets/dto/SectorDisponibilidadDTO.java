package org.tudai.ticketya.tickets.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import org.tudai.ticketya.tickets.domain.TipoSector;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SectorDisponibilidadDTO {

    @NotNull(message = "El id del sector es obligatorio")
    private Long sectorId;

    @NotNull(message = "El tipo de sector es obligatorio")
    private TipoSector tipo;

    @Min(value = 1, message = "La capacidad maxima debe ser mayor a 0")
    private Integer capacidadMaxima;

    @Valid
    private List<AsientoDTO> asientos;

    @Min(value = 1, message = "La cantidad de filas debe ser mayor a 0")
    private Integer filas;

    @Min(value = 1, message = "La cantidad de asientos por fila debe ser mayor a 0")
    private Integer asientosPorFila;

    @AssertTrue(message = "La configuracion del sector no es valida")
    public boolean isConfiguracionValida() {
        if (this.tipo == null) {
            return false;
        }
        if (this.tipo == TipoSector.NO_NUMERADO) {
            return this.capacidadMaxima != null && this.capacidadMaxima > 0;
        }
        boolean porLista = this.asientos != null && !this.asientos.isEmpty();
        boolean porDimensiones = this.filas != null && this.filas > 0
                && this.asientosPorFila != null && this.asientosPorFila > 0;
        return porLista || porDimensiones;
    }
}
