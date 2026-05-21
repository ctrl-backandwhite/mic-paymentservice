package com.backandwhite.infrastructure.message.kafka.consumer;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.backandwhite.application.usecase.PaymentUseCase;
import com.backandwhite.common.domain.valueobject.Money;
import com.backandwhite.core.kafka.avro.OrderCreatedEvent;
import com.backandwhite.core.kafka.avro.OrderReturnApprovedEvent;
import com.backandwhite.domain.model.Payment;
import com.backandwhite.domain.repository.PaymentRepository;
import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PaymentEventConsumerServiceTest {

    @Mock
    private PaymentUseCase paymentUseCase;

    @Mock
    private PaymentRepository paymentRepository;

    @InjectMocks
    private PaymentEventConsumerService consumer;

    private OrderCreatedEvent orderCreatedEvent(String orderId, String amount, String status) {
        return OrderCreatedEvent.newBuilder().setOrderId(orderId).setUserId("u1").setEmail("e@e.com")
                .setOrderReference("NX-1").setTotalAmount(amount).setCurrency("USD").setStatus(status).setItemCount(1)
                .setShippingAddressId("addr-1").setTimestamp("2026-01-01T00:00:00Z").build();
    }

    private OrderReturnApprovedEvent returnApprovedEvent(String orderId, String refundAmount) {
        // Use all-args constructor so we can pass null for refundAmount (Avro builder
        // would reject null on a required field).
        return new OrderReturnApprovedEvent(orderId, "rr-1", "u1", "e@e.com", "NX-1", refundAmount,
                "2026-01-01T00:00:00Z");
    }

    @Test
    void onOrderCreated_logsPayloadWithoutSideEffects() {
        OrderCreatedEvent event = orderCreatedEvent("o1", "100.00", "DRAFT");

        assertThatCode(() -> consumer.onOrderCreated(event)).doesNotThrowAnyException();

        verify(paymentRepository, never()).findByOrderId(anyString());
    }

    @Test
    void onReturnApproved_paymentFound_invokesAutoRefundWithExplicitAmount() {
        Payment payment = Payment.builder().id("pay-1").orderId("o1").userId("u1")
                .amount(Money.of(new BigDecimal("100.00"))).build();
        when(paymentRepository.findByOrderId("o1")).thenReturn(Optional.of(payment));

        OrderReturnApprovedEvent event = returnApprovedEvent("o1", "25.00");

        consumer.onReturnApproved(event);

        ArgumentCaptor<Money> moneyCap = ArgumentCaptor.forClass(Money.class);
        verify(paymentUseCase).refundPayment(eq("pay-1"), moneyCap.capture(), anyString());
        org.assertj.core.api.Assertions.assertThat(moneyCap.getValue().getAmount())
                .isEqualByComparingTo(new BigDecimal("25.00"));
    }

    @Test
    void onReturnApproved_paymentFoundWithNullRefund_fallsBackToPaymentAmount() {
        Payment payment = Payment.builder().id("pay-2").orderId("o2").userId("u1")
                .amount(Money.of(new BigDecimal("80.00"))).build();
        when(paymentRepository.findByOrderId("o2")).thenReturn(Optional.of(payment));

        OrderReturnApprovedEvent event = returnApprovedEvent("o2", null);

        consumer.onReturnApproved(event);

        ArgumentCaptor<Money> moneyCap = ArgumentCaptor.forClass(Money.class);
        verify(paymentUseCase).refundPayment(eq("pay-2"), moneyCap.capture(), anyString());
        org.assertj.core.api.Assertions.assertThat(moneyCap.getValue().getAmount())
                .isEqualByComparingTo(new BigDecimal("80.00"));
    }

    @Test
    void onReturnApproved_noPaymentForOrder_skipsRefund() {
        when(paymentRepository.findByOrderId("o3")).thenReturn(Optional.empty());

        OrderReturnApprovedEvent event = returnApprovedEvent("o3", "10.00");

        consumer.onReturnApproved(event);

        verify(paymentUseCase, never()).refundPayment(anyString(), any(Money.class), anyString());
    }

    @Test
    void onReturnApproved_useCaseThrows_isCaughtAndLogged() {
        Payment payment = Payment.builder().id("pay-3").orderId("o4").userId("u1")
                .amount(Money.of(new BigDecimal("50.00"))).build();
        when(paymentRepository.findByOrderId("o4")).thenReturn(Optional.of(payment));
        when(paymentUseCase.refundPayment(anyString(), any(Money.class), anyString()))
                .thenThrow(new RuntimeException("refund failed"));

        OrderReturnApprovedEvent event = returnApprovedEvent("o4", "50.00");

        assertThatCode(() -> consumer.onReturnApproved(event)).doesNotThrowAnyException();
    }
}
