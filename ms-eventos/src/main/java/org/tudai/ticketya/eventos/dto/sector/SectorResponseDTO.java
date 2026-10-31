package org.tudai.ticketya.eventos.dto.sector;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class SectorResponseDTO {
    private Long id;
    private String nombre;
    private Double precio;
    private String tipoSector;
    private Integer capacidad;
    private Integer capacidadMaxima;
    private Integer filas;
    private Integer asientosPorFila;
}