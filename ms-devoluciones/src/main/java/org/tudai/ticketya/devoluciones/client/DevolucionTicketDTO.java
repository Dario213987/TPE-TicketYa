package org.tudai.ticketya.devoluciones.client;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class DevolucionTicketDTO {
    private Long ticketId;
    private String estado;
    private Long transaccionId;
    private Boolean reintegroPendiente;
}
