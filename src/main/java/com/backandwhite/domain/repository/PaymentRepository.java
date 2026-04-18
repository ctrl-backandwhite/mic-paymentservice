package com.backandwhite.domain.repository;

import com.backandwhite.domain.model.Payment;
import com.backandwhite.domain.model.PaymentRefund;
import java.util.Map;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface PaymentRepository {

    Payment save(Payment payment);

    Payment update(Payment payment);

    Optional<Payment> findById(String id);

    Optional<Payment> findByIdempotencyKey(String idempotencyKey);

    Optional<Payment> findByOrderId(String orderId);

    Page<Payment> findByUserId(String userId, Pageable pageable);

    Page<Payment> findAll(Map<String, Object> filters, Pageable pageable);

    PaymentRefund saveRefund(PaymentRefund refund);

    PaymentRefund updateRefund(PaymentRefund refund);

    Page<PaymentRefund> findRefundsByPaymentId(String paymentId, Pageable pageable);
}
