package org.tudai.ticketya.tickets.service;

import org.springframework.stereotype.Component;
import org.tudai.ticketya.contract.eventos.model.EventoDetalleResponse;
import org.tudai.ticketya.core.exception.ForbiddenException;

import java.util.Objects;

@Component
public class EventoAccessValidator {

    /** Valida autorizacion para la solicitud de reportes de un evento   * */
    public void validarAcceso(EventoDetalleResponse evento, Long solicitanteId, boolean esAdmin) {
        if (!esAdmin && !Objects.equals(evento.getOrganizadorId(), solicitanteId)) {
            throw new ForbiddenException(
                    "No tenés permiso para consultar el reporte de este evento");
        }
    }
}
