package org.tudai.ticketya.tickets.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.tudai.ticketya.tickets.domain.EstadoTicket;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ValidacionQrResponseDTO {

    private boolean valido;

    private Long ticketId;

    private Long eventoId;

    private Long sectorId;

    private Long clienteId;

    private EstadoTicket estado;
}
