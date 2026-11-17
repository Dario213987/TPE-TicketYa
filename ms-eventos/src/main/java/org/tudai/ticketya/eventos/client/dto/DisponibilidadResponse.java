package org.tudai.ticketya.eventos.client.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DisponibilidadResponse {
    private Long eventoId;
    private boolean yaExistia;
    private int sectoresProcesados;
    private int asientosInsertados;
    private int asientosOmitidos;
    private Instant creadoEl;
}
