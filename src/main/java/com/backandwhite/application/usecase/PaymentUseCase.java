package com.backandwhite.application.usecase;

import com.backandwhite.common.domain.model.PageResult;
import com.backandwhite.common.domain.valueobject.Money;
import com.backandwhite.domain.model.Payment;
import com.backandwhite.domain.model.PaymentRefund;
import com.backandwhite.domain.valueobject.PaymentMethod;
import java.util.Map;

public interface PaymentUseCase {

    default Payment processPayment(String orderId, String userId, String email, Money amount, String currency,
            PaymentMethod method, String idempotencyKey) {
        return processPayment(orderId, userId, email, amount, currency, method, idempotencyKey, null);
    }

    Payment processPayment(String orderId, String userId, String email, Money amount, String currency,
            PaymentMethod method, String idempotencyKey, String stripePaymentMethodId);

    Payment findById(String id);

    Payment findByOrderId(String orderId);

    PageResult<Payment> findByUserId(String userId, int page, int size, String sortBy, boolean ascending);

    PageResult<Payment> findAll(Map<String, Object> filters, int page, int size, String sortBy, boolean ascending);

    PaymentRefund refundPayment(String paymentId, Money amount, String reason);

    PageResult<PaymentRefund> findRefunds(String paymentId, int page, int size, String sortBy, boolean ascending);

    Payment createCryptoPayment(String orderId, String userId, Money amount, String currency, PaymentMethod method);

    Payment verifyCryptoPayment(String paymentId);

    void handleWebhook(String provider, String payload, String signature);

    /**
     * Two-step PayPal flow — step 1. Persists the Payment in PROCESSING, creates
     * the PayPal checkout order, and returns the provider order id plus the approve
     * URL. The frontend opens the PayPal buttons popup against that order id; once
     * the buyer approves, call {@link #capturePayPalPayment}.
     */
    PayPalInitiation initiatePayPalPayment(String orderId, String userId, String email, Money amount, String currency,
            String idempotencyKey);

    /**
     * Two-step PayPal flow — step 2. Captures the already-approved order, updates
     * the Payment row and publishes {@code payment.confirmed} (or
     * {@code payment.failed}).
     */
    Payment capturePayPalPayment(String paypalOrderId);

    /**
     * Output of {@link #initiatePayPalPayment}.
     */
    record PayPalInitiation(String paymentId, String paypalOrderId, String approveUrl) {
    }
}
