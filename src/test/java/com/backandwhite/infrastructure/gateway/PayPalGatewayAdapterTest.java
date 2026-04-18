package com.backandwhite.infrastructure.gateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import com.backandwhite.domain.gateway.PaymentRequest;
import com.backandwhite.domain.gateway.PaymentResult;
import com.backandwhite.domain.gateway.RefundRequest;
import com.backandwhite.domain.gateway.RefundResult;
import com.backandwhite.domain.valueobject.PaymentMethod;
import com.backandwhite.infrastructure.client.paypal.PayPalTokenManager;
import com.backandwhite.infrastructure.gateway.config.PaymentGatewayProperties;
import java.math.BigDecimal;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

class PayPalGatewayAdapterTest {

    private PayPalTokenManager tokenManager;
    private PaymentGatewayProperties props;
    private PayPalGatewayAdapter adapter;

    @BeforeEach
    void setUp() {
        tokenManager = mock(PayPalTokenManager.class);
        when(tokenManager.getAccessToken()).thenReturn("token-xyz");
        props = new PaymentGatewayProperties();
        props.getPaypal().setBaseUrl("https://api-m.sandbox.paypal.com");
        adapter = new PayPalGatewayAdapter(tokenManager, props);
    }

    private RestClient stubbedRestClient(Map<String, Object> firstResponse, Map<String, Object> secondResponse) {
        RestClient client = mock(RestClient.class);
        RestClient.RequestBodyUriSpec uriSpec1 = mock(RestClient.RequestBodyUriSpec.class);
        RestClient.RequestBodySpec bodySpec1 = mock(RestClient.RequestBodySpec.class);
        RestClient.ResponseSpec respSpec1 = mock(RestClient.ResponseSpec.class);
        RestClient.RequestBodyUriSpec uriSpec2 = mock(RestClient.RequestBodyUriSpec.class);
        RestClient.RequestBodySpec bodySpec2 = mock(RestClient.RequestBodySpec.class);
        RestClient.ResponseSpec respSpec2 = mock(RestClient.ResponseSpec.class);
        when(client.post()).thenReturn(uriSpec1).thenReturn(uriSpec2);
        when(uriSpec1.uri(anyString())).thenReturn(bodySpec1);
        when(uriSpec2.uri(anyString())).thenReturn(bodySpec2);
        when(bodySpec1.header(anyString(), anyString())).thenReturn(bodySpec1);
        when(bodySpec2.header(anyString(), anyString())).thenReturn(bodySpec2);
        when(bodySpec1.body(any(Object.class))).thenReturn(bodySpec1);
        when(bodySpec2.body(any(Object.class))).thenReturn(bodySpec2);
        when(bodySpec1.retrieve()).thenReturn(respSpec1);
        when(bodySpec2.retrieve()).thenReturn(respSpec2);
        when(respSpec1.body(eq(Map.class))).thenReturn(firstResponse);
        when(respSpec2.body(eq(Map.class))).thenReturn(secondResponse);
        return client;
    }

