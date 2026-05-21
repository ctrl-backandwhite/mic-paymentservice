package com.backandwhite.infrastructure.message.kafka.producer;

import com.backandwhite.application.port.out.PaymentEventPort;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Fallback adapter wired when Kafka is disabled (local dev / tests).
 *
 * <p>
 * Every method is intentionally a no-op — the event is silently dropped so the
 * rest of the use-case flow keeps working without a broker. Production uses
 * {@link KafkaPaymentEventAdapter} instead.
 */
@Component
@ConditionalOnProperty(name = "spring.kafka.enabled", havingValue = "false", matchIfMissing = true)
public class NoOpPaymentEventAdapter implements PaymentEventPort {

    @Override
    public void publishPaymentInitiated(String paymentId, String orderId, String userId, String amount, String currency,
            String method, String gateway) {
        // Intentionally empty: Kafka disabled, event dropped.
    }

    @Override
    public void publishPaymentConfirmed(String paymentId, String orderId, String userId, String email, String amount,
            String currency, String method, String gateway, String transactionRef) {
        // Intentionally empty: Kafka disabled, event dropped.
    }

    @Override
    public void publishPaymentFailed(String paymentId, String orderId, String userId, String email, String amount,
            String reason, String gateway) {
        // Intentionally empty: Kafka disabled, event dropped.
    }

    @Override
    public void publishRefundInitiated(String paymentId, String refundId, String orderId, String userId,
            String refundAmount, String reason) {
        // Intentionally empty: Kafka disabled, event dropped.
    }

    @Override
    public void publishRefundCompleted(String paymentId, String refundId, String orderId, String userId,
            String refundAmount) {
        // Intentionally empty: Kafka disabled, event dropped.
    }
}
