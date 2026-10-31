package org.tudai.ticketya.eventos.dto.lugar;

import lombok.*;
import org.tudai.ticketya.eventos.dto.sector.SectorResponseDTO;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LugarDetalleResponseDTO {
    private Long id;
    private String nombre;
    private String tipo;
    private String direccion;
    private String ciudad;
    private Double latitud;
    private Double longitud;
    private Integer capacidad;
    private List<SectorResponseDTO> sectores;
}
