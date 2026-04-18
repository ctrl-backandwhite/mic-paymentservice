package com.backandwhite.application.service;

import com.backandwhite.domain.valueobject.PaymentMethod;
import org.springframework.stereotype.Service;

/**
 * Determines the internal settlement currency for a given payment method.
 * <p>
 * Business rules:
 * <ul>
 * <li>CARD → settle in USDT (Stripe charges converted to USDT)</li>
 * <li>PAYPAL → settle in USD (PayPal native currency)</li>
 * <li>USDT → settle in USDT (direct crypto)</li>
 * <li>BTC → settle in BTC (direct crypto)</li>
 * </ul>
 */
@Service
public class PaymentCurrencyRouter {

    /**
     * Resolve the settlement currency for a payment method.
     *
     * @param method
     *            the payment method chosen by the user
     * @return ISO currency code or crypto symbol for settlement
     */
    public String resolveSettlementCurrency(PaymentMethod method) {
        return switch (method) {
            case CARD -> "USDT";
            case PAYPAL -> "USD";
            case USDT -> "USDT";
            case BTC -> "BTC";
        };
    }

    /**
     * Whether the settlement currency differs from the display currency, meaning an
     * exchange-rate conversion is required.
     */
    public boolean requiresConversion(String displayCurrency, PaymentMethod method) {
        String settlement = resolveSettlementCurrency(method);
        return !settlement.equalsIgnoreCase(displayCurrency);
    }
}
