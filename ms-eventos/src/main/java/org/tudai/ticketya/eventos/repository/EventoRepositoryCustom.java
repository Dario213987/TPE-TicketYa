package org.tudai.ticketya.eventos.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.tudai.ticketya.eventos.domain.Evento;

/**
 * Extensión del repositorio de eventos con consultas que Spring Data no puede
 * generar por sí solo. Spring la integra a {@code EventoRepository}.
 */
public interface EventoRepositoryCustom {

    /**
     * Busca eventos aplicando la {@code Specification} recibida y los ordena por el
     * precio mínimo de los sectores configurados para el evento, de menor a mayor.
     * La paginación se resuelve en la base.
     *
     * Los eventos sin precio quedan al final. Los empates se desempatan por id para
     * que la paginación sea estable.
     *
     * Ignora el orden del {@code Pageable}: el criterio lo define la propia consulta.
     *
     * @param specification filtros a aplicar sobre los eventos
     * @param pageable página y tamaño solicitados (su orden se ignora)
     *
     * @return una página con los eventos que cumplen los filtros, ordenados por precio mínimo
     */
    Page<Evento> buscarPorPrecio(Specification<Evento> specification, Pageable pageable);

    /**
     * Busca eventos aplicando la {@code Specification} recibida y los ordena por
     * relevancia: primero los {@code PUBLICADO} y después los {@code AGOTADO}.
     * Dentro de cada estado ordena por fecha ascendente y desempata por id para
     * que la paginación sea estable. {@code CANCELADO} no se lista.
     *
     * Ignora el orden del {@code Pageable}: el criterio lo define la propia consulta.
     *
     * @param specification filtros a aplicar sobre los eventos
     * @param pageable página y tamaño solicitados (su orden se ignora)
     *
     * @return una página con los eventos que cumplen los filtros, ordenados por relevancia
     */
    Page<Evento> buscarPorRelevancia(Specification<Evento> specification, Pageable pageable);
}
