package org.tudai.ticketya.preferencias.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.tudai.ticketya.core.exception.BadRequestException;
import org.tudai.ticketya.preferencias.service.DestinatariosService;

import java.util.List;

@RestController
@RequestMapping("/api/preferencias/internal")
@RequiredArgsConstructor
public class PreferenciaInternalController {

    private static final int MAX_PARTICIPANTES = 100;
    private static final int MAX_CIUDAD = 100;

    private final DestinatariosService destinatariosService;

    @GetMapping("/destinatarios")
    public List<Long> obtenerDestinatarios(
            @RequestParam(name = "participanteIds", required = false) List<Long> participanteIds,
            @RequestParam(name = "ciudad") String ciudad) {
        validar(participanteIds, ciudad);
        return destinatariosService.obtenerDestinatarios(participanteIds, ciudad);
    }

    private void validar(List<Long> participanteIds, String ciudad) {
        if (!StringUtils.hasText(ciudad) || ciudad.length() > MAX_CIUDAD) {
            throw new BadRequestException("La ciudad debe tener entre 1 y 100 caracteres");
        }
        if (participanteIds == null) {
            return;
        }
        if (participanteIds.size() > MAX_PARTICIPANTES) {
            throw new BadRequestException("No se pueden enviar mas de 100 participantes");
        }
        if (participanteIds.stream().anyMatch(id -> id == null || id < 1)) {
            throw new BadRequestException("Los ids de participante deben ser mayores a 0");
        }
    }
}
