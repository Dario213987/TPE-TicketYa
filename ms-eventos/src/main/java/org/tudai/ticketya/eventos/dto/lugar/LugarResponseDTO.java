package org.tudai.ticketya.eventos.dto.lugar;

import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LugarResponseDTO {
    private Long id;
    private String nombre;
    private String tipo;
    private String direccion;
    private String ciudad;
    private Double latitud;
    private Double longitud;
    private Integer capacidad;
}
