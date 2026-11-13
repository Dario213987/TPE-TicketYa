package org.tudai.ticketya.devoluciones.client;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class TicketDTO {
    private Long id;
    private Long eventoId;
    private Double precio;
    private Long usuarioId;
    private Long clienteId;
    private Long idAsiento;
    private Long idSector;
    private String estado;
    private Boolean utilizado;
    private LocalDateTime fechaEvento;

    public Long getUsuarioId() { return usuarioId != null ? usuarioId : clienteId; }
}
