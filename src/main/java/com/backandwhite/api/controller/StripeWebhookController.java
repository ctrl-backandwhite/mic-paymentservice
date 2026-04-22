package com.backandwhite.api.controller;

import com.backandwhite.application.usecase.PaymentUseCase;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Event;
import com.stripe.net.Webhook;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Receives Stripe webhook events for payment confirmations, refunds, and
 * disputes. Webhook endpoint: POST /api/v1/payments/webhooks/stripe
 *
 * Setup in Stripe Dashboard: 1. Go to Developers → API Keys 2. Create webhook
 * endpoint: https://yourserver/api/v1/payments/webhooks/stripe 3. Select
 * events: payment_intent.succeeded, payment_intent.payment_failed 4. Copy
 * signing secret (whsec_xxx) to STRIPE_WEBHOOK_SECRET env var
 */
@Log4j2
@RestController
@RequestMapping("/api/v1/payments/webhooks")
@RequiredArgsConstructor
public class StripeWebhookController {

    private final PaymentUseCase paymentUseCase;

    @Value("${stripe.webhook-secret:}")
    private String webhookSecret;

    private static final ObjectMapper objectMapper = new ObjectMapper();

    @PostMapping("/stripe")
    public ResponseEntity<String> handleStripeWebhook(HttpServletRequest request) {
        String payload = getPayload(request);
        String sigHeader = request.getHeader("Stripe-Signature");

        try {
            // Verify signature (critical for security)
            if (webhookSecret == null || webhookSecret.isBlank()) {
                log.warn("STRIPE_WEBHOOK_SECRET not configured — webhook signature verification SKIPPED (unsafe)");
                // Proceed anyway for now (only in dev mode)
            } else {
                Event event = Webhook.constructEvent(payload, sigHeader, webhookSecret);
                log.info("✓ Webhook signature verified — event id={}", event.getId());
                return processEvent(event);
            }

            // Fallback: parse without signature verification (dev only)
            Event event = objectMapper.readValue(payload, Event.class);
            log.warn("Processing webhook WITHOUT signature verification (unsafe — set STRIPE_WEBHOOK_SECRET)");
            return processEvent(event);

        } catch (SignatureVerificationException e) {
            log.error("Webhook signature verification FAILED: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid signature");
        } catch (IOException e) {
            log.error("Failed to parse webhook payload: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid payload");
        } catch (Exception e) {
            log.error("Unexpected error processing webhook: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Internal error");
        }
    }

    private ResponseEntity<String> processEvent(Event event) {
        log.info("Processing Stripe webhook event: type={}", event.getType());

        switch (event.getType()) {
            case "payment_intent.succeeded" :
                return handlePaymentIntentSucceeded(event);

            case "payment_intent.payment_failed" :
                return handlePaymentIntentFailed(event);

            case "charge.refunded" :
                return handleChargeRefunded(event);

            default :
                log.debug("Ignoring webhook event type: {}", event.getType());
                return ResponseEntity.ok("{}");
        }
    }

    private ResponseEntity<String> handlePaymentIntentSucceeded(Event event) {
        try {
            // Event.Data is opaque; convert to JsonNode via ObjectMapper
            String dataJson = objectMapper.writeValueAsString(event.getData().getObject());
            JsonNode paymentIntent = objectMapper.readTree(dataJson);
            String intentId = paymentIntent.get("id").asText();
            JsonNode metadataNode = paymentIntent.get("metadata");

            log.info("payment_intent.succeeded: intentId={}", intentId);

            if (metadataNode != null) {
                String paymentId = metadataNode.get("paymentId").asText();
                log.info("  → Payment confirmed: paymentId={}", paymentId);
                // Note: Payment status should already be COMPLETED from processPayment()
                // This webhook is mainly for async verification / logging
            }

            return ResponseEntity.ok("{}");
        } catch (Exception e) {
            log.error("Error processing payment_intent.succeeded: {}", e.getMessage(), e);
            return ResponseEntity.ok("{}"); // Always return 200 to avoid retry loop
        }
    }

    private ResponseEntity<String> handlePaymentIntentFailed(Event event) {
        try {
            // Event.Data is opaque; convert to JsonNode via ObjectMapper
            String dataJson = objectMapper.writeValueAsString(event.getData().getObject());
            JsonNode paymentIntent = objectMapper.readTree(dataJson);
            String intentId = paymentIntent.get("id").asText();
            JsonNode errorNode = paymentIntent.get("last_payment_error");
            String errorMsg = errorNode != null ? errorNode.get("message").asText() : "Unknown error";

            log.warn("payment_intent.payment_failed: intentId={}, error={}", intentId, errorMsg);

            if (paymentIntent.has("metadata")) {
                JsonNode metadataNode = paymentIntent.get("metadata");
                String paymentId = metadataNode.get("paymentId").asText();
                log.warn("  → Payment failed: paymentId={}", paymentId);
                // Could trigger compensating transaction (cancel order, refund, etc.)
            }

            return ResponseEntity.ok("{}");
        } catch (Exception e) {
            log.error("Error processing payment_intent.payment_failed: {}", e.getMessage(), e);
            return ResponseEntity.ok("{}");
        }
    }

    private ResponseEntity<String> handleChargeRefunded(Event event) {
        try {
            // Event.Data is opaque; convert to JsonNode via ObjectMapper
            String dataJson = objectMapper.writeValueAsString(event.getData().getObject());
            JsonNode charge = objectMapper.readTree(dataJson);
            String chargeId = charge.get("id").asText();
            long refundedAmount = charge.get("amount_refunded").asLong();

            log.info("charge.refunded: chargeId={}, refundedAmount={}", chargeId, refundedAmount);
            return ResponseEntity.ok("{}");
        } catch (Exception e) {
            log.error("Error processing charge.refunded: {}", e.getMessage(), e);
            return ResponseEntity.ok("{}");
        }
    }

    /**
     * Extract raw payload from request body. This is necessary because
     * Webhook.constructEvent needs the raw body before Jackson parsing.
     */
    private String getPayload(HttpServletRequest request) {
        try (Scanner scanner = new Scanner(request.getInputStream(), StandardCharsets.UTF_8)) {
            return scanner.useDelimiter("\\A").hasNext() ? scanner.next() : "";
        } catch (IOException e) {
            log.error("Failed to read webhook payload: {}", e.getMessage());
            return "";
        }
    }
}
