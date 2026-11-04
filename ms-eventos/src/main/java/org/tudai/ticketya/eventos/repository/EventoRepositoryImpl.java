package org.tudai.ticketya.eventos.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.tudai.ticketya.eventos.domain.Evento;
import org.tudai.ticketya.eventos.domain.Lugar;
import org.tudai.ticketya.eventos.domain.Sector;

import java.util.List;

/**
 * Implementación de {@link EventoRepositoryCustom} con Criteria API.
 * Spring Data la detecta por el sufijo {@code Impl}.
 */
public class EventoRepositoryImpl implements EventoRepositoryCustom {

    @PersistenceContext
    private EntityManager entityManager;

    /**
     * {@inheritDoc}
     *
     * Hace {@code LEFT JOIN} de evento a lugar y de lugar a sectores, agrupa por id de evento
     * y ordena por {@code MIN(precio)}. Desactiva {@code distinct} porque la base no permite
     * ordenar por una agregación junto con {@code DISTINCT}; los duplicados se evitan con el
     * {@code GROUP BY}. El total se calcula con una consulta {@code count} aparte, con los
     * mismos filtros.
     */
    @Override
    public Page<Evento> buscarPorPrecio(Specification<Evento> specification, Pageable pageable) {
        CriteriaBuilder criteriaBuilder = entityManager.getCriteriaBuilder();

        CriteriaQuery<Evento> query = criteriaBuilder.createQuery(Evento.class);
        Root<Evento> root = query.from(Evento.class);
        Predicate predicate = specification.toPredicate(root, query, criteriaBuilder);
        query.where(predicate);
        query.distinct(false);

        Join<Evento, Lugar> lugar = root.join("lugar", JoinType.LEFT);
        Join<Lugar, Sector> sector = lugar.join("sectores", JoinType.LEFT);
        Expression<Double> precioMinimo = criteriaBuilder.min(sector.get("precio").as(Double.class));

        query.groupBy(root.get("id"));
        query.orderBy(
                criteriaBuilder.asc(criteriaBuilder.coalesce(precioMinimo, Double.MAX_VALUE)),
                criteriaBuilder.asc(root.get("id")));

        TypedQuery<Evento> typedQuery = entityManager.createQuery(query);
        if (!pageable.isUnpaged()) {
            typedQuery.setFirstResult((int) pageable.getOffset());
            typedQuery.setMaxResults(pageable.getPageSize());
        }

        List<Evento> content = typedQuery.getResultList();

        CriteriaQuery<Long> countQuery = criteriaBuilder.createQuery(Long.class);
        Root<Evento> countRoot = countQuery.from(Evento.class);
        Predicate countPredicate = specification.toPredicate(countRoot, countQuery, criteriaBuilder);
        countQuery.select(criteriaBuilder.countDistinct(countRoot));
        countQuery.where(countPredicate);
        countQuery.distinct(false);

        long total = entityManager.createQuery(countQuery).getSingleResult();

        return new PageImpl<>(content, pageable, total);
    }
}
