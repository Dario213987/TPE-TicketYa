package org.tudai.ticketya.tickets.dto;

import lombok.*;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DisponibilidadEventoResponseDTO {
    private Long eventoId;
    private List<SectorDisponibilidadResponseDTO> sectores;
}
