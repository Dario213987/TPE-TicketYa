package org.tudai.ticketya.preferencias.domain;

import org.tudai.ticketya.contract.preferencias.model.TipoEvento;

import java.time.Instant;
import java.util.List;

/**
 * Evento candidato a recomendar. Lo arma el endpoint de sugerencias (US-09.04)
 * a partir de los eventos publicados y futuros de ms-eventos; el algoritmo de
 * esta card solo lo puntúa.
 *
 * @param eventoId        id del evento
 * @param tipo            tipo de evento
 * @param participanteIds ids de los participantes (artistas)
 * @param ciudad          ciudad del evento
 * @param fecha           fecha y hora del evento
 */
public record EventoCandidato(Long eventoId,
                              TipoEvento tipo,
                              List<Long> participanteIds,
                              String ciudad,
                              Instant fecha) {
}
