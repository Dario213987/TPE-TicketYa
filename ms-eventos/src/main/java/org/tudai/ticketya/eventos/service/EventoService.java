package org.tudai.ticketya.eventos.service;

import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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
     * Lista los eventos visibles aplicando los filtros
     * opcionales recibidos. Los filtros presentes se combinan con AND y el resultado
     * se devuelve paginado. Sin filtros, devuelve todos los eventos visibles.
     *
     * @param filtro filtros opcionales: tipo, organizador, participante, ciudad y rango de fechas
     * @param pageable paginación y orden del resultado
     *
     * @return una página con los eventos visibles que cumplen todos los filtros
     */
    @Transactional(readOnly = true)
    public Page<Evento> listarPublicados(EventoFiltroRequestDTO filtro, Pageable pageable) {
        validarRangoDeFechas(filtro);
        return eventoRepository.findAll(construirSpecification(filtro), pageable);
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
