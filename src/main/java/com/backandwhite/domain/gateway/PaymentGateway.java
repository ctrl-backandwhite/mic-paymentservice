package com.backandwhite.domain.gateway;

import com.backandwhite.domain.valueobject.PaymentMethod;

public interface PaymentGateway {

    PaymentResult process(PaymentRequest request);

    RefundResult refund(RefundRequest request);

    boolean supports(PaymentMethod method);

    /**
     * Two-step flow: create the provider-side order so the buyer can approve it.
     * Default implementation: gateways that don't require a buyer-approval
     * round-trip throw to let the caller fall back to {@link #process}.
     */
    default PaymentInitiation initiate(PaymentRequest request) {
        throw new UnsupportedOperationException("Gateway does not support two-step initiate");
    }

    /**
     * Two-step flow: capture an already-approved provider order.
     */
    default PaymentResult capture(PaymentRequest request, String providerRef) {
        throw new UnsupportedOperationException("Gateway does not support two-step capture");
    }
}
