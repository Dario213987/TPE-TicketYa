package org.tudai.ticketya.eventos.client.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AsientoRequest {
    private Integer fila;
    private Integer numero;
}
