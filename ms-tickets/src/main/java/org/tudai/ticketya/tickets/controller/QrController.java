package org.tudai.ticketya.tickets.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.tudai.ticketya.core.Authorize;
import org.tudai.ticketya.tickets.dto.ValidacionQrRequestDTO;
import org.tudai.ticketya.tickets.dto.ValidacionQrResponseDTO;
import org.tudai.ticketya.tickets.service.QrValidationService;

@RestController
@RequestMapping("/api/tickets/qr")
@RequiredArgsConstructor
public class QrController {

    private final QrValidationService qrValidationService;

    @PostMapping("/validar")
    @Authorize("hasAnyRole('ORGANIZADOR','ADMINISTRADOR')")
    public ValidacionQrResponseDTO validar(@Valid @RequestBody ValidacionQrRequestDTO request) {
        return qrValidationService.validar(request.getCodigoQr());
    }
}
