package org.tudai.ticketya.tickets.dto;

import lombok.*;
import org.tudai.ticketya.tickets.domain.TipoSector;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SectorDisponibilidadResponseDTO {
    private Long sectorId;
    private Integer capacidadTotal;
    private Integer disponibles;
    private TipoSector tipoSector;
    private List<AsientoDisponibilidadDTO> asientos;
}
