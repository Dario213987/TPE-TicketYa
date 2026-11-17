package org.tudai.ticketya.preferencias.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.tudai.ticketya.contract.preferencias.model.TipoEvento;
import org.tudai.ticketya.preferencias.domain.EventoCandidato;
import org.tudai.ticketya.preferencias.domain.HistorialCompra;
import org.tudai.ticketya.preferencias.domain.MotivoRecomendacion;
import org.tudai.ticketya.preferencias.domain.Preferencia;
import org.tudai.ticketya.preferencias.domain.Recomendacion;
import org.tudai.ticketya.preferencias.repository.PreferenciaRepository;

import java.time.Instant;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Algoritmo de recomendación simplificado (US-09.03).
 * <p>
 * Puntaje lineal, sin aprendizaje automático, en este orden de peso:
 * <ol>
 *     <li><b>Artista</b> (+5): un participante del candidato está en
 *     {@code artistasFavoritos} o participó de un evento del historial con
 *     {@code usado = true} (al que el cliente asistió).</li>
 *     <li><b>Tipo</b> (+3): el tipo está en {@code tiposFavoritos} o aparece al
 *     menos una vez en el historial (comprado o asistido).</li>
 *     <li><b>Co-ocurrencia</b> (+1 a +3): suma de cuántos clientes compraron
 *     alguno de los eventos del historial junto con el candidato, con tope 3.</li>
 *     <li><b>Ciudad</b> (+1): la ciudad del candidato coincide con la del
 *     cliente.</li>
 * </ol>
 * Se excluyen los eventos para los que el cliente ya tiene entradas (historial
 * con {@code comprado} o {@code usado}) y se devuelven hasta {@code limite}
 * eventos ordenados por puntaje, fecha y id. Un cliente sin historial se puntúa
 * solo con sus preferencias declaradas; si tampoco tiene preferencias, no hay
 * coincidencias y la lista queda vacía (el fallback de "próximos eventos de mi
 * ciudad" es parte del endpoint de US-09.04).
 */
@Service
@RequiredArgsConstructor
public class RecomendacionService {

    public static final int PESO_ARTISTA = 5;
    public static final int PESO_TIPO = 3;
    public static final int PESO_CIUDAD = 1;
    public static final int PESO_MAX_CO_COMPRA = 3;

    private final PreferenciaRepository preferenciaRepository;
    private final CoCompraService coCompraService;

    /**
     * Recomienda eventos para un cliente. Carga sus preferencias e historial.
     *
     * @param clienteId  cliente autenticado
     * @param candidatos eventos publicados y futuros que armó el endpoint
     * @param limite     cantidad máxima de sugerencias
     *
     * @return sugerencias ordenadas; vacía si no hay candidatos o coincidencias
     */
    public List<Recomendacion> recomendar(Long clienteId, List<EventoCandidato> candidatos, int limite) {
        if (clienteId == null || candidatos == null || candidatos.isEmpty() || limite < 1) {
            return List.of();
        }

        Preferencia preferencia = preferenciaRepository.findById(clienteId)
                .orElseGet(() -> Preferencia.builder().clienteId(clienteId).build());
        return recomendar(preferencia, candidatos, limite);
    }

    List<Recomendacion> recomendar(Preferencia preferencia, List<EventoCandidato> candidatos, int limite) {
        if (preferencia == null || candidatos == null || candidatos.isEmpty() || limite < 1) {
            return List.of();
        }

        List<HistorialCompra> historial = historialDe(preferencia);
        Set<Long> eventosDelCliente = historial.stream()
                .filter(RecomendacionService::cuentaComoCompra)
                .map(HistorialCompra::getEventoId)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(HashSet::new));
        Map<Long, Long> coOcurrencias = coCompraService.coOcurrencias(eventosDelCliente);

        return candidatos.stream()
                .filter(Objects::nonNull)
                .filter(candidato -> candidato.eventoId() != null)
                .filter(candidato -> !eventosDelCliente.contains(candidato.eventoId()))
                .map(candidato -> puntuar(preferencia, historial, candidato,
                        coOcurrencias.getOrDefault(candidato.eventoId(), 0L)))
                .filter(punteado -> punteado.puntaje > 0 && punteado.motivo != null)
                .sorted(Comparator.comparingInt((Puntuado punteado) -> punteado.puntaje).reversed()
                        .thenComparing(punteado -> punteado.candidato.fecha(),
                                Comparator.nullsLast(Instant::compareTo))
                        .thenComparing(punteado -> punteado.candidato.eventoId()))
                .limit(limite)
                .map(punteado -> new Recomendacion(
                        punteado.candidato.eventoId(), punteado.puntaje, punteado.motivo))
                .toList();
    }

    private Puntuado puntuar(Preferencia preferencia, List<HistorialCompra> historial,
                             EventoCandidato candidato, long coCompra) {
        int puntaje = 0;
        MotivoRecomendacion motivo = null;

        if (coincideArtista(preferencia, historial, candidato)) {
            puntaje += PESO_ARTISTA;
            motivo = MotivoRecomendacion.ARTISTA;
        }
        if (coincideTipo(preferencia, historial, candidato)) {
            puntaje += PESO_TIPO;
            if (motivo == null) {
                motivo = MotivoRecomendacion.TIPO;
            }
        }
        int pesoCoCompra = (int) Math.min(PESO_MAX_CO_COMPRA, coCompra);
        if (pesoCoCompra > 0) {
            puntaje += pesoCoCompra;
            if (motivo == null) {
                motivo = MotivoRecomendacion.CO_COMPRA;
            }
        }
        if (coincideCiudad(preferencia, candidato)) {
            puntaje += PESO_CIUDAD;
            if (motivo == null) {
                motivo = MotivoRecomendacion.CIUDAD;
            }
        }

        return new Puntuado(candidato, puntaje, motivo);
    }

    private boolean coincideArtista(Preferencia preferencia, List<HistorialCompra> historial,
                                    EventoCandidato candidato) {
        List<Long> participantes = candidato.participanteIds();
        if (participantes == null || participantes.isEmpty()) {
            return false;
        }

        Set<Long> favoritos = new HashSet<>(lista(preferencia.getArtistasFavoritos()));
        if (participantes.stream().anyMatch(favoritos::contains)) {
            return true;
        }

        return historial.stream()
                .filter(HistorialCompra::isUsado)
                .map(HistorialCompra::getParticipanteIds)
                .filter(Objects::nonNull)
                .anyMatch(asistidos -> asistidos.stream().anyMatch(participantes::contains));
    }

    private boolean coincideTipo(Preferencia preferencia, List<HistorialCompra> historial,
                                 EventoCandidato candidato) {
        TipoEvento tipo = candidato.tipo();
        if (tipo == null) {
            return false;
        }
        if (lista(preferencia.getTiposFavoritos()).contains(tipo)) {
            return true;
        }
        return historial.stream().anyMatch(entrada -> entrada.getTipo() == tipo);
    }

    private boolean coincideCiudad(Preferencia preferencia, EventoCandidato candidato) {
        return preferencia.getCiudad() != null && candidato.ciudad() != null
                && preferencia.getCiudad().trim().equalsIgnoreCase(candidato.ciudad().trim());
    }

    private static List<HistorialCompra> historialDe(Preferencia preferencia) {
        return preferencia.getHistorial() == null ? List.of() : preferencia.getHistorial();
    }

    private static boolean cuentaComoCompra(HistorialCompra entrada) {
        return entrada != null && (entrada.isComprado() || entrada.isUsado());
    }

    private static <T> List<T> lista(List<T> valores) {
        return valores == null ? List.of() : valores;
    }

    private record Puntuado(EventoCandidato candidato, int puntaje, MotivoRecomendacion motivo) {
    }
}
