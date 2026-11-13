package org.tudai.ticketya.tickets.dto;

import lombok.*;

import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TurnoFilaResponseDTO {

    private Long eventoId;
    private Long clienteId;
    private long posicion;
    private long totalEnFila;
    private boolean yaEstabaEnFila;
    private Instant ingresadoEl;
}
