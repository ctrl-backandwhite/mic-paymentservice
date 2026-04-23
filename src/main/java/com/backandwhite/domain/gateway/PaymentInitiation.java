package com.backandwhite.domain.gateway;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Outcome of a two-step gateway flow where the buyer has to approve the charge
 * on the provider's UI before the merchant can capture funds. Used by PayPal's
 * Checkout v2 flow: {@code initiate} returns the provider order id and an
 * approve URL, the buyer authorizes in the PayPal popup, and the merchant
 * finishes with {@code capture}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentInitiation {

    private boolean success;
    private String providerRef;
    private String approveUrl;
    private String errorMessage;
}
