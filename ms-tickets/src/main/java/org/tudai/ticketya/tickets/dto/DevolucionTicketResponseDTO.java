package org.tudai.ticketya.tickets.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class DevolucionTicketResponseDTO {
    private Long ticketId;
    private String estado;
    private Long transaccionId;
    private Boolean reintegroPendiente;
}
