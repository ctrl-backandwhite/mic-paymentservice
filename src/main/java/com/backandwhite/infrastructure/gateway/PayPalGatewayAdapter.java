package com.backandwhite.infrastructure.gateway;

import com.backandwhite.domain.gateway.PaymentGateway;
import com.backandwhite.domain.gateway.PaymentRequest;
import com.backandwhite.domain.gateway.PaymentResult;
import com.backandwhite.domain.gateway.RefundRequest;
import com.backandwhite.domain.gateway.RefundResult;
import com.backandwhite.domain.valueobject.PaymentMethod;
import com.backandwhite.infrastructure.client.paypal.PayPalTokenManager;
import com.backandwhite.infrastructure.gateway.config.PaymentGatewayProperties;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Log4j2
@Component
@RequiredArgsConstructor
public class PayPalGatewayAdapter implements PaymentGateway {

    private final PayPalTokenManager tokenManager;
    private final PaymentGatewayProperties props;

    @Override
    @SuppressWarnings("unchecked")
    public PaymentResult process(PaymentRequest request) {
        try {
            String token = tokenManager.getAccessToken();
            String baseUrl = props.getPaypal().getBaseUrl();
            RestClient client = RestClient.create();

            String amountStr = request.getAmount().setScale(2, RoundingMode.HALF_UP).toPlainString();

            Map<String, Object> body = Map.of("intent", "CAPTURE", "purchase_units",
                    List.of(Map.of("reference_id", request.getOrderId(), "amount",
                            Map.of("currency_code", request.getCurrency().toUpperCase(), "value", amountStr))));

            Map<String, Object> orderResp = client.post().uri(baseUrl + "/v2/checkout/orders")
                    .header("Authorization", "Bearer " + token).header("Content-Type", "application/json")
                    .header("PayPal-Request-Id", request.getPaymentId()).body(body).retrieve().body(Map.class);

            if (orderResp == null) {
                throw new IllegalStateException("Empty PayPal order response");
            }

            String orderId = (String) orderResp.get("id");

            Map<String, Object> captureResp = client.post().uri(baseUrl + "/v2/checkout/orders/" + orderId + "/capture")
                    .header("Authorization", "Bearer " + token).header("Content-Type", "application/json")
                    .body(Map.of()).retrieve().body(Map.class);

            if (captureResp == null) {
                throw new IllegalStateException("Empty PayPal capture response");
            }

            String status = (String) captureResp.get("status");
            boolean completed = "COMPLETED".equals(status);

            return PaymentResult.builder().success(completed).providerRef(orderId)
                    .providerResponse(Map.of("provider", "paypal", "orderId", orderId, "status", status))
                    .errorMessage(completed ? null : "PayPal capture status: " + status).build();

        } catch (RestClientException e) {
            log.error("PayPal payment failed for orderId={}: {}", request.getOrderId(), e.getMessage());
            return PaymentResult.builder().success(false).errorMessage(e.getMessage()).build();
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public RefundResult refund(RefundRequest request) {
        try {
            String token = tokenManager.getAccessToken();
            String baseUrl = props.getPaypal().getBaseUrl();
            RestClient client = RestClient.create();

            String amountStr = request.getAmount().setScale(2, RoundingMode.HALF_UP).toPlainString();

            Map<String, Object> body = Map.of("amount", Map.of("value", amountStr, "currency_code", "USD"),
                    "note_to_payer", request.getReason() != null ? request.getReason() : "Refund");

            Map<String, Object> resp = client.post()
                    .uri(baseUrl + "/v2/payments/captures/" + request.getProviderRef() + "/refund")
                    .header("Authorization", "Bearer " + token).header("Content-Type", "application/json").body(body)
                    .retrieve().body(Map.class);

            if (resp == null) {
                throw new IllegalStateException("Empty PayPal refund response");
            }

            String status = (String) resp.get("status");
            return RefundResult.builder().success("COMPLETED".equals(status)).providerRef((String) resp.get("id"))
                    .errorMessage("COMPLETED".equals(status) ? null : "Refund status: " + status).build();

        } catch (RestClientException e) {
            log.error("PayPal refund failed for paymentId={}: {}", request.getPaymentId(), e.getMessage());
            return RefundResult.builder().success(false).errorMessage(e.getMessage()).build();
        }
    }

    @Override
    public boolean supports(PaymentMethod method) {
        return method == PaymentMethod.PAYPAL;
    }
}
