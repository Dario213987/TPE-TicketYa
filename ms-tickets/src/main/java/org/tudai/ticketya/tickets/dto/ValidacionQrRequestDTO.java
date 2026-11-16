package org.tudai.ticketya.tickets.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ValidacionQrRequestDTO {

    @NotBlank(message = "El código QR es obligatorio")
    private String codigoQr;
}
