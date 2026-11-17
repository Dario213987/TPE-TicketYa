package org.tudai.ticketya.preferencias.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.tudai.ticketya.preferencias.domain.CoCompra;
import org.tudai.ticketya.preferencias.domain.Relacionado;
import org.tudai.ticketya.preferencias.repository.CoCompraRepository;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Co-ocurrencia de compras entre eventos (US-09.03).
 * <p>
 * Cuando un cliente compra un evento nuevo, se incrementa el par
 * {@code (evento, previo)} y {@code (previo, evento)} por cada evento que el
 * cliente ya había comprado. La cantidad representa <b>clientes</b> que
 * compraron ambos eventos: por eso el llamador solo invoca este servicio cuando
 * la compra es nueva para el cliente (una segunda compra del mismo evento no
 * vuelve a contar).
 * <p>
 * La actualización es read-modify-write sobre la lista {@code relacionados}
 * porque el modelo documentado es {@code relacionados[{eventoId, cantidad}]}.
 * El consumidor de eventos procesa los mensajes de forma secuencial, así que no
 * hay escrituras concurrentes sobre el mismo documento en esta entrega.
 */
@Service
@RequiredArgsConstructor
public class CoCompraService {

    private final CoCompraRepository coCompraRepository;

    /**
     * Registra que un cliente compró {@code eventoId} y ya tenía los
     * {@code eventosPrevios}. Actualiza ambas direcciones de cada par.
     *
     * @param eventoId       evento comprado
     * @param eventosPrevios eventos que el cliente ya había comprado
     */
    public void registrarCompra(Long eventoId, Collection<Long> eventosPrevios) {
        if (eventoId == null || eventosPrevios == null) {
            return;
        }

        Set<Long> previos = eventosPrevios.stream()
                .filter(Objects::nonNull)
                .filter(previo -> !eventoId.equals(previo))
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (previos.isEmpty()) {
            return;
        }

        incrementar(eventoId, previos);
        for (Long previo : previos) {
            incrementar(previo, List.of(eventoId));
        }
    }

    /**
     * Suma las cantidades de los eventos relacionados con los eventos base.
     * Se usa para puntuar un candidato por "quienes compraron X también
     * compraron Y" a partir del historial del cliente.
     *
     * @param eventosBase eventos que el cliente compró o usó
     *
     * @return cantidad total por evento relacionado
     */
    public Map<Long, Long> coOcurrencias(Collection<Long> eventosBase) {
        if (eventosBase == null || eventosBase.isEmpty()) {
            return Map.of();
        }

        Map<Long, Long> totales = new HashMap<>();
        for (CoCompra coCompra : coCompraRepository.findAllById(eventosBase)) {
            if (coCompra == null || coCompra.getRelacionados() == null) {
                continue;
            }
            for (Relacionado relacionado : coCompra.getRelacionados()) {
                if (relacionado == null || relacionado.getEventoId() == null) {
                    continue;
                }
                totales.merge(relacionado.getEventoId(), relacionado.getCantidad(), Long::sum);
            }
        }
        return totales;
    }

    private void incrementar(Long eventoId, Collection<Long> otros) {
        CoCompra coCompra = coCompraRepository.findById(eventoId)
                .orElseGet(() -> CoCompra.builder().eventoId(eventoId).build());

        if (coCompra.getRelacionados() == null) {
            coCompra.setRelacionados(new ArrayList<>());
        }

        for (Long otro : otros) {
            Relacionado relacionado = coCompra.getRelacionados().stream()
                    .filter(actual -> Objects.equals(actual.getEventoId(), otro))
                    .findFirst()
                    .orElseGet(() -> {
                        Relacionado nuevo = new Relacionado(otro, 0);
                        coCompra.getRelacionados().add(nuevo);
                        return nuevo;
                    });
            relacionado.setCantidad(relacionado.getCantidad() + 1);
        }

        coCompraRepository.save(coCompra);
    }
}
