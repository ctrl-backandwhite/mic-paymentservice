package com.backandwhite.domain.gateway;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.backandwhite.domain.valueobject.PaymentMethod;
import org.junit.jupiter.api.Test;

/**
 * Drives the default methods on {@link PaymentGateway} so the interface's
 * fallback implementations are exercised.
 */
class PaymentGatewayTest {

    /**
     * Minimal stub gateway that does not override {@code initiate}/{@code capture}.
     */
    static final class StubGateway implements PaymentGateway {
        @Override
        public PaymentResult process(PaymentRequest request) {
            return PaymentResult.builder().success(true).build();
        }

        @Override
        public RefundResult refund(RefundRequest request) {
            return RefundResult.builder().success(true).build();
        }

        @Override
        public boolean supports(PaymentMethod method) {
            return true;
        }
    }

    @Test
    void defaultInitiate_throwsUnsupported() {
        StubGateway g = new StubGateway();
        PaymentRequest req = PaymentRequest.builder().paymentId("p").build();
        assertThatThrownBy(() -> g.initiate(req)).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void defaultCapture_throwsUnsupported() {
        StubGateway g = new StubGateway();
        PaymentRequest req = PaymentRequest.builder().paymentId("p").build();
        assertThatThrownBy(() -> g.capture(req, "ref")).isInstanceOf(UnsupportedOperationException.class);
    }
}
