package com.backandwhite.infrastructure.gateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
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
import com.stripe.model.Customer;
import com.stripe.model.CustomerCollection;
import com.stripe.model.PaymentIntent;
import com.stripe.model.Refund;
import com.stripe.model.StripeError;
import com.stripe.param.CustomerCreateParams;
import com.stripe.param.CustomerListParams;
import com.stripe.param.PaymentIntentCreateParams;
import com.stripe.param.PaymentMethodAttachParams;
import com.stripe.param.RefundCreateParams;
import com.stripe.service.CustomerService;
import com.stripe.service.PaymentIntentService;
import com.stripe.service.PaymentMethodService;
import com.stripe.service.RefundService;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
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
                .stripePaymentMethodId("pm_test_123").amount(new BigDecimal("10.00")).currency("USD")
                .method(PaymentMethod.CARD).build();

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
                .stripePaymentMethodId("pm_test_123").amount(new BigDecimal("10.00")).currency("usd")
                .method(PaymentMethod.CARD).build();

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
                .stripePaymentMethodId("pm_test_123").amount(new BigDecimal("10.00")).currency("USD")
                .method(PaymentMethod.CARD).build();

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
                .stripePaymentMethodId("pm_test_123").amount(new BigDecimal("10.00")).currency("USD")
                .method(PaymentMethod.CARD).build();

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

    @Test
    void process_missingPaymentMethodId_returnsFailure() {
        PaymentRequest request = PaymentRequest.builder().paymentId("p").orderId("o").userId("u")
                .stripePaymentMethodId(null).amount(new BigDecimal("10")).currency("USD").method(PaymentMethod.CARD)
                .build();
        PaymentResult result = adapter.process(request);
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorMessage()).contains("Payment method token is required");
    }

    @Test
    void process_blankPaymentMethodId_returnsFailure() {
        PaymentRequest request = PaymentRequest.builder().paymentId("p").orderId("o").userId("u")
                .stripePaymentMethodId("   ").amount(new BigDecimal("10")).currency("USD").method(PaymentMethod.CARD)
                .build();
        PaymentResult result = adapter.process(request);
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorMessage()).contains("Payment method token is required");
    }

    @Test
    void process_withEmail_resolvesExistingCustomerAndAttaches() throws Exception {
        // payment intent service
        PaymentIntentService piService = mock(PaymentIntentService.class);
        when(mockClient.paymentIntents()).thenReturn(piService);
        PaymentIntent pi = mock(PaymentIntent.class);
        when(pi.getStatus()).thenReturn("succeeded");
        when(pi.getId()).thenReturn("pi_existing");
        when(piService.create(any(PaymentIntentCreateParams.class))).thenReturn(pi);

        // existing customer match
        CustomerService cs = mock(CustomerService.class);
        when(mockClient.customers()).thenReturn(cs);
        CustomerCollection coll = mock(CustomerCollection.class);
        com.stripe.model.Customer existing = mock(com.stripe.model.Customer.class);
        when(existing.getId()).thenReturn("cus_existing");
        when(coll.getData()).thenReturn(List.of(existing));
        when(cs.list(any(CustomerListParams.class))).thenReturn(coll);

        // payment method attach (success)
        PaymentMethodService pmService = mock(PaymentMethodService.class);
        when(mockClient.paymentMethods()).thenReturn(pmService);
        when(pmService.attach(anyString(), any(PaymentMethodAttachParams.class)))
                .thenReturn(mock(com.stripe.model.PaymentMethod.class));

        PaymentRequest request = PaymentRequest.builder().paymentId("p").orderId("o").userId("u")
                .email("buyer@example.com").stripePaymentMethodId("pm_x").amount(new BigDecimal("12.34"))
                .currency("USD").method(PaymentMethod.CARD).build();

        PaymentResult result = adapter.process(request);
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getProviderRef()).isEqualTo("pi_existing");
    }

    @Test
    void process_withEmail_createsNewCustomerWhenNotFound() throws Exception {
        PaymentIntentService piService = mock(PaymentIntentService.class);
        when(mockClient.paymentIntents()).thenReturn(piService);
        PaymentIntent pi = mock(PaymentIntent.class);
        when(pi.getStatus()).thenReturn("succeeded");
        when(pi.getId()).thenReturn("pi_new");
        when(piService.create(any(PaymentIntentCreateParams.class))).thenReturn(pi);

        CustomerService cs = mock(CustomerService.class);
        when(mockClient.customers()).thenReturn(cs);
        CustomerCollection coll = mock(CustomerCollection.class);
        when(coll.getData()).thenReturn(Collections.emptyList());
        when(cs.list(any(CustomerListParams.class))).thenReturn(coll);
        Customer created = mock(Customer.class);
        when(created.getId()).thenReturn("cus_new");
        when(cs.create(any(CustomerCreateParams.class))).thenReturn(created);

        PaymentMethodService pmService = mock(PaymentMethodService.class);
        when(mockClient.paymentMethods()).thenReturn(pmService);
        when(pmService.attach(anyString(), any(PaymentMethodAttachParams.class)))
                .thenReturn(mock(com.stripe.model.PaymentMethod.class));

        PaymentRequest request = PaymentRequest.builder().paymentId("p").orderId("o").userId("u-1")
                .email("new@example.com").stripePaymentMethodId("pm_x").amount(new BigDecimal("10.00")).currency("USD")
                .method(PaymentMethod.CARD).build();

        PaymentResult result = adapter.process(request);
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getProviderRef()).isEqualTo("pi_new");
    }

    @Test
    void process_newCustomer_withoutUserId_skipsMetadata() throws Exception {
        PaymentIntentService piService = mock(PaymentIntentService.class);
        when(mockClient.paymentIntents()).thenReturn(piService);
        PaymentIntent pi = mock(PaymentIntent.class);
        when(pi.getStatus()).thenReturn("succeeded");
        when(pi.getId()).thenReturn("pi_nm");
        when(piService.create(any(PaymentIntentCreateParams.class))).thenReturn(pi);

        CustomerService cs = mock(CustomerService.class);
        when(mockClient.customers()).thenReturn(cs);
        CustomerCollection coll = mock(CustomerCollection.class);
        when(coll.getData()).thenReturn(Collections.emptyList());
        when(cs.list(any(CustomerListParams.class))).thenReturn(coll);
        Customer created = mock(Customer.class);
        when(created.getId()).thenReturn("cus_nm");
        when(cs.create(any(CustomerCreateParams.class))).thenReturn(created);

        PaymentMethodService pmService = mock(PaymentMethodService.class);
        when(mockClient.paymentMethods()).thenReturn(pmService);
        when(pmService.attach(anyString(), any(PaymentMethodAttachParams.class)))
                .thenReturn(mock(com.stripe.model.PaymentMethod.class));

        // userId is null and blank to exercise both branches over two calls
        PaymentRequest request = PaymentRequest.builder().paymentId("p").orderId("o").userId(null)
                .email("none@example.com").stripePaymentMethodId("pm_x").amount(new BigDecimal("1.00")).currency("USD")
                .method(PaymentMethod.CARD).build();
        PaymentResult result = adapter.process(request);
        assertThat(result.isSuccess()).isTrue();

        request = PaymentRequest.builder().paymentId("p").orderId("o").userId("  ").email("none2@example.com")
                .stripePaymentMethodId("pm_x").amount(new BigDecimal("1.00")).currency("USD").method(PaymentMethod.CARD)
                .build();
        result = adapter.process(request);
        assertThat(result.isSuccess()).isTrue();
    }

    @Test
    void process_blankEmail_skipsCustomer() throws Exception {
        PaymentIntentService piService = mock(PaymentIntentService.class);
        when(mockClient.paymentIntents()).thenReturn(piService);
        PaymentIntent pi = mock(PaymentIntent.class);
        when(pi.getStatus()).thenReturn("succeeded");
        when(pi.getId()).thenReturn("pi_no_cus");
        when(piService.create(any(PaymentIntentCreateParams.class))).thenReturn(pi);

        PaymentRequest request = PaymentRequest.builder().paymentId("p").orderId("o").userId("u").email("   ")
                .stripePaymentMethodId("pm_x").amount(new BigDecimal("9.99")).currency("USD").method(PaymentMethod.CARD)
                .build();
        PaymentResult result = adapter.process(request);
        assertThat(result.isSuccess()).isTrue();
    }

    @Test
    void process_attachAlreadyAttached_isSwallowed() throws Exception {
        PaymentIntentService piService = mock(PaymentIntentService.class);
        when(mockClient.paymentIntents()).thenReturn(piService);
        PaymentIntent pi = mock(PaymentIntent.class);
        when(pi.getStatus()).thenReturn("succeeded");
        when(pi.getId()).thenReturn("pi_aa");
        when(piService.create(any(PaymentIntentCreateParams.class))).thenReturn(pi);

        CustomerService cs = mock(CustomerService.class);
        when(mockClient.customers()).thenReturn(cs);
        CustomerCollection coll = mock(CustomerCollection.class);
        Customer existing = mock(Customer.class);
        when(existing.getId()).thenReturn("cus_aa");
        when(coll.getData()).thenReturn(List.of(existing));
        when(cs.list(any(CustomerListParams.class))).thenReturn(coll);

        PaymentMethodService pmService = mock(PaymentMethodService.class);
        when(mockClient.paymentMethods()).thenReturn(pmService);
        when(pmService.attach(anyString(), any(PaymentMethodAttachParams.class)))
                .thenThrow(new ApiException("PaymentMethod has already been attached", "r", "c", 400, null));

        PaymentRequest request = PaymentRequest.builder().paymentId("p").orderId("o").userId("u")
                .email("aa@example.com").stripePaymentMethodId("pm_x").amount(new BigDecimal("5.00")).currency("USD")
                .method(PaymentMethod.CARD).build();
        PaymentResult result = adapter.process(request);
        assertThat(result.isSuccess()).isTrue();
    }

    @Test
    void process_attachOtherError_isLoggedAndContinues() throws Exception {
        PaymentIntentService piService = mock(PaymentIntentService.class);
        when(mockClient.paymentIntents()).thenReturn(piService);
        PaymentIntent pi = mock(PaymentIntent.class);
        when(pi.getStatus()).thenReturn("succeeded");
        when(pi.getId()).thenReturn("pi_oe");
        when(piService.create(any(PaymentIntentCreateParams.class))).thenReturn(pi);

        CustomerService cs = mock(CustomerService.class);
        when(mockClient.customers()).thenReturn(cs);
        CustomerCollection coll = mock(CustomerCollection.class);
        Customer existing = mock(Customer.class);
        when(existing.getId()).thenReturn("cus_oe");
        when(coll.getData()).thenReturn(List.of(existing));
        when(cs.list(any(CustomerListParams.class))).thenReturn(coll);

        PaymentMethodService pmService = mock(PaymentMethodService.class);
        when(mockClient.paymentMethods()).thenReturn(pmService);
        when(pmService.attach(anyString(), any(PaymentMethodAttachParams.class)))
                .thenThrow(new ApiException("network blip", "r", "c", 500, null));

        PaymentRequest request = PaymentRequest.builder().paymentId("p").orderId("o").userId("u")
                .email("oe@example.com").stripePaymentMethodId("pm_x").amount(new BigDecimal("5.00")).currency("USD")
                .method(PaymentMethod.CARD).build();
        PaymentResult result = adapter.process(request);
        assertThat(result.isSuccess()).isTrue();
    }

    @Test
    void process_attachOtherError_nullMessage_isLoggedAndContinues() throws Exception {
        PaymentIntentService piService = mock(PaymentIntentService.class);
        when(mockClient.paymentIntents()).thenReturn(piService);
        PaymentIntent pi = mock(PaymentIntent.class);
        when(pi.getStatus()).thenReturn("succeeded");
        when(pi.getId()).thenReturn("pi_nm");
        when(piService.create(any(PaymentIntentCreateParams.class))).thenReturn(pi);

        CustomerService cs = mock(CustomerService.class);
        when(mockClient.customers()).thenReturn(cs);
        CustomerCollection coll = mock(CustomerCollection.class);
        Customer existing = mock(Customer.class);
        when(existing.getId()).thenReturn("cus_oe");
        when(coll.getData()).thenReturn(List.of(existing));
        when(cs.list(any(CustomerListParams.class))).thenReturn(coll);

        PaymentMethodService pmService = mock(PaymentMethodService.class);
        when(mockClient.paymentMethods()).thenReturn(pmService);
        when(pmService.attach(anyString(), any(PaymentMethodAttachParams.class)))
                .thenThrow(new ApiException(null, "r", "c", 500, null));

        PaymentRequest request = PaymentRequest.builder().paymentId("p").orderId("o").userId("u")
                .email("nm@example.com").stripePaymentMethodId("pm_x").amount(new BigDecimal("5.00")).currency("USD")
                .method(PaymentMethod.CARD).build();
        PaymentResult result = adapter.process(request);
        assertThat(result.isSuccess()).isTrue();
    }
}
