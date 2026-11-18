package org.tudai.ticketya.tickets.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ValidarAccesoRequestDTO {

    @NotBlank(message = "El codigo QR es obligatorio")
    private String codigoQr;
}
