package com.backandwhite.infrastructure.db.postgres.repository.impl;

import com.backandwhite.domain.model.Payment;
import com.backandwhite.domain.model.PaymentRefund;
import com.backandwhite.domain.repository.PaymentRepository;
import com.backandwhite.infrastructure.db.postgres.entity.PaymentEntity;
import com.backandwhite.infrastructure.db.postgres.entity.PaymentRefundEntity;
import com.backandwhite.infrastructure.db.postgres.mapper.PaymentInfraMapper;
import com.backandwhite.infrastructure.db.postgres.repository.PaymentJpaRepository;
import com.backandwhite.infrastructure.db.postgres.repository.PaymentRefundJpaRepository;
import com.backandwhite.infrastructure.db.postgres.specification.PaymentSpecification;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PaymentRepositoryImpl implements PaymentRepository {

    private final PaymentJpaRepository jpaRepository;
    private final PaymentRefundJpaRepository refundJpaRepository;
    private final PaymentInfraMapper mapper;

    @Override
    public Payment save(Payment payment) {
        PaymentEntity entity = mapper.toEntity(payment);
        entity.setId(UUID.randomUUID().toString());
        return mapper.toDomain(jpaRepository.save(entity));
    }

    @Override
    public Payment update(Payment payment) {
        return mapper.toDomain(jpaRepository.save(mapper.toEntity(payment)));
    }

    @Override
    public Optional<Payment> findById(String id) {
        return jpaRepository.findById(id).map(mapper::toDomain);
    }

    @Override
    public Optional<Payment> findByIdempotencyKey(String idempotencyKey) {
        return jpaRepository.findByIdempotencyKey(idempotencyKey).map(mapper::toDomain);
    }

    @Override
    public Optional<Payment> findByOrderId(String orderId) {
        return jpaRepository.findByOrderId(orderId).map(mapper::toDomain);
    }

    @Override
    public Page<Payment> findByUserId(String userId, Pageable pageable) {
        return jpaRepository.findByUserId(userId, pageable).map(mapper::toDomain);
    }

    @Override
    public Page<Payment> findAll(Map<String, Object> filters, Pageable pageable) {
        Specification<PaymentEntity> spec = PaymentSpecification.fromFilters(filters);
        return jpaRepository.findAll(spec, pageable).map(mapper::toDomain);
    }

    @Override
    public PaymentRefund saveRefund(PaymentRefund refund) {
        PaymentRefundEntity entity = mapper.toRefundEntity(refund);
        entity.setId(UUID.randomUUID().toString());
        return mapper.toRefundDomain(refundJpaRepository.save(entity));
    }

    @Override
    public PaymentRefund updateRefund(PaymentRefund refund) {
        return mapper.toRefundDomain(refundJpaRepository.save(mapper.toRefundEntity(refund)));
    }

    @Override
    public Page<PaymentRefund> findRefundsByPaymentId(String paymentId, Pageable pageable) {
        return refundJpaRepository.findByPaymentId(paymentId, pageable).map(mapper::toRefundDomain);
    }
}
