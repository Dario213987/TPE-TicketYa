package org.tudai.ticketya.eventos.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.MapJoin;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.tudai.ticketya.contract.eventos.model.EstadoEvento;
import org.tudai.ticketya.eventos.domain.Evento;

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
     * Hace {@code LEFT JOIN} de evento a sus precios por sector, agrupa por id de evento y
     * ordena por el mínimo de esos precios. Desactiva {@code distinct} porque la base no permite
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

        MapJoin<Evento, Long, Double> precios = root.joinMap("preciosSectores", JoinType.LEFT);
        Expression<Double> precioMinimo = criteriaBuilder.min(precios.value());

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

    /**
     * {@inheritDoc}
     *
     * Ordena con un {@code CASE} sobre el estado para no depender del orden alfabético
     * ni del ordinal del enum: {@code PUBLICADO} obtiene prioridad 0 y el resto 1.
     * Agrupa por id de evento para eliminar duplicados cuando la especificación hace
     * joins (por ejemplo, el filtro por participante).
     */
    @Override
    public Page<Evento> buscarPorRelevancia(Specification<Evento> specification, Pageable pageable) {
        CriteriaBuilder criteriaBuilder = entityManager.getCriteriaBuilder();

        CriteriaQuery<Evento> query = criteriaBuilder.createQuery(Evento.class);
        Root<Evento> root = query.from(Evento.class);
        Predicate predicate = specification.toPredicate(root, query, criteriaBuilder);
        query.where(predicate);
        query.distinct(false);

        Expression<Integer> prioridadEstado = criteriaBuilder.<Integer>selectCase()
                .when(criteriaBuilder.equal(root.get("estado"), EstadoEvento.PUBLICADO), 0)
                .otherwise(1);

        query.groupBy(root.get("id"));
        query.orderBy(
                criteriaBuilder.asc(prioridadEstado),
                criteriaBuilder.asc(root.get("fecha")),
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
