package org.tudai.ticketya.tickets.client.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EventoResponseDto {

    private Long id;
    private String nombre;
    private String estado;
    private Long organizadorId;
}
