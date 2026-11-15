package org.tudai.ticketya.tickets.dto;

import lombok.*;
import org.tudai.ticketya.tickets.domain.EstadoFila;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PosicionFilaResponseDTO {

    private Long eventoId;
    private Long clienteId;
    private Long posicion;
    private Long personasDelante;
    private Long totalEnFila;
    private EstadoFila estado;
    private PaseDTO pase;
}
