package com.backandwhite.domain.gateway;

import com.backandwhite.domain.valueobject.PaymentMethod;
import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentRequest {

    private String paymentId;
    private String orderId;
    private String userId;
    private String email;
    private BigDecimal amount;
    private String currency;
    private PaymentMethod method;
    private String idempotencyKey;

    /**
     * Stripe payment method token (pm_xxxx) created by Stripe.js on the frontend.
     * Required for CARD payments in production.
     *
     * When present: Use real Stripe gateway with this payment method. When absent:
     * Fall back to mock mode to avoid "PaymentIntent missing payment method"
     * errors.
     */
    private String stripePaymentMethodId;
}
