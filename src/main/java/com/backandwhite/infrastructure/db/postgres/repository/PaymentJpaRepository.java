package com.backandwhite.infrastructure.db.postgres.repository;

import com.backandwhite.infrastructure.db.postgres.entity.PaymentEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;

public interface PaymentJpaRepository extends JpaRepository<PaymentEntity, String>,
        JpaSpecificationExecutor<PaymentEntity> {

    Optional<PaymentEntity> findByIdempotencyKey(String idempotencyKey);

    Optional<PaymentEntity> findByOrderId(String orderId);

    Page<PaymentEntity> findByUserId(String userId, Pageable pageable);
}
