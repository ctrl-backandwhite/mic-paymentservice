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
public class CryptoGatewayAdapter implements PaymentGateway {

    @Override
    public PaymentResult process(PaymentRequest request) {
        log.info("Creating crypto payment for order={} amount={} {} method={}",
                request.getOrderId(), request.getAmount(), request.getCurrency(), request.getMethod());

        // TODO: Integrate with crypto payment processor — currently returns mock
        // address
        String address = request.getMethod() == PaymentMethod.BTC
                ? "bc1q" + UUID.randomUUID().toString().replace("-", "").substring(0, 30)
                : "0x" + UUID.randomUUID().toString().replace("-", "").substring(0, 40);

        String txRef = "CRYPTO-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16).toUpperCase();

        return PaymentResult.builder()
                .success(true)
                .providerRef(txRef)
                .cryptoAddress(address)
                .qrCodeUrl("https://api.qrserver.com/v1/create-qr-code/?data=" + address)
                .providerResponse(Map.of(
                        "provider", "crypto",
                        "network", request.getMethod().name(),
                        "address", address,
                        "txRef", txRef))
                .build();
    }

    @Override
    public RefundResult refund(RefundRequest request) {
        log.warn("Crypto refunds are not automatically processed — manual intervention required for paymentId={}",
                request.getPaymentId());

        return RefundResult.builder()
                .success(false)
                .errorMessage("Crypto refunds require manual processing")
                .build();
    }

    @Override
    public boolean supports(PaymentMethod method) {
        return method == PaymentMethod.USDT || method == PaymentMethod.BTC;
    }
}
