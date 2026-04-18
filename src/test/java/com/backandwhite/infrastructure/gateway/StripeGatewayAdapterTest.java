package com.backandwhite.infrastructure.gateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.backandwhite.domain.gateway.PaymentRequest;
import com.backandwhite.domain.gateway.PaymentResult;
import com.backandwhite.domain.gateway.RefundRequest;
import com.backandwhite.domain.gateway.RefundResult;
import com.backandwhite.domain.valueobject.PaymentMethod;
import com.backandwhite.infrastructure.gateway.config.PaymentGatewayProperties;
import com.stripe.StripeClient;
import com.stripe.exception.ApiException;
import com.stripe.model.PaymentIntent;
import com.stripe.model.Refund;
import com.stripe.model.StripeError;
import com.stripe.param.PaymentIntentCreateParams;
import com.stripe.param.RefundCreateParams;
import com.stripe.service.PaymentIntentService;
import com.stripe.service.RefundService;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class StripeGatewayAdapterTest {

    private PaymentGatewayProperties props;
    private StripeGatewayAdapter adapter;
    private StripeClient mockClient;

    @BeforeEach
    void setUp() throws Exception {
        props = new PaymentGatewayProperties();
        props.getStripe().setApiKey("sk_test_xyz");
        adapter = new StripeGatewayAdapter(props);
        adapter.init();
        // swap the real client with a mock
        mockClient = mock(StripeClient.class);
        setField(adapter, "stripeClient", mockClient);
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field f = target.getClass().getDeclaredField(name);
        f.setAccessible(true);
        f.set(target, value);
    }

    @Test
    void init_blankApiKey_usesPlaceholder() {
        PaymentGatewayProperties p = new PaymentGatewayProperties();
        p.getStripe().setApiKey("   ");
        StripeGatewayAdapter a = new StripeGatewayAdapter(p);
        a.init();
        assertThat(a).isNotNull();
    }

    @Test
    void init_nullApiKey_usesPlaceholder() {
        PaymentGatewayProperties p = new PaymentGatewayProperties();
        p.getStripe().setApiKey(null);
        StripeGatewayAdapter a = new StripeGatewayAdapter(p);
        a.init();
        assertThat(a).isNotNull();
    }

    @Test
    void process_succeeded_returnsSuccess() throws Exception {
        PaymentIntentService piService = mock(PaymentIntentService.class);
        when(mockClient.paymentIntents()).thenReturn(piService);

        PaymentIntent pi = mock(PaymentIntent.class);
        when(pi.getStatus()).thenReturn("succeeded");
        when(pi.getId()).thenReturn("pi_123");
        when(piService.create(any(PaymentIntentCreateParams.class))).thenReturn(pi);

        PaymentRequest request = PaymentRequest.builder().paymentId("pay-1").orderId("ord-1").userId("u-1")
                .amount(new BigDecimal("10.00")).currency("USD").method(PaymentMethod.CARD).build();

        PaymentResult result = adapter.process(request);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getProviderRef()).isEqualTo("pi_123");
        assertThat(result.getErrorMessage()).isNull();
    }

    @Test
    void process_failedWithError_returnsFailure() throws Exception {
        PaymentIntentService piService = mock(PaymentIntentService.class);
        when(mockClient.paymentIntents()).thenReturn(piService);

        PaymentIntent pi = mock(PaymentIntent.class);
        when(pi.getStatus()).thenReturn("requires_payment_method");
        when(pi.getId()).thenReturn("pi_err");
        StripeError err = mock(StripeError.class);
        when(err.getMessage()).thenReturn("card declined");
        when(pi.getLastPaymentError()).thenReturn(err);
        when(piService.create(any(PaymentIntentCreateParams.class))).thenReturn(pi);

        PaymentRequest request = PaymentRequest.builder().paymentId("p").orderId("o").userId("u")
                .amount(new BigDecimal("10.00")).currency("usd").method(PaymentMethod.CARD).build();

        PaymentResult result = adapter.process(request);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorMessage()).isEqualTo("card declined");
    }

    @Test
    void process_failedWithoutError_defaultMessage() throws Exception {
        PaymentIntentService piService = mock(PaymentIntentService.class);
        when(mockClient.paymentIntents()).thenReturn(piService);

        PaymentIntent pi = mock(PaymentIntent.class);
        when(pi.getStatus()).thenReturn("canceled");
        when(pi.getId()).thenReturn("pi_c");
        when(pi.getLastPaymentError()).thenReturn(null);
        when(piService.create(any(PaymentIntentCreateParams.class))).thenReturn(pi);

        PaymentRequest request = PaymentRequest.builder().paymentId("p").orderId("o").userId("u")
                .amount(new BigDecimal("10.00")).currency("USD").method(PaymentMethod.CARD).build();

        PaymentResult result = adapter.process(request);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorMessage()).isEqualTo("Payment failed");
    }

    @Test
    void process_stripeException_returnsFailure() throws Exception {
        PaymentIntentService piService = mock(PaymentIntentService.class);
        when(mockClient.paymentIntents()).thenReturn(piService);
        when(piService.create(any(PaymentIntentCreateParams.class)))
                .thenThrow(new ApiException("boom", "req-1", "code", 400, null));

        PaymentRequest request = PaymentRequest.builder().paymentId("p").orderId("o").userId("u")
                .amount(new BigDecimal("10.00")).currency("USD").method(PaymentMethod.CARD).build();

        PaymentResult result = adapter.process(request);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorMessage()).contains("boom");
    }

    @Test
    void refund_succeeded_returnsSuccess() throws Exception {
        RefundService rService = mock(RefundService.class);
        when(mockClient.refunds()).thenReturn(rService);
        Refund refund = mock(Refund.class);
        when(refund.getStatus()).thenReturn("succeeded");
        when(refund.getId()).thenReturn("re_1");
        when(rService.create(any(RefundCreateParams.class))).thenReturn(refund);

        RefundRequest request = RefundRequest.builder().paymentId("p").providerRef("pi_1")
                .amount(new BigDecimal("5.00")).reason("bad").build();

        RefundResult result = adapter.refund(request);
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getProviderRef()).isEqualTo("re_1");
    }

    @Test
    void refund_failed_returnsFailure() throws Exception {
        RefundService rService = mock(RefundService.class);
        when(mockClient.refunds()).thenReturn(rService);
        Refund refund = mock(Refund.class);
        when(refund.getStatus()).thenReturn("pending");
        when(refund.getId()).thenReturn("re_x");
        when(rService.create(any(RefundCreateParams.class))).thenReturn(refund);

        RefundRequest request = RefundRequest.builder().paymentId("p").providerRef("pi_1")
                .amount(new BigDecimal("5.00")).reason("bad").build();

        RefundResult result = adapter.refund(request);
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorMessage()).isEqualTo("Refund failed");
    }

    @Test
    void refund_stripeException_returnsFailure() throws Exception {
        RefundService rService = mock(RefundService.class);
        when(mockClient.refunds()).thenReturn(rService);
        when(rService.create(any(RefundCreateParams.class)))
                .thenThrow(new ApiException("refund failed", "req", "c", 500, null));

        RefundRequest request = RefundRequest.builder().paymentId("p").providerRef("pi_1")
                .amount(new BigDecimal("5.00")).reason("bad").build();

        RefundResult result = adapter.refund(request);
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorMessage()).contains("refund failed");
    }

    @Test
    void supports_card_true() {
        assertThat(adapter.supports(PaymentMethod.CARD)).isTrue();
    }

    @Test
    void supports_paypal_false() {
        assertThat(adapter.supports(PaymentMethod.PAYPAL)).isFalse();
    }

    // Unused import guard
    @SuppressWarnings("unused")
    private StripeError unused() {
        return null;
    }
}
