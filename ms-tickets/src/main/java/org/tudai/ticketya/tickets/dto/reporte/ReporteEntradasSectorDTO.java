package org.tudai.ticketya.tickets.dto.reporte;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReporteEntradasSectorDTO {
    private Long sectorId;
    private String sectorNombre;
    private long entradasVendidas;
    private long entradasDevueltas;
}
