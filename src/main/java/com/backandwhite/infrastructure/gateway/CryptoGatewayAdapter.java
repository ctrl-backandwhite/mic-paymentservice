package com.backandwhite.infrastructure.gateway;

import com.backandwhite.domain.gateway.PaymentGateway;
import com.backandwhite.domain.gateway.PaymentRequest;
import com.backandwhite.domain.gateway.PaymentResult;
import com.backandwhite.domain.gateway.RefundRequest;
import com.backandwhite.domain.gateway.RefundResult;
import com.backandwhite.domain.valueobject.PaymentMethod;
import com.backandwhite.infrastructure.client.coinbase.CoinbaseCommerceClient;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;

@Log4j2
@Component
@RequiredArgsConstructor
public class CryptoGatewayAdapter implements PaymentGateway {

    private final CoinbaseCommerceClient coinbaseClient;

    @Override
    @SuppressWarnings("unchecked")
    public PaymentResult process(PaymentRequest request) {
        try {
            String network = request.getMethod() == PaymentMethod.BTC ? "bitcoin" : "usdc";
            Map<String, Object> charge = coinbaseClient.createCharge(request.getOrderId(), request.getPaymentId(),
                    request.getAmount(), request.getCurrency(), network);

            String code = (String) charge.get("code");
            Map<String, Object> addresses = (Map<String, Object>) charge.get("addresses");
            String address = addresses != null ? (String) addresses.get(network) : null;
            String hostedUrl = (String) charge.get("hosted_url");

            return PaymentResult.builder().success(true).providerRef(code).cryptoAddress(address).qrCodeUrl(hostedUrl)
                    .providerResponse(Map.of("provider", "coinbase", "chargeCode", code, "network", network, "address",
                            address != null ? address : ""))
                    .build();

        } catch (RestClientException e) {
            log.error("Coinbase Commerce charge creation failed for orderId={}: {}", request.getOrderId(),
                    e.getMessage());
            return PaymentResult.builder().success(false).errorMessage(e.getMessage()).build();
        }
    }

    @Override
    public RefundResult refund(RefundRequest request) {
        log.warn("Crypto refunds require manual processing for paymentId={}", request.getPaymentId());
        return RefundResult.builder().success(false)
                .errorMessage("Crypto refunds require manual processing via Coinbase Commerce dashboard").build();
    }

    @Override
    public boolean supports(PaymentMethod method) {
        return method == PaymentMethod.USDT || method == PaymentMethod.BTC;
    }
}
