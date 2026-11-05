package org.tudai.ticketya.eventos.domain;

import java.util.EnumSet;
import java.util.Set;

public enum EstadoEvento {
    BORRADOR,
    CONFIRMADO,
    CANCELADO,
    FINALIZADO,
    PUBLICADO,
    SUSPENDIDO,
    AGOTADO;

    public static final Set<EstadoEvento> VISIBLES = EnumSet.of(CONFIRMADO, FINALIZADO, AGOTADO);
}
