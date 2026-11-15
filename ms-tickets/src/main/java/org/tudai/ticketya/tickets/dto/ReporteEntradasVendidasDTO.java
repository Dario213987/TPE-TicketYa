package org.tudai.ticketya.tickets.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReporteEntradasVendidasDTO {

    private Long eventoId;
    private String eventoNombre;
    private LocalDateTime eventoFecha;
    private String eventoEstado;
    private long entradasVendidas;
    private long entradasDevueltas;
    private List<ReporteSectorDTO> sectores;
}
