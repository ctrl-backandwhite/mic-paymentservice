package com.backandwhite.infrastructure.db.postgres.specification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.backandwhite.infrastructure.db.postgres.entity.PaymentEntity;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import org.springframework.data.jpa.domain.Specification;

class PaymentSpecificationTest {

    private Root<PaymentEntity> root;
    private CriteriaQuery<?> query;
    private CriteriaBuilder cb;

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() {
        root = mock(Root.class, Answers.RETURNS_DEEP_STUBS);
        query = mock(CriteriaQuery.class);
        cb = mock(CriteriaBuilder.class, Answers.RETURNS_DEEP_STUBS);
    }

    private Predicate run(Map<String, Object> filters) {
        Specification<PaymentEntity> spec = PaymentSpecification.fromFilters(filters);
        return spec.toPredicate(root, query, cb);
    }

    @Test
    void fromFilters_nullMap_returnsConjunction() {
        assertThat(run(null)).isNotNull();
    }

    @Test
    void fromFilters_emptyMap_returnsConjunction() {
        assertThat(run(new HashMap<>())).isNotNull();
    }

    @Test
    void fromFilters_search_executesLikeBranch() {
        assertThat(run(Map.of("search", "ABC"))).isNotNull();
    }

    @Test
    void fromFilters_status_executesEqualBranch() {
        assertThat(run(Map.of("status", "COMPLETED"))).isNotNull();
    }

    @Test
    void fromFilters_paymentMethod_executesEqualBranch() {
        assertThat(run(Map.of("paymentMethod", "CARD"))).isNotNull();
    }

    @Test
    void fromFilters_userId_executesEqualBranch() {
        assertThat(run(Map.of("userId", "u1"))).isNotNull();
    }

    @Test
    void fromFilters_orderId_executesEqualBranch() {
        assertThat(run(Map.of("orderId", "o1"))).isNotNull();
    }

    @Test
    void fromFilters_allFilters_combined() {
        Map<String, Object> filters = Map.of("search", "abc", "status", "COMPLETED", "paymentMethod", "CARD", "userId",
                "u1", "orderId", "o1");
        assertThat(run(filters)).isNotNull();
    }
}
