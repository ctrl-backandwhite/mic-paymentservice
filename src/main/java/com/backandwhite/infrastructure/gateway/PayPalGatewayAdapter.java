package com.backandwhite.infrastructure.gateway;

import com.backandwhite.domain.gateway.PaymentGateway;
import com.backandwhite.domain.gateway.PaymentInitiation;
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
    public PaymentResult process(PaymentRequest request) {
        // Legacy one-shot flow retained for callers that pre-approved the order
        // out of band. The standard in-page buyer flow is `initiate` + `capture`.
        PaymentInitiation init = initiate(request);
        if (!init.isSuccess()) {
            return PaymentResult.builder().success(false).errorMessage(init.getErrorMessage()).build();
        }
        return capture(request, init.getProviderRef());
    }

    @Override
    @SuppressWarnings("unchecked")
    public PaymentInitiation initiate(PaymentRequest request) {
        try {
            String token = tokenManager.getAccessToken();
            String baseUrl = props.getPaypal().getBaseUrl();
            RestClient client = RestClient.create();

            String amountStr = request.getAmount().setScale(2, RoundingMode.HALF_UP).toPlainString();

            String returnBase = props.getPaypal().getReturnBaseUrl();
            Map<String, Object> body = Map.of("intent", "CAPTURE", "purchase_units",
                    List.of(Map.of("reference_id", request.getOrderId(), "amount",
                            Map.of("currency_code", request.getCurrency().toUpperCase(), "value", amountStr))),
                    "application_context",
                    Map.of("return_url", returnBase + "/checkout/paypal-return", "cancel_url",
                            returnBase + "/checkout?paypalCancelled=1", "user_action", "PAY_NOW", "shipping_preference",
                            "NO_SHIPPING"));

            Map<String, Object> orderResp = client.post().uri(baseUrl + "/v2/checkout/orders")
                    .header("Authorization", "Bearer " + token).header("Content-Type", "application/json")
                    .header("PayPal-Request-Id", request.getPaymentId()).body(body).retrieve().body(Map.class);

            if (orderResp == null) {
                return PaymentInitiation.builder().success(false).errorMessage("Empty PayPal order response").build();
            }

            String orderId = (String) orderResp.get("id");
            String approveUrl = extractApproveUrl(orderResp);
            return PaymentInitiation.builder().success(true).providerRef(orderId).approveUrl(approveUrl).build();

        } catch (RestClientException e) {
            log.error("PayPal initiate failed for orderId={}: {}", request.getOrderId(), e.getMessage());
            return PaymentInitiation.builder().success(false).errorMessage(e.getMessage()).build();
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public PaymentResult capture(PaymentRequest request, String providerRef) {
        try {
            String token = tokenManager.getAccessToken();
            String baseUrl = props.getPaypal().getBaseUrl();
            RestClient client = RestClient.create();

            Map<String, Object> captureResp = client.post()
                    .uri(baseUrl + "/v2/checkout/orders/" + providerRef + "/capture")
                    .header("Authorization", "Bearer " + token).header("Content-Type", "application/json")
                    .body(Map.of()).retrieve().body(Map.class);

            if (captureResp == null) {
                throw new IllegalStateException("Empty PayPal capture response");
            }

            String status = (String) captureResp.get("status");
            boolean completed = "COMPLETED".equals(status);
            return PaymentResult.builder().success(completed).providerRef(providerRef)
                    .providerResponse(Map.of("provider", "paypal", "orderId", providerRef, "status", status))
                    .errorMessage(completed ? null : "PayPal capture status: " + status).build();

        } catch (RestClientException e) {
            log.error("PayPal capture failed for paypalOrderId={}: {}", providerRef, e.getMessage());
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

    @SuppressWarnings("unchecked")
    private static String extractApproveUrl(Map<String, Object> orderResp) {
        Object rawLinks = orderResp.get("links");
        if (!(rawLinks instanceof List<?> links)) {
            return null;
        }
        for (Object link : links) {
            if (link instanceof Map<?, ?> m && "approve".equals(m.get("rel"))) {
                Object href = m.get("href");
                return href != null ? href.toString() : null;
            }
        }
        return null;
    }
}
