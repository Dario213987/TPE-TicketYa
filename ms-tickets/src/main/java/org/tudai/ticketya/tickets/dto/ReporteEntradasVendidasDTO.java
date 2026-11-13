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
public class ReporteEntradasVendidasDTO {

    private Long eventoId;
    private long entradasVendidas;
    private Long solicitanteId;
    private boolean esAdministrador;
}
