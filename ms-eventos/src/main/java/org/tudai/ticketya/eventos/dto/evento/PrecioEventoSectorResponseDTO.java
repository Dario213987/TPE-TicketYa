package org.tudai.ticketya.eventos.dto.evento;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PrecioEventoSectorResponseDTO {

    private Long eventoId;
    private Long sectorId;
    private Double precio;
}
