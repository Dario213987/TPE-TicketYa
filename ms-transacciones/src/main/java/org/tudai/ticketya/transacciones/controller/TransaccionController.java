package org.tudai.ticketya.transacciones.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.PagedModel;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.tudai.ticketya.contract.transacciones.model.GenerarTransaccionRequest;
import org.tudai.ticketya.contract.transacciones.model.TipoTransaccion;
import org.tudai.ticketya.contract.transacciones.model.TransaccionResponse;
import org.tudai.ticketya.core.Authorize;
import org.tudai.ticketya.core.exception.BadRequestException;
import org.tudai.ticketya.transacciones.domain.Transaccion;
import org.tudai.ticketya.transacciones.service.TransaccionService;

import java.util.Set;

@RestController
@RequestMapping("/api/transacciones")
@RequiredArgsConstructor
@Authorize("hasRole('ORGANIZADOR')")
public class TransaccionController {

    private static final Set<String> ORDENABLES = Set.of("id", "creadoEl", "monto", "estado");

    private final TransaccionService service;
    private final ModelMapper mapper;

    @PostMapping("/debitos")
    @ResponseStatus(HttpStatus.CREATED)
    public TransaccionResponse debit(@Valid @RequestBody GenerarTransaccionRequest request) {
        return create(TipoTransaccion.DEBITO, request);
    }

    @PostMapping("/creditos")
    @ResponseStatus(HttpStatus.CREATED)
    public TransaccionResponse credit(@Valid @RequestBody GenerarTransaccionRequest request) {
        return create(TipoTransaccion.CREDITO, request);
    }

    @GetMapping("/{id}")
    public TransaccionResponse obtenerTransaccion(@PathVariable Long id) {
        return toResponseDTO(service.findById(id));
    }

    @GetMapping("/ticket/{ticketId}")
    public PagedModel<TransaccionResponse> findByTicket(
            @PathVariable Long ticketId,
            @RequestParam(required = false) TipoTransaccion tipo,
            @PageableDefault(sort = {"creadoEl", "id"}, direction = Sort.Direction.DESC) Pageable pageable) {
        return toPage(service.findByTicketIdAndType(ticketId, tipo, ordenable(pageable)));
    }

    @GetMapping("/organizador/{organizadorId}")
    public PagedModel<TransaccionResponse> findAllByOrganizador(
            @PathVariable Long organizadorId,
            @PageableDefault(sort = {"creadoEl", "id"}, direction = Sort.Direction.DESC) Pageable pageable) {
        return toPage(service.findAllByOrganizador(organizadorId, ordenable(pageable)));
    }

    private static Pageable ordenable(Pageable pageable) {
        pageable.getSort().forEach(orden -> {
            if (!ORDENABLES.contains(orden.getProperty())) {
                throw new BadRequestException(
                        "No se puede ordenar por '" + orden.getProperty() + "'. Campos válidos: " + ORDENABLES);
            }
        });
        return pageable;
    }

    private TransaccionResponse create(TipoTransaccion tipo, GenerarTransaccionRequest request) {
        Transaccion pedido = mapper.map(request, Transaccion.class);
        pedido.setTipo(tipo);
        return mapper.map(service.crear(pedido), TransaccionResponse.class);
    }

    private TransaccionResponse toResponseDTO(Transaccion transaccion) {
        return mapper.map(transaccion, TransaccionResponse.class);
    }

    private PagedModel<TransaccionResponse> toPage(Page<Transaccion> page) {
        return new PagedModel<>(page.map(this::toResponseDTO));
    }
}