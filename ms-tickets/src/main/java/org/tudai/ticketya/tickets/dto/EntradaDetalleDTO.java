package org.tudai.ticketya.tickets.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class EntradaDetalleDTO {
    private Long id;
    private Long eventoId;
    private Double precio;
    private Long usuarioId;
    private Long clienteId;
    private Long transaccionId;
    private Long idAsiento;
    private Long idSector;
    private String estado;
    private Boolean utilizado;
    private LocalDateTime fechaEvento;
}
