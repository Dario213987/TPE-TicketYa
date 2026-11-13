package org.tudai.ticketya.tickets.dto;

import lombok.*;
import org.tudai.ticketya.tickets.domain.EstadoTicket;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ResultadoAccesoDTO {

    private String resultado;
    private boolean valido;
    private String codigoQr;
    private Long eventoId;
    private EstadoTicket estadoTicket;
}
