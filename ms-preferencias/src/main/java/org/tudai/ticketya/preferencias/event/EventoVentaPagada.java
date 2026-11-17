package org.tudai.ticketya.preferencias.event;

import org.tudai.ticketya.contract.preferencias.model.TipoEvento;

import java.time.Instant;
import java.util.List;

/**
 * Datos de una compra confirmada que publica ms-tickets en el stream
 * {@code venta.pagada}.
 *
 * @param clienteId       cliente que compró
 * @param eventoId        evento comprado
 * @param tipo            tipo de evento
 * @param participanteIds ids de los participantes del evento
 * @param ciudad          ciudad del evento
 * @param fechaEvento     fecha y hora del evento
 * @param traceId         id de correlación de la operación que originó el evento
 */
public record EventoVentaPagada(Long clienteId,
                                Long eventoId,
                                TipoEvento tipo,
                                List<Long> participanteIds,
                                String ciudad,
                                Instant fechaEvento,
                                String traceId) {
}
