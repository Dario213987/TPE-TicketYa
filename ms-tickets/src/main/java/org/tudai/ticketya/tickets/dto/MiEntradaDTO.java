package org.tudai.ticketya.tickets.dto;

import lombok.*;
import org.tudai.ticketya.tickets.domain.EstadoTicket;

import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MiEntradaDTO {

    private Long id;
    private Long eventoId;
    private Long sectorId;
    private Integer fila;
    private Integer numero;
    private EstadoTicket estado;
    private boolean utilizado;
    private Double precio;
    private Instant fechaEmision;
}
