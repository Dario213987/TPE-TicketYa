package org.tudai.ticketya.preferencias.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.ArrayList;
import java.util.List;

/**
 * Co-ocurrencia de compras de un evento (US-09.03).
 * <p>
 * Un documento por evento con la lista de eventos que compraron sus mismos
 * clientes y cuántos clientes compraron ambos. Se mantiene de forma incremental
 * cuando llega un {@code venta.pagada} de un evento nuevo para el cliente.
 * La usa el recomendador para sumar interés por "quienes compraron X también
 * compraron Y".
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "co_compras")
public class CoCompra {

    @Id
    private Long eventoId;

    @Builder.Default
    private List<Relacionado> relacionados = new ArrayList<>();
}
