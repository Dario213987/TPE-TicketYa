package org.tudai.ticketya.preferencias.event;

import java.time.Instant;

/**
 * Datos de una entrada utilizada que publica ms-tickets en el stream
 * {@code ticket.utilizado}.
 *
 * @param clienteId  cliente que ingresó
 * @param eventoId   evento en el que se usó la entrada
 * @param ticketId   id de la entrada
 * @param fechaUso   momento del ingreso
 * @param traceId    id de correlación de la operación que originó el evento
 */
public record EventoTicketUtilizado(Long clienteId,
                                    Long eventoId,
                                    Long ticketId,
                                    Instant fechaUso,
                                    String traceId) {
}
