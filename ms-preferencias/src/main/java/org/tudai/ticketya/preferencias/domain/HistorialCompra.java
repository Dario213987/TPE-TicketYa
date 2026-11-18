package org.tudai.ticketya.preferencias.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.tudai.ticketya.contract.preferencias.model.TipoEvento;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Una entrada del historial del cliente: un evento que compró y, si ya ingresó,
 * que también usó.
 * <p>
 * El historial lo escribe únicamente el consumidor de eventos
 * ({@code venta.pagada} y {@code ticket.utilizado}); el cliente solo lo lee.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HistorialCompra {

    private Long eventoId;

    private TipoEvento tipo;

    @Builder.Default
    private List<Long> participanteIds = new ArrayList<>();

    private String ciudad;

    private Instant fecha;

    @Builder.Default
    private boolean comprado = false;

    @Builder.Default
    private boolean usado = false;
}
