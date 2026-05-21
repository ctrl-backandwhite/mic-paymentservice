package com.backandwhite.infrastructure.db.postgres.specification;

import com.backandwhite.infrastructure.db.postgres.entity.PaymentEntity;
import jakarta.persistence.criteria.Predicate;
import java.util.Map;
import org.springframework.data.jpa.domain.Specification;

public final class PaymentSpecification {

    private static final String ORDER_ID = "orderId";
    private static final String USER_ID = "userId";
    private static final String STATUS = "status";
    private static final String PAYMENT_METHOD = "paymentMethod";
    private static final String SEARCH = "search";
    private static final String PROVIDER_REF = "providerRef";
    private static final String IDEMPOTENCY_KEY = "idempotencyKey";

    private PaymentSpecification() {
    }

    public static Specification<PaymentEntity> fromFilters(Map<String, Object> filters) {
        return (root, query, cb) -> {
            Predicate predicate = cb.conjunction();

            if (filters == null || filters.isEmpty()) {
                return predicate;
            }

            if (filters.containsKey(SEARCH)) {
                String like = "%" + filters.get(SEARCH).toString().toLowerCase() + "%";
                predicate = cb.and(predicate,
                        cb.or(cb.like(cb.lower(root.get(ORDER_ID)), like), cb.like(cb.lower(root.get(USER_ID)), like),
                                cb.like(cb.lower(root.get(PROVIDER_REF)), like),
                                cb.like(cb.lower(root.get(IDEMPOTENCY_KEY)), like)));
            }

            if (filters.containsKey(STATUS)) {
                predicate = cb.and(predicate,
                        cb.equal(root.get(STATUS).as(String.class), filters.get(STATUS).toString()));
            }

            if (filters.containsKey(PAYMENT_METHOD)) {
                predicate = cb.and(predicate,
                        cb.equal(root.get(PAYMENT_METHOD).as(String.class), filters.get(PAYMENT_METHOD).toString()));
            }

            if (filters.containsKey(USER_ID)) {
                predicate = cb.and(predicate, cb.equal(root.get(USER_ID), filters.get(USER_ID).toString()));
            }

            if (filters.containsKey(ORDER_ID)) {
                predicate = cb.and(predicate, cb.equal(root.get(ORDER_ID), filters.get(ORDER_ID).toString()));
            }

            return predicate;
        };
    }
}
