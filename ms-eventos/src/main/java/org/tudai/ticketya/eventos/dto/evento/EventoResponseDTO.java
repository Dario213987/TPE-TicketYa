package org.tudai.ticketya.eventos.dto.evento;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
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
    private String estado;
    private Long organizadorId;
    private Long lugarId;
    private LocalDate fecha;
    private Boolean altaDemanda;
    private Integer limiteEntradasPorCliente;
    private List<ParticipanteResponseDTO> participantes;
    private List<SectorResponseDTO> sectores;
}