package org.tudai.ticketya.eventos.service;

import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.tudai.ticketya.core.exception.BadRequestException;
import org.tudai.ticketya.eventos.domain.EstadoEvento;
import org.tudai.ticketya.eventos.domain.Evento;
import org.tudai.ticketya.eventos.domain.Participante;
import org.tudai.ticketya.eventos.dto.evento.EventoFiltroRequestDTO;
import org.tudai.ticketya.eventos.repository.EventoRepository;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class EventoService {

    private final EventoRepository eventoRepository;

    @Transactional(readOnly = true)
    public Page<Evento> listarPublicados(Pageable pageable) {
        return eventoRepository.findByEstadoIn(EstadoEvento.VISIBLES, pageable);
    }

    /**
     * Lista los eventos visibles aplicando los filtros opcionales recibidos. Los filtros
     * presentes se combinan con AND y el resultado se devuelve paginado. Sin filtros,
     * devuelve todos los eventos visibles.
     * <p>
     * El orden se elige con {@code filtro.orden}:
     * <ul>
     *   <li>{@code precio}: ordena por el precio mínimo de los sectores, directamente en la base.</li>
     *   <li>{@code fecha}: ordena por fecha ascendente (desempata por id).</li>
     *   <li>sin valor: respeta el orden del {@code Pageable} y, si no trae ninguno, ordena por fecha.</li>
     * </ul>
     *
     * @param filtro   filtros opcionales: tipo, organizador, participante, ciudad, rango de fechas y orden
     * @param pageable paginación y orden del resultado
     *
     * @return una página con los eventos visibles que cumplen todos los filtros
     *
     * @throws BadRequestException si el rango de fechas es inválido o el orden no está permitido
     */
    @Transactional(readOnly = true)
    public Page<Evento> listarPublicados(EventoFiltroRequestDTO filtro, Pageable pageable) {
        validarRangoDeFechas(filtro);
        OrdenEvento orden = resolverOrden(filtro.getOrden());
        Specification<Evento> specification = construirSpecification(filtro);

        //Si el orden es por precio, busca directamente por el precio
        if (orden == OrdenEvento.PRECIO) {
            return eventoRepository.buscarPorPrecio(specification, sinOrden(pageable));
        }

        //Por defencto siempre ordena por fecha
        return eventoRepository.findAll(specification, aplicarOrdenFecha(pageable));
    }


    /**
     * Convierte el texto del parámetro {@code orden} en un OrdenEvento.
     * No distingue mayúsculas ni espacios al inicio o al final.
     *
     * @param orden texto recibido (puede ser nulo o vacío)
     *
     * @return el orden correspondiente, o {@code null} si no se indicó ninguno
     *
     * @throws BadRequestException si el orden es "relevancia" (todavía no definido)
     *                             o no es uno de los valores permitidos (fecha, precio)
     */
    private OrdenEvento resolverOrden(String orden) {
        if (!StringUtils.hasText(orden)) {
            return null;
        }

        return switch (orden.trim().toLowerCase(Locale.ROOT)) {
            case "fecha" -> OrdenEvento.FECHA;
            case "precio" -> OrdenEvento.PRECIO;
            //PENDIENTE A RESOLVER
            case "relevancia" -> throw new BadRequestException(
                    "El orden por relevancia aún no está definido");
            default -> throw new BadRequestException(
                    "Orden inválido: '" + orden + "'. Valores permitidos: fecha, precio");
        };
    }

    /**
     * Arma el {@code Pageable} para ordenar por fecha ascendente, con el id como desempate
     * para que la paginación sea estable. Ignora el orden que traiga el {@code Pageable}.
     *
     * @param pageable paginación recibida
     *
     * @return {@code Pageable} con orden por fecha e id
     */
    private Pageable aplicarOrdenFecha(Pageable pageable) {
        Sort sort = Sort.by(Sort.Direction.ASC, "fecha")
                .and(Sort.by(Sort.Direction.ASC, "id"));

        if (pageable.isUnpaged()) {
            return Pageable.unpaged(sort);
        }

        return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), sort);
    }

    /**
     * Devuelve el {@code Pageable} sin criterio de orden, conservando página y tamaño.
     * Se usa cuando el orden lo define la propia consulta (por precio) y no el {@code Pageable}.
     *
     * @param pageable paginación recibida
     *
     * @return {@code Pageable} sin orden
     */
    private Pageable sinOrden(Pageable pageable) {
        if (pageable.isUnpaged()) {
            return Pageable.unpaged();
        }

        return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize());
    }

    /** Criterios de orden admitidos para el listado de eventos. */
    private enum OrdenEvento {
        FECHA,
        PRECIO
    }

    /**
     * Verifica que el rango de fechas sea apto.
     *
     * @param filtro filtro aplicado
     *
     * @throws BadRequestException si la fechaDesde > fechaHasta
     * */
    private void validarRangoDeFechas(EventoFiltroRequestDTO filtro) {
        if (filtro.getFechaDesde() != null && filtro.getFechaHasta() != null
                && filtro.getFechaDesde().isAfter(filtro.getFechaHasta())) {
            throw new BadRequestException("La fecha 'desde' no puede ser posterior a la fecha 'hasta'");
        }
    }

    /**
     * Construye la Specification que combina con AND los filtros presentes y siempre
     * restringe el resultado a los eventos en estado visible ({@code EstadoEvento.VISIBLES}).
     *
     * Los filtros que vienen nulos o vacíos se ignoran:
     * tipo y ciudad: coincidencia sin distinguir mayúsculas/minúsculas y sin espacios al inicio ni al final.
     * organizador: coincidencia por id.
     * fecha desde / hasta: rango inclusivo sobre la fecha del evento.
     * participante: coincidencia exacta con el nombre de algún artista, sin distinguir
     * mayúsculas ni tildes (se normalizan ambos lados). Hace un join con los artistas
     * y usa {@code distinct} para no repetir eventos.
     *
     * @param filtro filtros opcionales: tipo, organizador, participante, ciudad y rango de fechas
     *
     * @return Specification lista para usar en el repository
     */
    private Specification<Evento> construirSpecification(EventoFiltroRequestDTO filtro) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(root.get("estado").in(EstadoEvento.VISIBLES));

            if (StringUtils.hasText(filtro.getTipo())) {
                predicates.add(cb.equal(cb.lower(root.get("tipo").as(String.class)),
                        filtro.getTipo().trim().toLowerCase(Locale.ROOT)));
            }

            if (filtro.getOrganizadorId() != null) {
                predicates.add(cb.equal(root.get("organizadorId"), filtro.getOrganizadorId()));
            }

            if (StringUtils.hasText(filtro.getCiudad())) {
                predicates.add(cb.equal(cb.lower(root.get("lugar").get("ciudad").as(String.class)),
                        filtro.getCiudad().trim().toLowerCase(Locale.ROOT)));
            }
            if (filtro.getFechaDesde() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("fecha"), filtro.getFechaDesde()));
            }
            if (filtro.getFechaHasta() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("fecha"), filtro.getFechaHasta()));
            }
            if (StringUtils.hasText(filtro.getParticipante())) {
                Join<Evento, Participante> participante = root.join("artistas");
                Expression<String> nombreNormalizado = cb.function(
                        "translate",
                        String.class,
                        cb.lower(participante.get("nombre")),
                        cb.literal("áéíóúüñ"),
                        cb.literal("aeiouun"));
                predicates.add(cb.equal(nombreNormalizado, normalizar(filtro.getParticipante())));
                query.distinct(true);
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    private static String normalizar(String valor) {
        return Normalizer.normalize(valor.trim(), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT);
    }
}