    @Test
    void process_completed_returnsSuccess() {
        Map<String, Object> orderResp = Map.of("id", "ORDER-1");
        Map<String, Object> captureResp = Map.of("status", "COMPLETED");
        RestClient client = stubbedRestClient(orderResp, captureResp);

        try (MockedStatic<RestClient> mocked = mockStatic(RestClient.class)) {
            mocked.when(RestClient::create).thenReturn(client);

            PaymentRequest request = PaymentRequest.builder().paymentId("p").orderId("o").userId("u")
                    .amount(new BigDecimal("15.00")).currency("USD").method(PaymentMethod.PAYPAL).build();
            PaymentResult result = adapter.process(request);

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getProviderRef()).isEqualTo("ORDER-1");
            assertThat(result.getErrorMessage()).isNull();
            assertThat(result.getProviderResponse()).containsEntry("status", "COMPLETED");
        }
    }

    @Test
    void process_notCompleted_returnsFailure() {
        Map<String, Object> orderResp = Map.of("id", "ORDER-2");
        Map<String, Object> captureResp = Map.of("status", "DENIED");
        RestClient client = stubbedRestClient(orderResp, captureResp);

        try (MockedStatic<RestClient> mocked = mockStatic(RestClient.class)) {
            mocked.when(RestClient::create).thenReturn(client);
            PaymentRequest request = PaymentRequest.builder().paymentId("p").orderId("o").userId("u")
                    .amount(new BigDecimal("15.00")).currency("USD").method(PaymentMethod.PAYPAL).build();
            PaymentResult result = adapter.process(request);
            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getErrorMessage()).contains("DENIED");
        }
    }

    @Test
    void process_nullOrderResponse_throwsAndCaughtAsFailure() {
        RestClient client = stubbedRestClient(null, null);
        try (MockedStatic<RestClient> mocked = mockStatic(RestClient.class)) {
            mocked.when(RestClient::create).thenReturn(client);
            PaymentRequest request = PaymentRequest.builder().paymentId("p").orderId("o").userId("u")
                    .amount(new BigDecimal("15.00")).currency("USD").method(PaymentMethod.PAYPAL).build();
            // will throw IllegalStateException internally but that is not
            // RestClientException,
            // so it propagates. We assert propagation.
            org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class, () -> adapter.process(request));
        }
    }

    @Test
    void process_nullCaptureResponse_throws() {
        Map<String, Object> orderResp = Map.of("id", "ORDER-3");
        RestClient client = stubbedRestClient(orderResp, null);
        try (MockedStatic<RestClient> mocked = mockStatic(RestClient.class)) {
            mocked.when(RestClient::create).thenReturn(client);
            PaymentRequest request = PaymentRequest.builder().paymentId("p").orderId("o").userId("u")
                    .amount(new BigDecimal("15.00")).currency("USD").method(PaymentMethod.PAYPAL).build();
            org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class, () -> adapter.process(request));
        }
    }

    @Test
    void process_restClientException_returnsFailure() {
        RestClient client = mock(RestClient.class);
        when(client.post()).thenThrow(new RestClientException("conn refused"));
        try (MockedStatic<RestClient> mocked = mockStatic(RestClient.class)) {
            mocked.when(RestClient::create).thenReturn(client);
            PaymentRequest request = PaymentRequest.builder().paymentId("p").orderId("o").userId("u")
                    .amount(new BigDecimal("15.00")).currency("USD").method(PaymentMethod.PAYPAL).build();
            PaymentResult result = adapter.process(request);
            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getErrorMessage()).isEqualTo("conn refused");
        }
    }

    @Test
    void refund_completed_returnsSuccess() {
        Map<String, Object> resp = Map.of("status", "COMPLETED", "id", "RE-1");
        RestClient client = mock(RestClient.class);
        RestClient.RequestBodyUriSpec uriSpec = mock(RestClient.RequestBodyUriSpec.class);
        RestClient.RequestBodySpec bodySpec = mock(RestClient.RequestBodySpec.class);
        RestClient.ResponseSpec respSpec = mock(RestClient.ResponseSpec.class);
        when(client.post()).thenReturn(uriSpec);
        when(uriSpec.uri(anyString())).thenReturn(bodySpec);
        when(bodySpec.header(anyString(), anyString())).thenReturn(bodySpec);
        when(bodySpec.body(any(Object.class))).thenReturn(bodySpec);
        when(bodySpec.retrieve()).thenReturn(respSpec);
        when(respSpec.body(eq(Map.class))).thenReturn(resp);

        try (MockedStatic<RestClient> mocked = mockStatic(RestClient.class)) {
            mocked.when(RestClient::create).thenReturn(client);
            RefundRequest req = RefundRequest.builder().paymentId("p").providerRef("ORDER")
                    .amount(new BigDecimal("5.00")).reason("bad").build();
            RefundResult result = adapter.refund(req);
            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getProviderRef()).isEqualTo("RE-1");
        }
    }

    @Test
    void refund_notCompleted_returnsFailure() {
        Map<String, Object> resp = Map.of("status", "PENDING", "id", "RE-2");
        RestClient client = mock(RestClient.class);
        RestClient.RequestBodyUriSpec uriSpec = mock(RestClient.RequestBodyUriSpec.class);
        RestClient.RequestBodySpec bodySpec = mock(RestClient.RequestBodySpec.class);
        RestClient.ResponseSpec respSpec = mock(RestClient.ResponseSpec.class);
        when(client.post()).thenReturn(uriSpec);
        when(uriSpec.uri(anyString())).thenReturn(bodySpec);
        when(bodySpec.header(anyString(), anyString())).thenReturn(bodySpec);
        when(bodySpec.body(any(Object.class))).thenReturn(bodySpec);
        when(bodySpec.retrieve()).thenReturn(respSpec);
        when(respSpec.body(eq(Map.class))).thenReturn(resp);

        try (MockedStatic<RestClient> mocked = mockStatic(RestClient.class)) {
            mocked.when(RestClient::create).thenReturn(client);
            RefundRequest req = RefundRequest.builder().paymentId("p").providerRef("ORDER")
                    .amount(new BigDecimal("5.00")).reason(null).build();
            RefundResult result = adapter.refund(req);
            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getErrorMessage()).contains("PENDING");
        }
    }

    @Test
    void refund_nullResponse_throws() {
        RestClient client = mock(RestClient.class);
        RestClient.RequestBodyUriSpec uriSpec = mock(RestClient.RequestBodyUriSpec.class);
        RestClient.RequestBodySpec bodySpec = mock(RestClient.RequestBodySpec.class);
        RestClient.ResponseSpec respSpec = mock(RestClient.ResponseSpec.class);
        when(client.post()).thenReturn(uriSpec);
        when(uriSpec.uri(anyString())).thenReturn(bodySpec);
        when(bodySpec.header(anyString(), anyString())).thenReturn(bodySpec);
        when(bodySpec.body(any(Object.class))).thenReturn(bodySpec);
        when(bodySpec.retrieve()).thenReturn(respSpec);
        when(respSpec.body(eq(Map.class))).thenReturn(null);

        try (MockedStatic<RestClient> mocked = mockStatic(RestClient.class)) {
            mocked.when(RestClient::create).thenReturn(client);
            RefundRequest req = RefundRequest.builder().paymentId("p").providerRef("ORDER")
                    .amount(new BigDecimal("5.00")).build();
            org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class, () -> adapter.refund(req));
        }
    }

    @Test
    void refund_restClientException_returnsFailure() {
        RestClient client = mock(RestClient.class);
        when(client.post()).thenThrow(new RestClientException("timeout"));
        try (MockedStatic<RestClient> mocked = mockStatic(RestClient.class)) {
            mocked.when(RestClient::create).thenReturn(client);
            RefundRequest req = RefundRequest.builder().paymentId("p").providerRef("ORDER")
                    .amount(new BigDecimal("5.00")).reason("bad").build();
            RefundResult result = adapter.refund(req);
            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getErrorMessage()).isEqualTo("timeout");
        }
    }

    @Test
    void supports_paypal_true() {
        assertThat(adapter.supports(PaymentMethod.PAYPAL)).isTrue();
    }

    @Test
    void supports_card_false() {
        assertThat(adapter.supports(PaymentMethod.CARD)).isFalse();
    }
}
