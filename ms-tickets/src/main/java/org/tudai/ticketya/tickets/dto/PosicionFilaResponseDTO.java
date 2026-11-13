package org.tudai.ticketya.tickets.dto;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PosicionFilaResponseDTO {

    private Long eventoId;
    private Long clienteId;
    private long posicion;
    private long personasDelante;
    private long totalEnFila;
}
