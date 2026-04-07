package com.backandwhite.infrastructure.gateway;

import com.backandwhite.domain.gateway.PaymentGateway;
import com.backandwhite.domain.gateway.PaymentRequest;
import com.backandwhite.domain.gateway.PaymentResult;
import com.backandwhite.domain.gateway.RefundRequest;
import com.backandwhite.domain.gateway.RefundResult;
import com.backandwhite.domain.valueobject.PaymentMethod;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

@Log4j2
@Component
public class StripeGatewayAdapter implements PaymentGateway {

    @Override
    public PaymentResult process(PaymentRequest request) {
        log.info("Processing Stripe payment for order={} amount={} {}",
                request.getOrderId(), request.getAmount(), request.getCurrency());

        // TODO: Integrate with Stripe API — currently returns mock success
        String chargeId = "ch_" + UUID.randomUUID().toString().replace("-", "").substring(0, 24);

        return PaymentResult.builder()
                .success(true)
                .providerRef(chargeId)
                .providerResponse(Map.of(
                        "provider", "stripe",
                        "chargeId", chargeId,
                        "status", "succeeded"))
                .build();
    }

    @Override
    public RefundResult refund(RefundRequest request) {
        log.info("Processing Stripe refund for paymentId={} amount={}",
                request.getPaymentId(), request.getAmount());

        String refundId = "re_" + UUID.randomUUID().toString().replace("-", "").substring(0, 24);

        return RefundResult.builder()
                .success(true)
                .providerRef(refundId)
                .build();
    }

    @Override
    public boolean supports(PaymentMethod method) {
        return method == PaymentMethod.CARD;
    }
}
