package org.tudai.ticketya.tickets.client.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EventoResponseDto {

    private Long id;
    private String nombre;
    private LocalDateTime fecha;
    private String estado;
    private Long organizadorId;
    private List<SectorResponseDto> sectores;
}
