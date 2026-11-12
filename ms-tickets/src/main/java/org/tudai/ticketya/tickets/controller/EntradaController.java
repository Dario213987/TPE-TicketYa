package org.tudai.ticketya.tickets.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.PagedModel;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.tudai.ticketya.core.Authorize;
import org.tudai.ticketya.core.UserContext;
import org.tudai.ticketya.tickets.dto.MiEntradaDTO;
import org.tudai.ticketya.tickets.dto.QrEntradaDTO;
import org.tudai.ticketya.tickets.service.EntradaService;

@RestController
@RequestMapping("/api/tickets")
@RequiredArgsConstructor
public class EntradaController {

    private final EntradaService entradaService;
    private final UserContext userContext;

    @GetMapping("/mis-entradas")
    @Authorize("hasRole('CLIENTE')")
    public PagedModel<MiEntradaDTO> misEntradas(@PageableDefault(size = 20) Pageable pageable) {
        return new PagedModel<>(entradaService.listar(userContext.userId(), pageable));
    }

    @GetMapping("/{ticketId}/qr")
    @Authorize("hasRole('CLIENTE')")
    public QrEntradaDTO qr(@PathVariable Long ticketId) {
        return entradaService.qr(ticketId, userContext.userId());
    }
}
