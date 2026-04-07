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
public class PayPalGatewayAdapter implements PaymentGateway {

    @Override
    public PaymentResult process(PaymentRequest request) {
        log.info("Processing PayPal payment for order={} amount={} {}",
                request.getOrderId(), request.getAmount(), request.getCurrency());

        // TODO: Integrate with PayPal API — currently returns mock success
        String captureId = "PAYID-" + UUID.randomUUID().toString().replace("-", "").substring(0, 20).toUpperCase();

        return PaymentResult.builder()
                .success(true)
                .providerRef(captureId)
                .providerResponse(Map.of(
                        "provider", "paypal",
                        "captureId", captureId,
                        "status", "COMPLETED"))
                .build();
    }

    @Override
    public RefundResult refund(RefundRequest request) {
        log.info("Processing PayPal refund for paymentId={} amount={}",
                request.getPaymentId(), request.getAmount());

        String refundId = "REFUND-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16).toUpperCase();

        return RefundResult.builder()
                .success(true)
                .providerRef(refundId)
                .build();
    }

    @Override
    public boolean supports(PaymentMethod method) {
        return method == PaymentMethod.PAYPAL;
    }
}
