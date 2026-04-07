package com.backandwhite.application.usecase;

import com.backandwhite.common.domain.model.PageResult;
import com.backandwhite.domain.model.Payment;
import com.backandwhite.domain.model.PaymentRefund;
import com.backandwhite.domain.valueobject.PaymentMethod;

import java.math.BigDecimal;
import java.util.Map;

public interface PaymentUseCase {

        Payment processPayment(String orderId, String userId, String email, BigDecimal amount,
                                                        String currency, PaymentMethod method, String idempotencyKey);

        Payment findById(String id);

        Payment findByOrderId(String orderId);

        PageResult<Payment> findByUserId(String userId, int page, int size,
                                                        String sortBy, boolean ascending);

        PageResult<Payment> findAll(Map<String, Object> filters, int page, int size,
                                                        String sortBy, boolean ascending);

        PaymentRefund refundPayment(String paymentId, BigDecimal amount, String reason);

        PageResult<PaymentRefund> findRefunds(String paymentId, int page, int size,
                                                        String sortBy, boolean ascending);

        Payment createCryptoPayment(String orderId, String userId, BigDecimal amount,
                                                        String currency, PaymentMethod method);

        Payment verifyCryptoPayment(String paymentId);

        void handleWebhook(String provider, String payload, String signature);
}
