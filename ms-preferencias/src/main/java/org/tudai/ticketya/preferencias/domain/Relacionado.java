package org.tudai.ticketya.preferencias.domain;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Evento relacionado con otro por co-ocurrencia de compras: cuántos clientes
 * compraron ambos.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Relacionado {

    private Long eventoId;

    private long cantidad;
}
