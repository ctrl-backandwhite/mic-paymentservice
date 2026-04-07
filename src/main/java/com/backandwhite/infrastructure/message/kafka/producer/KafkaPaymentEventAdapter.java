package com.backandwhite.infrastructure.message.kafka.producer;

import com.backandwhite.common.constants.AppConstants;
import com.backandwhite.core.kafka.avro.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.avro.specific.SpecificRecord;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import com.backandwhite.application.port.out.PaymentEventPort;

import java.time.Instant;

@Log4j2
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "spring.kafka.enabled", havingValue = "true")
public class KafkaPaymentEventAdapter implements PaymentEventPort {

    private final KafkaTemplate<String, SpecificRecord> kafkaTemplate;

    public void publishPaymentInitiated(String paymentId, String orderId, String userId,
            String amount, String currency, String method, String gateway) {
        PaymentInitiatedEvent event = PaymentInitiatedEvent.newBuilder()
                .setPaymentId(paymentId)
                .setOrderId(orderId)
                .setUserId(userId)
                .setEmail(null)
                .setAmount(amount)
                .setCurrency(currency)
                .setMethod(method)
                .setGateway(gateway)
                .setTimestamp(now())
                .build();
        send(AppConstants.KAFKA_TOPIC_PAYMENT_INITIATED, orderId, event);
    }

    public void publishPaymentConfirmed(String paymentId, String orderId, String userId,
            String email, String amount, String currency, String method,
            String gateway, String transactionRef) {
        PaymentConfirmedEvent event = PaymentConfirmedEvent.newBuilder()
                .setPaymentId(paymentId)
                .setOrderId(orderId)
                .setUserId(userId)
                .setEmail(email)
                .setAmount(amount)
                .setCurrency(currency)
                .setMethod(method)
                .setGateway(gateway)
                .setTransactionRef(transactionRef)
                .setTimestamp(now())
                .build();
        send(AppConstants.KAFKA_TOPIC_PAYMENT_CONFIRMED, orderId, event);
    }

    public void publishPaymentFailed(String paymentId, String orderId, String userId,
            String email, String amount, String reason, String gateway) {
        PaymentFailedEvent event = PaymentFailedEvent.newBuilder()
                .setPaymentId(paymentId)
                .setOrderId(orderId)
                .setUserId(userId)
                .setEmail(email)
                .setAmount(amount)
                .setReason(reason)
                .setGateway(gateway)
                .setTimestamp(now())
                .build();
        send(AppConstants.KAFKA_TOPIC_PAYMENT_FAILED, orderId, event);
    }

    public void publishRefundInitiated(String paymentId, String refundId, String orderId,
            String userId, String refundAmount, String reason) {
        PaymentRefundInitiatedEvent event = PaymentRefundInitiatedEvent.newBuilder()
                .setPaymentId(paymentId)
                .setRefundId(refundId)
                .setOrderId(orderId)
                .setUserId(userId)
                .setEmail(null)
                .setRefundAmount(refundAmount)
                .setReason(reason)
                .setTimestamp(now())
                .build();
        send(AppConstants.KAFKA_TOPIC_PAYMENT_REFUND_INITIATED, orderId, event);
    }

    public void publishRefundCompleted(String paymentId, String refundId, String orderId,
            String userId, String refundAmount) {
        PaymentRefundCompletedEvent event = PaymentRefundCompletedEvent.newBuilder()
                .setPaymentId(paymentId)
                .setRefundId(refundId)
                .setOrderId(orderId)
                .setUserId(userId)
                .setEmail(null)
                .setRefundAmount(refundAmount)
                .setTimestamp(now())
                .build();
        send(AppConstants.KAFKA_TOPIC_PAYMENT_REFUND_COMPLETED, orderId, event);
    }

    // ── Common ───────────────────────────────────────────────────────────────

    private void send(String topic, String key, SpecificRecord event) {
        log.info("::> Publishing to [{}] key={}: {}", topic, key, event.getClass().getSimpleName());
        kafkaTemplate.send(topic, key, event).whenComplete((result, ex) -> {
            if (ex != null) {
                log.error("::> Failed to publish to [{}]: {}", topic, ex.getMessage(), ex);
            } else {
                log.debug("::> Published to [{}] offset={}",
                        topic, result.getRecordMetadata().offset());
            }
        });
    }

    private String now() {
        return Instant.now().toString();
    }
}
