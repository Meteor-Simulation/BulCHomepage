package com.bulc.homepage.licensing.query;

import com.bulc.homepage.licensing.domain.License;
import com.bulc.homepage.licensing.domain.OwnerType;
import com.bulc.homepage.licensing.query.view.LicenseDetailView;
import com.bulc.homepage.licensing.query.view.LicenseSummaryView;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class LicenseQueryRepositoryImpl implements LicenseQueryRepository {

    private final EntityManager em;

    @Override
    public Optional<LicenseDetailView> findDetailById(UUID licenseId) {
        String jpql = """
                SELECT l FROM License l
                LEFT JOIN FETCH l.activations
                WHERE l.id = :id
                """;

        List<License> result = em.createQuery(jpql, License.class)
                .setParameter("id", licenseId)
                .getResultList();

        return result.stream()
                .findFirst()
                .map(LicenseDetailView::from);
    }

    @Override
    public Optional<LicenseDetailView> findDetailByKey(String licenseKey) {
        String jpql = """
                SELECT l FROM License l
                LEFT JOIN FETCH l.activations
                WHERE l.licenseKey = :key
                """;

        List<License> result = em.createQuery(jpql, License.class)
                .setParameter("key", licenseKey)
                .getResultList();

        return result.stream()
                .findFirst()
                .map(LicenseDetailView::from);
    }

    @Override
    public List<LicenseSummaryView> findByOwner(OwnerType ownerType, UUID ownerId) {
        String jpql = """
                SELECT l FROM License l
                LEFT JOIN FETCH l.activations
                WHERE l.ownerType = :ownerType AND l.ownerId = :ownerId
                ORDER BY l.createdAt DESC
                """;

        List<License> licenses = em.createQuery(jpql, License.class)
                .setParameter("ownerType", ownerType)
                .setParameter("ownerId", ownerId)
                .getResultList();

        return licenses.stream()
                .map(LicenseSummaryView::from)
                .toList();
    }

    @Override
    public Page<LicenseSummaryView> search(LicenseSearchCond cond, Pageable pageable) {
        CriteriaBuilder cb = em.getCriteriaBuilder();

        // Count query
        CriteriaQuery<Long> countQuery = cb.createQuery(Long.class);
        Root<License> countRoot = countQuery.from(License.class);
        countQuery.select(cb.count(countRoot));
        countQuery.where(buildPredicates(cb, countRoot, cond).toArray(new Predicate[0]));
        Long total = em.createQuery(countQuery).getSingleResult();

        if (total == 0) {
            return new PageImpl<>(List.of(), pageable, 0);
        }

        // Data query
        CriteriaQuery<License> dataQuery = cb.createQuery(License.class);
        Root<License> dataRoot = dataQuery.from(License.class);
        dataRoot.fetch("activations", JoinType.LEFT);
        dataQuery.select(dataRoot).distinct(true);
        dataQuery.where(buildPredicates(cb, dataRoot, cond).toArray(new Predicate[0]));

        // Sorting
        List<Order> orders = new ArrayList<>();
        for (Sort.Order sortOrder : pageable.getSort()) {
            Path<?> path = dataRoot.get(sortOrder.getProperty());
            orders.add(sortOrder.isAscending() ? cb.asc(path) : cb.desc(path));
        }
        if (orders.isEmpty()) {
            orders.add(cb.desc(dataRoot.get("createdAt")));
        }
        dataQuery.orderBy(orders);

        TypedQuery<License> typedQuery = em.createQuery(dataQuery);
        typedQuery.setFirstResult((int) pageable.getOffset());
        typedQuery.setMaxResults(pageable.getPageSize());

        List<LicenseSummaryView> content = typedQuery.getResultList().stream()
                .map(LicenseSummaryView::from)
                .toList();

        return new PageImpl<>(content, pageable, total);
    }

    private List<Predicate> buildPredicates(CriteriaBuilder cb, Root<License> root, LicenseSearchCond cond) {
        List<Predicate> predicates = new ArrayList<>();

        if (cond.ownerType() != null) {
            predicates.add(cb.equal(root.get("ownerType"), cond.ownerType()));
        }
        if (cond.ownerId() != null) {
            predicates.add(cb.equal(root.get("ownerId"), cond.ownerId()));
        }
        if (cond.productId() != null) {
            predicates.add(cb.equal(root.get("productId"), cond.productId()));
        }
        if (cond.planId() != null) {
            predicates.add(cb.equal(root.get("planId"), cond.planId()));
        }
        if (cond.status() != null) {
            predicates.add(cb.equal(root.get("status"), cond.status()));
        }
        if (cond.licenseType() != null) {
            predicates.add(cb.equal(root.get("licenseType"), cond.licenseType()));
        }
        if (cond.usageCategory() != null) {
            predicates.add(cb.equal(root.get("usageCategory"), cond.usageCategory()));
        }
        if (cond.licenseKey() != null && !cond.licenseKey().isBlank()) {
            predicates.add(cb.like(root.get("licenseKey"), "%" + cond.licenseKey() + "%"));
        }

        return predicates;
    }
}
