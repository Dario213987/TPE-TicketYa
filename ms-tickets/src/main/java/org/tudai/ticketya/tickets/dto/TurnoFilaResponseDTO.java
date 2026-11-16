package org.tudai.ticketya.tickets.dto;

import lombok.*;
import org.tudai.ticketya.tickets.domain.EstadoFila;

import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TurnoFilaResponseDTO {

    private Long eventoId;
    private Long clienteId;
    private Long posicion;
    private Long totalEnFila;
    private boolean yaEstabaEnFila;
    private Instant ingresadoEl;
    private EstadoFila estado;
    private PaseDTO pase;
}
