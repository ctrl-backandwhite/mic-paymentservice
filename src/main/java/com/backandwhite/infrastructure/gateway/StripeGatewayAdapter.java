package com.backandwhite.infrastructure.gateway;

import com.backandwhite.domain.gateway.PaymentGateway;
import com.backandwhite.domain.gateway.PaymentRequest;
import com.backandwhite.domain.gateway.PaymentResult;
import com.backandwhite.domain.gateway.RefundRequest;
import com.backandwhite.domain.gateway.RefundResult;
import com.backandwhite.domain.valueobject.PaymentMethod;
import com.backandwhite.infrastructure.gateway.config.PaymentGatewayProperties;
import com.stripe.StripeClient;
import com.stripe.exception.StripeException;
import com.stripe.model.Customer;
import com.stripe.model.PaymentIntent;
import com.stripe.model.Refund;
import com.stripe.param.CustomerCreateParams;
import com.stripe.param.CustomerListParams;
import com.stripe.param.PaymentIntentCreateParams;
import com.stripe.param.PaymentMethodAttachParams;
import com.stripe.param.RefundCreateParams;
import jakarta.annotation.PostConstruct;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Component;

@Log4j2
@Component
@RequiredArgsConstructor
public class StripeGatewayAdapter implements PaymentGateway {

    private final PaymentGatewayProperties props;
    private StripeClient stripeClient;

    @PostConstruct
    void init() {
        String apiKey = props.getStripe().getApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("Stripe apiKey is blank — using placeholder client; real calls will fail");
            apiKey = "sk_test_placeholder";
        }
        this.stripeClient = new StripeClient(apiKey);
    }

    @Override
    public PaymentResult process(PaymentRequest request) {
        try {
            // Validate that payment method token is provided
            if (request.getStripePaymentMethodId() == null || request.getStripePaymentMethodId().isBlank()) {
                log.error("Stripe payment failed for orderId={}: Missing stripePaymentMethodId", request.getOrderId());
                return PaymentResult.builder().success(false)
                        .errorMessage("Payment method token is required for Stripe CARD payments").build();
            }

            long amountCents = request.getAmount().multiply(BigDecimal.valueOf(100)).setScale(0, RoundingMode.HALF_UP)
                    .longValue();

            // Attach the PaymentMethod to a Customer so it can be reused beyond a
            // single PaymentIntent. Stripe rejects unattached PaymentMethods the
            // second time they are used, which is the root cause of guest/saved-
            // card double-use failures.
            String customerId = resolveOrCreateCustomer(request);
            attachPaymentMethodIfNeeded(request.getStripePaymentMethodId(), customerId, request.getOrderId());

            PaymentIntentCreateParams.Builder paramsBuilder = PaymentIntentCreateParams.builder().setAmount(amountCents)
                    .setCurrency(request.getCurrency().toLowerCase())
                    .setPaymentMethod(request.getStripePaymentMethodId()).setConfirm(true)
                    .setReturnUrl("http://localhost:9000/checkout?status=complete")
                    .putMetadata("orderId", request.getOrderId()).putMetadata("paymentId", request.getPaymentId());
            if (customerId != null) {
                paramsBuilder.setCustomer(customerId);
            }

            PaymentIntent intent = stripeClient.paymentIntents().create(paramsBuilder.build());
            boolean succeeded = "succeeded".equals(intent.getStatus());

            return PaymentResult.builder().success(succeeded).providerRef(intent.getId())
                    .providerResponse(
                            Map.of("provider", "stripe", "intentId", intent.getId(), "status", intent.getStatus()))
                    .errorMessage(succeeded
                            ? null
                            : intent.getLastPaymentError() != null
                                    ? intent.getLastPaymentError().getMessage()
                                    : "Payment failed")
                    .build();

        } catch (StripeException e) {
            log.error("Stripe payment failed for orderId={}: {}", request.getOrderId(), e.getMessage());
            return PaymentResult.builder().success(false).errorMessage(e.getMessage()).build();
        }
    }

    /**
     * Looks up a Stripe Customer by email (first match) or creates one if no
     * matching customer exists. Returns {@code null} when the caller did not supply
     * an email so guest flows keep working — in that case the PaymentIntent runs
     * without a Customer, which is fine for single-use tokens issued fresh by
     * Stripe.js in the same session.
     */
    private String resolveOrCreateCustomer(PaymentRequest request) throws StripeException {
        String email = request.getEmail();
        if (email == null || email.isBlank()) {
            return null;
        }
        List<Customer> matches = stripeClient.customers()
                .list(CustomerListParams.builder().setEmail(email).setLimit(1L).build()).getData();
        if (!matches.isEmpty()) {
            return matches.get(0).getId();
        }
        CustomerCreateParams.Builder create = CustomerCreateParams.builder().setEmail(email);
        if (request.getUserId() != null && !request.getUserId().isBlank()) {
            create.putMetadata("userId", request.getUserId());
        }
        Customer created = stripeClient.customers().create(create.build());
        log.info("::> Stripe Customer created id={} email={}", created.getId(), email);
        return created.getId();
    }

    /**
     * Attaches a PaymentMethod to a Customer. Swallows the "already attached" error
     * so repeated calls stay idempotent without an extra retrieve call.
     */
    private void attachPaymentMethodIfNeeded(String paymentMethodId, String customerId, String orderId) {
        if (customerId == null) {
            return;
        }
        try {
            stripeClient.paymentMethods().attach(paymentMethodId,
                    PaymentMethodAttachParams.builder().setCustomer(customerId).build());
        } catch (StripeException e) {
            String msg = e.getMessage() != null ? e.getMessage() : "";
            if (msg.contains("already been attached") || msg.contains("already attached")) {
                return;
            }
            log.debug("::> PM attach skipped for orderId={} pm={} reason={}", orderId, paymentMethodId, msg);
        }
    }

    @Override
    public RefundResult refund(RefundRequest request) {
        try {
            long amountCents = request.getAmount().multiply(BigDecimal.valueOf(100)).setScale(0, RoundingMode.HALF_UP)
                    .longValue();

            RefundCreateParams params = RefundCreateParams.builder().setPaymentIntent(request.getProviderRef())
                    .setAmount(amountCents).setReason(RefundCreateParams.Reason.REQUESTED_BY_CUSTOMER).build();

            Refund refund = stripeClient.refunds().create(params);
            boolean succeeded = "succeeded".equals(refund.getStatus());

            return RefundResult.builder().success(succeeded).providerRef(refund.getId())
                    .errorMessage(succeeded ? null : "Refund failed").build();

        } catch (StripeException e) {
            log.error("Stripe refund failed for paymentId={}: {}", request.getPaymentId(), e.getMessage());
            return RefundResult.builder().success(false).errorMessage(e.getMessage()).build();
        }
    }

    @Override
    public boolean supports(PaymentMethod method) {
        return method == PaymentMethod.CARD;
    }
}
