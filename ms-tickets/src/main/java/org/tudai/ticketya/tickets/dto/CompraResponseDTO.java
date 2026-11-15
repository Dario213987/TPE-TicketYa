package org.tudai.ticketya.tickets.dto;

import lombok.*;
import org.tudai.ticketya.tickets.domain.EstadoPago;

import java.time.Instant;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CompraResponseDTO {

    private Long reservaId;
    private Long ventaId;
    private Long eventoId;
    private Long clienteId;
    private String medioPago;
    private EstadoPago estadoPago;
    private Double total;
    private Instant pagadoEl;
    private List<TicketEmitidoDTO> tickets;
}
