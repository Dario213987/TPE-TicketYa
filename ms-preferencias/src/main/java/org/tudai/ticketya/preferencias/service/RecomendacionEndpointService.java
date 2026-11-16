package org.tudai.ticketya.preferencias.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.tudai.ticketya.contract.eventos.api.EventosApiClient;
import org.tudai.ticketya.contract.eventos.model.EstadoEvento;
import org.tudai.ticketya.contract.eventos.model.EventoResumen;
import org.tudai.ticketya.contract.eventos.model.PagedEventos;
import org.tudai.ticketya.contract.preferencias.model.RecomendacionResponseDTO;
import org.tudai.ticketya.contract.preferencias.model.TipoEvento;
import org.tudai.ticketya.preferencias.domain.EventoCandidato;
import org.tudai.ticketya.preferencias.domain.MotivoRecomendacion;
import org.tudai.ticketya.preferencias.domain.Preferencia;
import org.tudai.ticketya.preferencias.domain.Recomendacion;
import org.tudai.ticketya.preferencias.repository.PreferenciaRepository;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Endpoint de sugerencias (US-09.04).
 * <p>
 * Trae de {@code ms-eventos} los eventos publicados y futuros, los convierte en
 * candidatos y delega el puntaje en {@link RecomendacionService} (US-09.03).
 * <ul>
 *     <li>Excluye {@code AGOTADO} y los eventos sin fecha.</li>
 *     <li>Si el cliente no tiene preferencias ni historial, devuelve los próximos
 *     eventos de su ciudad (o generales) con motivo {@code CIUDAD} / {@code PROXIMO}.</li>
 *     <li>Si {@code ms-eventos} no responde, devuelve una lista vacía (200), como pide
 *     la card.</li>
 * </ul>
 * Todo se resuelve con {@code GET /api/eventos}: se recorren sus páginas (máximo 10
 * páginas de 50, el tope que acepta ms-eventos) y el resumen incluye ciudad e ids de
 * participantes para puntuar sin pedir cada detalle.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RecomendacionEndpointService {

    private static final int LIMITE_RECOMENDACIONES = 10;
    private static final int TAMANO_PAGINA = 50;
    private static final int MAX_PAGINAS = 10;
    private static final String ORDEN_FECHA = "fecha";

    private final EventosApiClient eventosClient;
    private final RecomendacionService recomendacionService;
    private final PreferenciaRepository preferenciaRepository;

    /**
     * Recomienda eventos para el cliente autenticado.
     *
     * @param clienteId id del cliente
     *
     * @return hasta 10 eventos sugeridos, o lista vacía si no hay coincidencias o
     *         {@code ms-eventos} no responde
     */
    public List<RecomendacionResponseDTO> recomendar(Long clienteId) {
        List<EventoResumen> resumenes = buscarEventos();
        if (resumenes.isEmpty()) {
            return List.of();
        }

        Map<Long, EventoResumen> porId = new LinkedHashMap<>();
        for (EventoResumen resumen : resumenes) {
            if (resumen != null && resumen.getId() != null) {
                porId.putIfAbsent(resumen.getId(), resumen);
            }
        }

        List<EventoCandidato> candidatos = porId.values().stream()
                .map(this::candidato)
                .toList();
        List<Recomendacion> recomendaciones = recomendacionService.recomendar(
                clienteId, candidatos, LIMITE_RECOMENDACIONES);

        if (!recomendaciones.isEmpty()) {
            return recomendaciones.stream()
                    .map(recomendacion -> respuesta(
                            porId.get(recomendacion.eventoId()),
                            recomendacion.puntaje(),
                            motivo(recomendacion.motivo())))
                    .filter(Objects::nonNull)
                    .toList();
        }

        Preferencia preferencia = preferenciaRepository.findById(clienteId).orElse(null);
        if (!sinPreferencias(preferencia)) {
            return List.of();
        }
        return fallback(preferencia, porId.values());
    }

    private List<EventoResumen> buscarEventos() {
        try {
            List<EventoResumen> visibles = new ArrayList<>();
            int paginaActual = 0;
            int totalPaginas = 0;
            do {
                PagedEventos pagina = eventosClient.listarEventos(
                        null, null, null, null, null,
                        LocalDate.now(), null, null, null,
                        ORDEN_FECHA, null, paginaActual, TAMANO_PAGINA);
                if (pagina == null || pagina.getContent() == null) {
                    break;
                }
                visibles.addAll(pagina.getContent());
                totalPaginas = pagina.getPage() == null || pagina.getPage().getTotalPages() == null
                        ? 0
                        : pagina.getPage().getTotalPages();
                paginaActual++;
            } while (paginaActual < totalPaginas && paginaActual < MAX_PAGINAS);

            return visibles.stream()
                    .filter(Objects::nonNull)
                    .filter(resumen -> resumen.getEstado() == EstadoEvento.PUBLICADO)
                    .filter(resumen -> resumen.getFecha() != null)
                    .toList();
        } catch (RuntimeException e) {
            log.warn("No se pudo consultar ms-eventos para recomendar: {}", e.getMessage());
            return List.of();
        }
    }

    private EventoCandidato candidato(EventoResumen resumen) {
        TipoEvento tipo = resumen.getTipo() == null
                ? null
                : TipoEvento.valueOf(resumen.getTipo().name());
        Instant fecha = resumen.getFecha() == null
                ? null
                : resumen.getFecha().toInstant(ZoneOffset.UTC);
        return new EventoCandidato(resumen.getId(), tipo, resumen.getParticipanteIds(),
                resumen.getCiudad(), fecha);
    }

    private List<RecomendacionResponseDTO> fallback(Preferencia preferencia,
                                                    Collection<EventoResumen> resumenes) {
        String ciudad = preferencia == null || preferencia.getCiudad() == null
                ? null
                : preferencia.getCiudad().trim();
        boolean porCiudad = ciudad != null && !ciudad.isBlank();

        return resumenes.stream()
                .filter(resumen -> !porCiudad || (resumen.getCiudad() != null
                        && resumen.getCiudad().trim().equalsIgnoreCase(ciudad)))
                .limit(LIMITE_RECOMENDACIONES)
                .map(resumen -> respuesta(resumen, 0, motivo(porCiudad
                        ? MotivoRecomendacion.CIUDAD
                        : MotivoRecomendacion.PROXIMO)))
                .toList();
    }

    private static boolean sinPreferencias(Preferencia preferencia) {
        return preferencia == null
                || (lista(preferencia.getTiposFavoritos()).isEmpty()
                && lista(preferencia.getArtistasFavoritos()).isEmpty()
                && lista(preferencia.getHistorial()).isEmpty());
    }

    private static <T> List<T> lista(List<T> valores) {
        return valores == null ? List.of() : valores;
    }

    private RecomendacionResponseDTO respuesta(
            EventoResumen resumen,
            int puntaje,
            org.tudai.ticketya.contract.preferencias.model.MotivoRecomendacion motivo) {
        if (resumen == null) {
            return null;
        }
        return new RecomendacionResponseDTO()
                .eventoId(resumen.getId())
                .nombre(resumen.getNombre())
                .tipo(resumen.getTipo() == null
                        ? null
                        : TipoEvento.valueOf(resumen.getTipo().name()))
                .fecha(resumen.getFecha())
                .estado(resumen.getEstado() == null ? null : resumen.getEstado().name())
                .ciudad(resumen.getCiudad())
                .motivo(motivo)
                .puntaje(puntaje);
    }

    private static org.tudai.ticketya.contract.preferencias.model.MotivoRecomendacion motivo(
            MotivoRecomendacion motivo) {
        return motivo == null
                ? null
                : org.tudai.ticketya.contract.preferencias.model.MotivoRecomendacion
                        .valueOf(motivo.name());
    }
}
