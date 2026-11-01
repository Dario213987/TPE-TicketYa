package org.tudai.ticketya.eventos.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.tudai.ticketya.eventos.domain.EstadoEvento;
import org.tudai.ticketya.eventos.domain.TipoEvento;

import java.time.Instant;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EventoResponse {

    private Long id;
    private String nombre;
    private String descripcion;
    private TipoEvento tipo;
    private EstadoEvento estado;
    private Long organizadorId;
    private Long lugarId;
    private Instant fechaHora;
    private Integer limiteEntradasPorCliente;
    private List<ParticipanteResponse> participantes;
}
