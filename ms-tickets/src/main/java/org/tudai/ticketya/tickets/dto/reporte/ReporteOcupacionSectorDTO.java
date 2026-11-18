package org.tudai.ticketya.tickets.dto.reporte;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReporteOcupacionSectorDTO {
    private Long sectorId;
    private String sectorNombre;
    private double ocupacion;
}
