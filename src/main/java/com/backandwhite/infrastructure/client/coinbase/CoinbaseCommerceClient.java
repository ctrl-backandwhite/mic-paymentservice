package com.backandwhite.infrastructure.client.coinbase;

import com.backandwhite.infrastructure.gateway.config.PaymentGatewayProperties;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Log4j2
@Component
@RequiredArgsConstructor
public class CoinbaseCommerceClient {

    private static final String CC_VERSION = "2018-03-22";
    private final PaymentGatewayProperties props;

    @SuppressWarnings("unchecked")
    public Map<String, Object> createCharge(String orderId, String paymentId, BigDecimal amount, String currency,
            String cryptoNetwork) {
        RestClient client = buildClient();
        String amountStr = amount.setScale(2, RoundingMode.HALF_UP).toPlainString();

        Map<String, Object> body = Map.of("name", "Order " + orderId, "description", "Payment for order " + orderId,
                "pricing_type", "fixed_price", "local_price",
                Map.of("amount", amountStr, "currency", currency.toUpperCase()), "metadata",
                Map.of("orderId", orderId, "paymentId", paymentId));

        Map<String, Object> response = client.post().uri("/charges").body(body).retrieve().body(Map.class);

        if (response == null) {
            throw new IllegalStateException("Empty Coinbase Commerce create charge response");
        }
        return (Map<String, Object>) response.get("data");
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> getCharge(String chargeCode) {
        RestClient client = buildClient();
        Map<String, Object> response = client.get().uri("/charges/" + chargeCode).retrieve().body(Map.class);
        if (response == null) {
            throw new IllegalStateException("Empty Coinbase Commerce get charge response");
        }
        return (Map<String, Object>) response.get("data");
    }

    private RestClient buildClient() {
        return RestClient.builder().baseUrl(props.getCrypto().getCoinbase().getBaseUrl())
                .defaultHeader("X-CC-Api-Key", props.getCrypto().getCoinbase().getApiKey())
                .defaultHeader("X-CC-Version", CC_VERSION).defaultHeader("Content-Type", "application/json").build();
    }
}
