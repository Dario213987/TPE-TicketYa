package org.tudai.ticketya.tickets.dto.reporte;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReporteRecaudacionSectorDTO {
    private Long sectorId;
    private String sectorNombre;
    private double ventas;
    private double reintegros;
    private double recaudacion;
}
