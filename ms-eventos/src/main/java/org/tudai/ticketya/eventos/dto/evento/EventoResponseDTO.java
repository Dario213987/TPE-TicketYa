package org.tudai.ticketya.eventos.dto.evento;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.tudai.ticketya.eventos.domain.EstadoEvento;
import org.tudai.ticketya.eventos.domain.TipoEvento;
import org.tudai.ticketya.eventos.dto.participante.ParticipanteResponseDTO;

import java.time.LocalDate;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EventoResponseDTO {

    private String nombre;
    private String descripcion;
    private TipoEvento tipo;
    private EstadoEvento estado;
    private Long organizadorId; // DEBERIA IR EL NOMBRE DEL ORGANIZADOR
    private String nombreLugar;
    private LocalDate fecha;
    private LocalDate hora;
    private int limiteEntradasPorCliente;
    private List<ParticipanteResponseDTO> participantes;
}
