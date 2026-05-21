package com.backandwhite.infrastructure.message.kafka.producer;

import static org.assertj.core.api.Assertions.assertThatCode;

import org.junit.jupiter.api.Test;

class NoOpPaymentEventAdapterTest {

    private final NoOpPaymentEventAdapter adapter = new NoOpPaymentEventAdapter();

    @Test
    void publishPaymentInitiated_doesNothing() {
        assertThatCode(() -> adapter.publishPaymentInitiated("p", "o", "u", "10", "USD", "CARD", "STRIPE"))
                .doesNotThrowAnyException();
    }

    @Test
    void publishPaymentConfirmed_doesNothing() {
        assertThatCode(() -> adapter.publishPaymentConfirmed("p", "o", "u", "e", "10", "USD", "CARD", "STRIPE", "tx"))
                .doesNotThrowAnyException();
    }

    @Test
    void publishPaymentFailed_doesNothing() {
        assertThatCode(() -> adapter.publishPaymentFailed("p", "o", "u", "e", "10", "reason", "STRIPE"))
                .doesNotThrowAnyException();
    }

    @Test
    void publishRefundInitiated_doesNothing() {
        assertThatCode(() -> adapter.publishRefundInitiated("p", "r", "o", "u", "10", "reason"))
                .doesNotThrowAnyException();
    }

    @Test
    void publishRefundCompleted_doesNothing() {
        assertThatCode(() -> adapter.publishRefundCompleted("p", "r", "o", "u", "10")).doesNotThrowAnyException();
    }
}
