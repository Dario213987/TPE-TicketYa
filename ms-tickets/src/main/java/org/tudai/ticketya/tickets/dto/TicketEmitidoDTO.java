package org.tudai.ticketya.tickets.dto;

import lombok.*;
import org.tudai.ticketya.tickets.domain.EstadoTicket;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TicketEmitidoDTO {

    private Long id;
    private String codigoQr;
    private EstadoTicket estado;
    private Long sectorId;
    private Integer fila;
    private Integer numero;
    private Double precio;
}
