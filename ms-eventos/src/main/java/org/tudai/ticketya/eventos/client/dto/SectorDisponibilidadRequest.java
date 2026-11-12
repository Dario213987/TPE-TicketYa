package org.tudai.ticketya.eventos.client.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SectorDisponibilidadRequest {
    private Long sectorId;
    private TipoSectorRequest tipo;
    private Integer capacidadMaxima;
    private List<AsientoRequest> asientos;
    private Integer filas;
    private Integer asientosPorFila;
    private Double precio;
}
