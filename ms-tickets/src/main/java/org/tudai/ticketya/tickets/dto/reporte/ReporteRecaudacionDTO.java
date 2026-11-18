package org.tudai.ticketya.tickets.dto.reporte;

import lombok.*;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReporteRecaudacionDTO {
    private Long eventoId;
    private String eventoNombre;
    private LocalDateTime eventoFecha;
    private String eventoEstado;
    private double ventas;
    private double reintegros;
    private double recaudacionTotal;
    private List<ReporteRecaudacionSectorDTO> sectores;
}
