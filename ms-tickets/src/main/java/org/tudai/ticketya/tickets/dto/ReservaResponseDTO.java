package org.tudai.ticketya.tickets.dto;

import lombok.*;

import java.time.Instant;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReservaResponseDTO {

    private Long idReserva;
    private Long eventoId;
    private Long clienteId;
    private Long sectorId;
    private int cantidad;
    private int limiteEntradasPorCliente;
    private long entradasVigentes;
    private Instant expiraEl;
    private List<String> codigosQr;
    private List<AsientoDisponibilidadDTO> asientos;

    public Long getReservaId() {
        return idReserva;
    }
}
