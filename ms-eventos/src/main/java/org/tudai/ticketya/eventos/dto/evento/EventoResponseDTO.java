package org.tudai.ticketya.eventos.dto.evento;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.tudai.ticketya.eventos.domain.EstadoEvento;
import org.tudai.ticketya.eventos.dto.lugar.LugarResponseDTO;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EventoResponseDTO {

    private Long id;
    private String nombre;
    private String descripcion;
    private String tipo;
    private LocalDate fecha;
    private EstadoEvento estado;
    private Long organizadorId;
    private LugarResponseDTO lugar;
    private List<String> artistas;
    private Map<Long, Double> preciosSectores;
}
