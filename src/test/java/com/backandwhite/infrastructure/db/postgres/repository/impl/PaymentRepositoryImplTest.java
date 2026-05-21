package com.backandwhite.infrastructure.db.postgres.repository.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.backandwhite.common.domain.valueobject.Money;
import com.backandwhite.domain.model.Payment;
import com.backandwhite.domain.model.PaymentRefund;
import com.backandwhite.domain.valueobject.PaymentMethod;
import com.backandwhite.domain.valueobject.PaymentStatus;
import com.backandwhite.infrastructure.db.postgres.entity.PaymentEntity;
import com.backandwhite.infrastructure.db.postgres.entity.PaymentRefundEntity;
import com.backandwhite.infrastructure.db.postgres.mapper.PaymentInfraMapper;
import com.backandwhite.infrastructure.db.postgres.repository.PaymentJpaRepository;
import com.backandwhite.infrastructure.db.postgres.repository.PaymentRefundJpaRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

@ExtendWith(MockitoExtension.class)
class PaymentRepositoryImplTest {

    @Mock
    private PaymentJpaRepository jpaRepository;

    @Mock
    private PaymentRefundJpaRepository refundJpaRepository;

    @Mock
    private PaymentInfraMapper mapper;

    @InjectMocks
    private PaymentRepositoryImpl repository;

    private Payment payment() {
        return Payment.builder().id("p1").orderId("o1").userId("u1").amount(Money.of(new BigDecimal("10.00")))
                .currency("USD").status(PaymentStatus.PROCESSING).paymentMethod(PaymentMethod.CARD).build();
    }

    private PaymentEntity entity() {
        PaymentEntity e = new PaymentEntity();
        e.setId("p1");
        return e;
    }

    private PaymentRefund refund() {
        return PaymentRefund.builder().id("r1").paymentId("p1").amount(Money.of(new BigDecimal("5.00"))).build();
    }

    private PaymentRefundEntity refundEntity() {
        PaymentRefundEntity e = new PaymentRefundEntity();
        e.setId("r1");
        return e;
    }

    @Test
    void save_assignsRandomId() {
        Payment p = payment();
        PaymentEntity e = entity();
        when(mapper.toEntity(p)).thenReturn(e);
        when(jpaRepository.save(any(PaymentEntity.class))).thenAnswer(i -> i.getArgument(0));
        when(mapper.toDomain(any(PaymentEntity.class))).thenReturn(p);

        Payment result = repository.save(p);
        assertThat(result).isSameAs(p);
        assertThat(e.getId()).isNotBlank();
    }

    @Test
    void update_keepsId() {
        Payment p = payment();
        PaymentEntity e = entity();
        when(mapper.toEntity(p)).thenReturn(e);
        when(jpaRepository.save(e)).thenReturn(e);
        when(mapper.toDomain(e)).thenReturn(p);

        assertThat(repository.update(p)).isSameAs(p);
    }

    @Test
    void findById_present() {
        PaymentEntity e = entity();
        Payment p = payment();
        when(jpaRepository.findById("p1")).thenReturn(Optional.of(e));
        when(mapper.toDomain(e)).thenReturn(p);
        assertThat(repository.findById("p1")).contains(p);
    }

    @Test
    void findById_empty() {
        when(jpaRepository.findById("x")).thenReturn(Optional.empty());
        assertThat(repository.findById("x")).isEmpty();
    }

    @Test
    void findByIdempotencyKey_present() {
        when(jpaRepository.findByIdempotencyKey("k1")).thenReturn(Optional.of(entity()));
        when(mapper.toDomain(any(PaymentEntity.class))).thenReturn(payment());
        assertThat(repository.findByIdempotencyKey("k1")).isPresent();
    }

    @Test
    void findByIdempotencyKey_empty() {
        when(jpaRepository.findByIdempotencyKey("x")).thenReturn(Optional.empty());
        assertThat(repository.findByIdempotencyKey("x")).isEmpty();
    }

    @Test
    void findByOrderId_present() {
        when(jpaRepository.findByOrderId("o1")).thenReturn(Optional.of(entity()));
        when(mapper.toDomain(any(PaymentEntity.class))).thenReturn(payment());
        assertThat(repository.findByOrderId("o1")).isPresent();
    }

    @Test
    void findByOrderId_empty() {
        when(jpaRepository.findByOrderId("x")).thenReturn(Optional.empty());
        assertThat(repository.findByOrderId("x")).isEmpty();
    }

    @Test
    void findByProviderRef_present() {
        when(jpaRepository.findByProviderRef("pi_1")).thenReturn(Optional.of(entity()));
        when(mapper.toDomain(any(PaymentEntity.class))).thenReturn(payment());
        assertThat(repository.findByProviderRef("pi_1")).isPresent();
    }

    @Test
    void findByProviderRef_empty() {
        when(jpaRepository.findByProviderRef("x")).thenReturn(Optional.empty());
        assertThat(repository.findByProviderRef("x")).isEmpty();
    }

    @Test
    void findByUserId_paginated() {
        Pageable pageable = PageRequest.of(0, 10);
        Page<PaymentEntity> page = new PageImpl<>(List.of(entity()));
        when(jpaRepository.findByUserId(eq("u1"), any(Pageable.class))).thenReturn(page);
        when(mapper.toDomain(any(PaymentEntity.class))).thenReturn(payment());

        Page<Payment> result = repository.findByUserId("u1", pageable);
        assertThat(result.getContent()).hasSize(1);
    }

    @SuppressWarnings("unchecked")
    @Test
    void findAll_withFilters() {
        Pageable pageable = PageRequest.of(0, 10);
        Page<PaymentEntity> page = new PageImpl<>(List.of(entity()));
        when(jpaRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(page);
        when(mapper.toDomain(any(PaymentEntity.class))).thenReturn(payment());

        Map<String, Object> filters = Map.of("status", "PROCESSING");
        Page<Payment> result = repository.findAll(filters, pageable);
        assertThat(result.getContent()).hasSize(1);
    }

    @Test
    void saveRefund_assignsRandomId() {
        PaymentRefund r = refund();
        PaymentRefundEntity e = refundEntity();
        when(mapper.toRefundEntity(r)).thenReturn(e);
        when(refundJpaRepository.save(any(PaymentRefundEntity.class))).thenAnswer(i -> i.getArgument(0));
        when(mapper.toRefundDomain(any(PaymentRefundEntity.class))).thenReturn(r);

        PaymentRefund result = repository.saveRefund(r);
        assertThat(result).isSameAs(r);
        assertThat(e.getId()).isNotBlank();
    }

    @Test
    void updateRefund_keepsId() {
        PaymentRefund r = refund();
        PaymentRefundEntity e = refundEntity();
        when(mapper.toRefundEntity(r)).thenReturn(e);
        when(refundJpaRepository.save(e)).thenReturn(e);
        when(mapper.toRefundDomain(e)).thenReturn(r);

        assertThat(repository.updateRefund(r)).isSameAs(r);
    }

    @Test
    void findRefundsByPaymentId_paginated() {
        Pageable pageable = PageRequest.of(0, 5);
        Page<PaymentRefundEntity> page = new PageImpl<>(List.of(refundEntity()));
        when(refundJpaRepository.findByPaymentId(eq("p1"), any(Pageable.class))).thenReturn(page);
        when(mapper.toRefundDomain(any(PaymentRefundEntity.class))).thenReturn(refund());

        Page<PaymentRefund> result = repository.findRefundsByPaymentId("p1", pageable);
        assertThat(result.getContent()).hasSize(1);
    }
}
