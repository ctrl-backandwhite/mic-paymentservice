package com.backandwhite.infrastructure.gateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.backandwhite.domain.gateway.PaymentRequest;
import com.backandwhite.domain.gateway.PaymentResult;
import com.backandwhite.domain.gateway.RefundRequest;
import com.backandwhite.domain.gateway.RefundResult;
import com.backandwhite.domain.valueobject.PaymentMethod;
import com.backandwhite.infrastructure.client.coinbase.CoinbaseCommerceClient;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestClientException;

@ExtendWith(MockitoExtension.class)
class CryptoGatewayAdapterTest {

    @Mock
    private CoinbaseCommerceClient coinbaseClient;

    @InjectMocks
    private CryptoGatewayAdapter adapter;

    @Test
    void process_btc_buildsChargeAndReturnsSuccess() {
        Map<String, Object> addresses = new HashMap<>();
        addresses.put("bitcoin", "bc1q-address");
        Map<String, Object> charge = Map.of("code", "CHRG1", "addresses", addresses, "hosted_url",
                "https://host/charge");
        when(coinbaseClient.createCharge(anyString(), anyString(), any(BigDecimal.class), anyString(), eq("bitcoin")))
                .thenReturn(charge);

        PaymentRequest request = PaymentRequest.builder().paymentId("p1").orderId("o1").userId("u1")
                .amount(new BigDecimal("0.005")).currency("BTC").method(PaymentMethod.BTC).build();

        PaymentResult result = adapter.process(request);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getProviderRef()).isEqualTo("CHRG1");
        assertThat(result.getCryptoAddress()).isEqualTo("bc1q-address");
        assertThat(result.getQrCodeUrl()).isEqualTo("https://host/charge");
        assertThat(result.getProviderResponse()).containsEntry("network", "bitcoin");
    }

    @Test
    void process_usdt_usesUsdcNetwork() {
        Map<String, Object> addresses = new HashMap<>();
        addresses.put("usdc", "0xabc");
        Map<String, Object> charge = Map.of("code", "CHRG2", "addresses", addresses, "hosted_url", "https://host/2");
        when(coinbaseClient.createCharge(anyString(), anyString(), any(BigDecimal.class), anyString(), eq("usdc")))
                .thenReturn(charge);

        PaymentRequest request = PaymentRequest.builder().paymentId("p1").orderId("o1").userId("u1")
                .amount(new BigDecimal("25.00")).currency("USDT").method(PaymentMethod.USDT).build();

        PaymentResult result = adapter.process(request);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getCryptoAddress()).isEqualTo("0xabc");
    }

    @Test
    void process_nullAddresses_returnsSuccessWithNullAddress() {
        Map<String, Object> charge = new HashMap<>();
        charge.put("code", "CHRG3");
        charge.put("addresses", null);
        charge.put("hosted_url", "https://host/3");
        when(coinbaseClient.createCharge(anyString(), anyString(), any(BigDecimal.class), anyString(), anyString()))
                .thenReturn(charge);

        PaymentRequest request = PaymentRequest.builder().paymentId("p1").orderId("o1").userId("u1")
                .amount(new BigDecimal("10")).currency("USDT").method(PaymentMethod.USDT).build();

        PaymentResult result = adapter.process(request);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getCryptoAddress()).isNull();
    }

    @Test
    void process_restClientException_returnsFailure() {
        when(coinbaseClient.createCharge(anyString(), anyString(), any(BigDecimal.class), anyString(), anyString()))
                .thenThrow(new RestClientException("boom"));
        PaymentRequest request = PaymentRequest.builder().paymentId("p1").orderId("o1").userId("u1")
                .amount(new BigDecimal("10")).currency("USDT").method(PaymentMethod.USDT).build();

        PaymentResult result = adapter.process(request);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorMessage()).isEqualTo("boom");
    }

    @Test
    void refund_returnsFailureWithManualProcessingMessage() {
        RefundRequest request = RefundRequest.builder().paymentId("p1").providerRef("ref").amount(BigDecimal.TEN)
                .reason("r").build();
        RefundResult result = adapter.refund(request);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorMessage()).contains("manual");
    }

    @Test
    void supports_usdt_true() {
        assertThat(adapter.supports(PaymentMethod.USDT)).isTrue();
    }

    @Test
    void supports_btc_true() {
        assertThat(adapter.supports(PaymentMethod.BTC)).isTrue();
    }

    @Test
    void supports_card_false() {
        assertThat(adapter.supports(PaymentMethod.CARD)).isFalse();
    }
}
