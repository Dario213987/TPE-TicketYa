package org.tudai.ticketya.preferencias.domain;

/**
 * Evento sugerido con su puntaje y el motivo principal.
 *
 * @param eventoId id del evento recomendado
 * @param puntaje  puntaje lineal calculado por {@code RecomendacionService}
 * @param motivo   criterio de mayor peso que explica la sugerencia
 */
public record Recomendacion(Long eventoId, int puntaje, MotivoRecomendacion motivo) {
}
