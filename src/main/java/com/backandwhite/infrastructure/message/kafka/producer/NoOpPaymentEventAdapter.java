package com.backandwhite.infrastructure.message.kafka.producer;

import com.backandwhite.application.port.out.PaymentEventPort;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "spring.kafka.enabled", havingValue = "false", matchIfMissing = true)
public class NoOpPaymentEventAdapter implements PaymentEventPort {

    @Override
    public void publishPaymentInitiated(String paymentId, String orderId, String userId,
            String amount, String currency, String method, String gateway) {
    }

    @Override
    public void publishPaymentConfirmed(String paymentId, String orderId, String userId,
            String email, String amount, String currency, String method,
            String gateway, String transactionRef) {
    }

    @Override
    public void publishPaymentFailed(String paymentId, String orderId, String userId,
            String email, String amount, String reason, String gateway) {
    }

    @Override
    public void publishRefundInitiated(String paymentId, String refundId, String orderId,
            String userId, String refundAmount, String reason) {
    }

    @Override
    public void publishRefundCompleted(String paymentId, String refundId, String orderId,
            String userId, String refundAmount) {
    }
}
