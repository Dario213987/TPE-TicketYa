package org.tudai.ticketya.preferencias.domain;

/**
 * Motivo principal por el que se recomienda un evento.
 * <p>
 * Cuando un candidato suma por varios criterios, se informa el de mayor peso:
 * {@link #ARTISTA} (5) &gt; {@link #TIPO} (3) &gt; {@link #CO_COMPRA} (hasta 3)
 * &gt; {@link #CIUDAD} (1). {@link #PROXIMO} no suma puntos: lo usa el endpoint
 * cuando el cliente no tiene preferencias y se le muestran los próximos eventos.
 */
public enum MotivoRecomendacion {
    ARTISTA,
    TIPO,
    CO_COMPRA,
    CIUDAD,
    PROXIMO
}
