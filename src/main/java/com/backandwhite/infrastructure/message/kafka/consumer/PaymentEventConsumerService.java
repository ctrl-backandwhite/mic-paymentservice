package com.backandwhite.infrastructure.message.kafka.consumer;

import com.backandwhite.application.usecase.PaymentUseCase;
import com.backandwhite.common.constants.AppConstants;
import com.backandwhite.common.domain.valueobject.Money;
import com.backandwhite.core.kafka.avro.OrderCreatedEvent;
import com.backandwhite.core.kafka.avro.OrderReturnApprovedEvent;
import com.backandwhite.domain.model.Payment;
import com.backandwhite.domain.repository.PaymentRepository;
import java.math.BigDecimal;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

/**
 * Consumes order events relevant to the payment service. Listens for
 * order.created for analytics and order.return.approved for auto-refund.
 */
@Log4j2
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "spring.kafka.enabled", havingValue = "true")
public class PaymentEventConsumerService {

    private final PaymentUseCase paymentUseCase;
    private final PaymentRepository paymentRepository;

    @KafkaListener(topics = AppConstants.KAFKA_TOPIC_ORDER_CREATED, groupId = AppConstants.KAFKA_GROUP_PAYMENT, containerFactory = "avroKafkaListenerContainerFactory")
    public void onOrderCreated(OrderCreatedEvent event) {
        log.info("::> Received order.created in payment-service: orderId={}, amount={}, status={}",
                str(event.getOrderId()), str(event.getTotalAmount()), str(event.getStatus()));
    }

    @KafkaListener(topics = AppConstants.KAFKA_TOPIC_ORDER_RETURN_APPROVED, groupId = AppConstants.KAFKA_GROUP_PAYMENT, containerFactory = "avroKafkaListenerContainerFactory")
    public void onReturnApproved(OrderReturnApprovedEvent event) {
        String orderId = str(event.getOrderId());
        String refundAmountStr = str(event.getRefundAmount());
        log.info("::> Received order.return.approved in payment-service: orderId={}, refundAmount={}", orderId,
                refundAmountStr);
        try {
            Payment payment = paymentRepository.findByOrderId(orderId).orElse(null);
            if (payment == null) {
                log.warn("::> No payment found for orderId={}, skipping auto-refund", orderId);
                return;
            }
            BigDecimal refundAmount = refundAmountStr != null
                    ? new BigDecimal(refundAmountStr)
                    : payment.getAmount().getAmount();
            paymentUseCase.refundPayment(payment.getId(), Money.of(refundAmount),
                    "Auto-refund for approved return on order " + orderId);
            log.info("::> Auto-refund processed for orderId={}, paymentId={}, amount={}", orderId, payment.getId(),
                    refundAmount);
        } catch (Exception e) {
            log.error("::> Auto-refund failed for orderId={}: {}", orderId, e.getMessage(), e);
        }
    }

    private String str(CharSequence cs) {
        return cs != null ? cs.toString() : null;
    }
}
