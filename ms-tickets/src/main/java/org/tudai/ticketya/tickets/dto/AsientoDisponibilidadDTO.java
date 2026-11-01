package org.tudai.ticketya.tickets.dto;

import lombok.*;
import org.tudai.ticketya.tickets.domain.EstadoDisponibilidad;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder

public class AsientoDisponibilidadDTO {
    private Integer fila;
    private Integer numero;
    private EstadoDisponibilidad estado;
}
