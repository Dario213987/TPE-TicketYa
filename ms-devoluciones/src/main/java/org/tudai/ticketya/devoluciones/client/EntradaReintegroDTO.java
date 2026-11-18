package org.tudai.ticketya.devoluciones.client;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class EntradaReintegroDTO {

    private Long id;
    private Long clienteId;
    private Double precio;
    private String estado;
    private Long organizadorId;
    private Long transaccionId;
}
