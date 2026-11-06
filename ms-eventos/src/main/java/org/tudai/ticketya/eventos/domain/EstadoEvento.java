package org.tudai.ticketya.eventos.domain;

import java.util.EnumSet;
import java.util.Set;

public enum EstadoEvento {
    BORRADOR,
    CANCELADO,
    FINALIZADO,
    PUBLICADO,
    SUSPENDIDO,
    AGOTADO;

    public static final Set<EstadoEvento> VISIBLES = EnumSet.of(PUBLICADO, FINALIZADO, AGOTADO);
}
