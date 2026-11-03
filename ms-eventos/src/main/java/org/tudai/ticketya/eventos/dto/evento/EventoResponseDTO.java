package org.tudai.ticketya.eventos.dto.evento;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.tudai.ticketya.eventos.domain.EstadoEvento;
import org.tudai.ticketya.eventos.dto.lugar.LugarResponseDTO;
import org.tudai.ticketya.eventos.dto.participante.ParticipanteResponseDTO;
import org.tudai.ticketya.eventos.dto.sector.SectorResponseDTO;

import java.time.LocalDate;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EventoResponseDTO {

    private Long id;
    private String nombre;
    private String descripcion;
    private String tipo;
    private EstadoEvento estado;
    private Long organizadorId;
    private LugarResponseDTO lugar;
    private LocalDate fecha;
    private List<ParticipanteResponseDTO> participantes;
    private List<SectorResponseDTO> sectores;
}