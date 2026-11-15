package org.tudai.ticketya.tickets.dto;

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
public class ReporteSectorDTO {

    private Long sectorId;
    private String sectorNombre;
    private long entradasVendidas;
    private long entradasDevueltas;
}
