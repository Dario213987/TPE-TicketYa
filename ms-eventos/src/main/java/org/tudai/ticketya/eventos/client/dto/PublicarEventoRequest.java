package org.tudai.ticketya.eventos.client.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PublicarEventoRequest {
    private Long eventoId;
    private Long organizadorId;
    private Boolean altaDemanda;
    private List<SectorDisponibilidadRequest> sectores;
}
