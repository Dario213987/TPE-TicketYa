package org.tudai.ticketya.eventos.service;

import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.tudai.ticketya.contract.eventos.model.EventoFiltroRequest;
import org.tudai.ticketya.contract.eventos.model.EventoResumen;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.StringJoiner;

/**
 * Cache de los resultados de búsqueda del listado de eventos ({@code GET /api/eventos}) en Redis
 * (TKY-83).
 * <p>
 * Guarda la página ya mapeada a {@link EventoResumen} (nunca las entidades) en la cache
 * {@value #CACHE}, con clave armada con todos los filtros, la página, el tamaño y el orden, y con
 * el TTL corto de {@code ticketya.cache.ttl.evento-busqueda} (30 s). Es un bean aparte de
 * {@link EventoService} porque {@code @Cacheable} no se aplica cuando un método llama a otro de la
 * misma clase.
 * <p>
 * No se invalida: publicar, cancelar o editar un evento no borra las búsquedas guardadas, así que
 * la inconsistencia máxima del listado es el TTL (aceptado, ver {@code docs/api/ms-eventos-cache.md}).
 */
@Component
@RequiredArgsConstructor
public class EventoBusquedaCache {

    public static final String CACHE = "evento-busqueda";

    private final EventoService eventoService;
    private final ModelMapper modelMapper;

    /**
     * Página de eventos visibles que cumplen los filtros, desde la cache o la base. El controller
     * arma la página con {@link Resultado#comoPage(Pageable)}.
     *
     * @throws org.tudai.ticketya.core.exception.BadRequestException si los filtros son inválidos
     *         (no se cachea)
     */
    @Cacheable(cacheNames = CACHE, key = "T(org.tudai.ticketya.eventos.service.EventoBusquedaCache).clave(#filtro, #pageable)")
    @Transactional(readOnly = true)
    public Resultado buscar(EventoFiltroRequest filtro, Pageable pageable) {
        Page<EventoResumen> pagina = eventoService.listarPublicados(filtro, pageable)
                .map(evento -> modelMapper.map(evento, EventoResumen.class));
        return new Resultado(new ArrayList<>(pagina.getContent()), pagina.getTotalElements());
    }

    /**
     * Clave de la búsqueda: todos los filtros, la página, el tamaño y el orden. Dos búsquedas con
     * los mismos parámetros comparten la entrada; cualquier diferencia arma otra.
     */
    public static String clave(EventoFiltroRequest filtro, Pageable pageable) {
        StringJoiner clave = new StringJoiner("|");
        clave.add("tipo=" + filtro.getTipo())
                .add("organizadorId=" + filtro.getOrganizadorId())
                .add("participante=" + filtro.getParticipante())
                .add("participanteId=" + filtro.getParticipanteId())
                .add("ciudad=" + filtro.getCiudad())
                .add("desde=" + filtro.getDesde())
                .add("hasta=" + filtro.getHasta())
                .add("fechaDesde=" + filtro.getFechaDesde())
                .add("fechaHasta=" + filtro.getFechaHasta())
                .add("orden=" + filtro.getOrden());
        if (pageable.isPaged()) {
            clave.add("page=" + pageable.getPageNumber()).add("size=" + pageable.getPageSize());
        } else {
            clave.add("unpaged");
        }
        return clave.add("sort=" + pageable.getSort()).toString();
    }

    /** Lo que se guarda en la cache: el contenido de la página y el total de resultados. */
    public record Resultado(List<EventoResumen> contenido, long total) implements Serializable {

        public Page<EventoResumen> comoPage(Pageable pageable) {
            return new PageImpl<>(contenido, pageable, total);
        }
    }
}
