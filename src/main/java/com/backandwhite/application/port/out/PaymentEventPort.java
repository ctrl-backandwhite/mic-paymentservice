package com.backandwhite.application.port.out;

public interface PaymentEventPort {

    void publishPaymentInitiated(String paymentId, String orderId, String userId, String amount, String currency,
            String method, String gateway);

    /**
     * Publishes a {@code payment.confirmed} event. Has 9 String parameters by
     * design — every field is part of the public Kafka contract consumed by the
     * order, notification and analytics services. Refactoring to a DTO would break
     * all downstream listeners, so we suppress S107 here.
     */
    @SuppressWarnings("java:S107")
    void publishPaymentConfirmed(String paymentId, String orderId, String userId, String email, String amount,
            String currency, String method, String gateway, String transactionRef);

    void publishPaymentFailed(String paymentId, String orderId, String userId, String email, String amount,
            String reason, String gateway);

    void publishRefundInitiated(String paymentId, String refundId, String orderId, String userId, String refundAmount,
            String reason);

    void publishRefundCompleted(String paymentId, String refundId, String orderId, String userId, String refundAmount);
}
