package com.backandwhite.infrastructure.db.postgres.specification;

import com.backandwhite.infrastructure.db.postgres.entity.PaymentEntity;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.Map;

public final class PaymentSpecification {

    private PaymentSpecification() {
    }

    public static Specification<PaymentEntity> fromFilters(Map<String, Object> filters) {
        return (root, query, cb) -> {
            Predicate predicate = cb.conjunction();

            if (filters == null || filters.isEmpty()) {
                return predicate;
            }

            if (filters.containsKey("search")) {
                String like = "%" + filters.get("search").toString().toLowerCase() + "%";
                predicate = cb.and(predicate, cb.or(
                        cb.like(cb.lower(root.get("orderId")), like),
                        cb.like(cb.lower(root.get("userId")), like),
                        cb.like(cb.lower(root.get("providerRef")), like),
                        cb.like(cb.lower(root.get("idempotencyKey")), like)));
            }

            if (filters.containsKey("status")) {
                predicate = cb.and(predicate,
                        cb.equal(root.get("status").as(String.class), filters.get("status").toString()));
            }

            if (filters.containsKey("paymentMethod")) {
                predicate = cb.and(predicate,
                        cb.equal(root.get("paymentMethod").as(String.class), filters.get("paymentMethod").toString()));
            }

            if (filters.containsKey("userId")) {
                predicate = cb.and(predicate,
                        cb.equal(root.get("userId"), filters.get("userId").toString()));
            }

            if (filters.containsKey("orderId")) {
                predicate = cb.and(predicate,
                        cb.equal(root.get("orderId"), filters.get("orderId").toString()));
            }

            return predicate;
        };
    }
}
