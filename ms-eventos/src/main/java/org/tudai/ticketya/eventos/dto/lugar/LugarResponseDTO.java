package org.tudai.ticketya.eventos.dto.lugar;

import lombok.*;
import org.tudai.ticketya.eventos.domain.TipoLugar;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LugarResponseDTO {
    private Long id;
    private String nombre;
    private TipoLugar tipo;
    private String direccion;
    private String ciudad;
    private Double latitud;
    private Double longitud;
    private Integer capacidad;
}
