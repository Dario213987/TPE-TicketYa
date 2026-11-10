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
public class DisponibilidadEventoResponse {
    private Long eventoId;
    private List<SectorDisponibilidadResponse> sectores;
}
