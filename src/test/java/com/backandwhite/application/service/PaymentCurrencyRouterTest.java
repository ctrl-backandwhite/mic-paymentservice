package com.backandwhite.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.backandwhite.domain.valueobject.PaymentMethod;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PaymentCurrencyRouterTest {

    private PaymentCurrencyRouter router;

    @BeforeEach
    void setUp() {
        router = new PaymentCurrencyRouter();
    }

    @Test
    void resolveSettlementCurrency_card_returnsUsd() {
        assertThat(router.resolveSettlementCurrency(PaymentMethod.CARD)).isEqualTo("USD");
    }

    @Test
    void resolveSettlementCurrency_paypal_returnsUsd() {
        assertThat(router.resolveSettlementCurrency(PaymentMethod.PAYPAL)).isEqualTo("USD");
    }

    @Test
    void resolveSettlementCurrency_usdt_returnsUsdt() {
        assertThat(router.resolveSettlementCurrency(PaymentMethod.USDT)).isEqualTo("USDT");
    }

    @Test
    void resolveSettlementCurrency_btc_returnsBtc() {
        assertThat(router.resolveSettlementCurrency(PaymentMethod.BTC)).isEqualTo("BTC");
    }

    @Test
    void requiresConversion_sameCurrency_returnsFalse() {
        assertThat(router.requiresConversion("USD", PaymentMethod.PAYPAL)).isFalse();
    }

    @Test
    void requiresConversion_differentCurrency_returnsTrue() {
        assertThat(router.requiresConversion("EUR", PaymentMethod.PAYPAL)).isTrue();
    }

    @Test
    void requiresConversion_caseInsensitive() {
        assertThat(router.requiresConversion("usd", PaymentMethod.CARD)).isFalse();
    }

    @Test
    void requiresConversion_cryptoBtc_differentDisplay_true() {
        assertThat(router.requiresConversion("USD", PaymentMethod.BTC)).isTrue();
    }
}
