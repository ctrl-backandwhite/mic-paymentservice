package com.backandwhite.application.port.out;

public interface PaymentEventPort {

    void publishPaymentInitiated(String paymentId, String orderId, String userId, String amount, String currency,
            String method, String gateway);

    void publishPaymentConfirmed(String paymentId, String orderId, String userId, String email, String amount,
            String currency, String method, String gateway, String transactionRef);

    void publishPaymentFailed(String paymentId, String orderId, String userId, String email, String amount,
            String reason, String gateway);

    void publishRefundInitiated(String paymentId, String refundId, String orderId, String userId, String refundAmount,
            String reason);

    void publishRefundCompleted(String paymentId, String refundId, String orderId, String userId, String refundAmount);
}
