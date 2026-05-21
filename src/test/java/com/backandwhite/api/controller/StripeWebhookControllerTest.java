package com.backandwhite.api.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import com.backandwhite.application.usecase.PaymentUseCase;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Event;
import com.stripe.net.Webhook;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class StripeWebhookControllerTest {

    @Mock
    private PaymentUseCase paymentUseCase;

    @InjectMocks
    private StripeWebhookController controller;

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private HttpServletRequest mockRequestWithBody(String body, String signature) throws IOException {
        HttpServletRequest req = mock(HttpServletRequest.class);
        InputStream is = new ByteArrayInputStream(body == null ? new byte[0] : body.getBytes());
        ServletInputStream sis = new ServletInputStream() {
            @Override
            public boolean isFinished() {
                try {
                    return is.available() == 0;
                } catch (IOException _) {
                    return true;
                }
            }
            @Override
            public boolean isReady() {
                return true;
            }
            @Override
            public void setReadListener(ReadListener listener) {
                /* noop */ }
            @Override
            public int read() throws IOException {
                return is.read();
            }
        };
        when(req.getInputStream()).thenReturn(sis);
        when(req.getHeader("Stripe-Signature")).thenReturn(signature);
        return req;
    }

    @BeforeEach
    void initSecret() {
        ReflectionTestUtils.setField(controller, "webhookSecret", "");
    }

    @ParameterizedTest
    @ValueSource(strings = {"payment_intent.succeeded", "payment_intent.payment_failed", "charge.refunded",
            "customer.created"})
    void blankSecret_anyEventType_returnsOk(String eventType) throws Exception {
        String payload = "{\"id\":\"evt_1\",\"type\":\"" + eventType + "\",\"data\":{\"object\":{}}}";
        ResponseEntity<String> resp = controller.handleStripeWebhook(mockRequestWithBody(payload, "sig"));
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void blankSecret_invalidJson_returnsBadRequest() throws Exception {
        String payload = "not-json";
        ResponseEntity<String> resp = controller.handleStripeWebhook(mockRequestWithBody(payload, "sig"));
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void withSecret_invalidSignature_returnsBadRequest() throws Exception {
        ReflectionTestUtils.setField(controller, "webhookSecret", "whsec_test");
        String payload = "{\"id\":\"evt_x\",\"type\":\"payment_intent.succeeded\",\"data\":{\"object\":{}}}";
        try (MockedStatic<Webhook> mocked = mockStatic(Webhook.class)) {
            mocked.when(() -> Webhook.constructEvent(anyString(), anyString(), anyString()))
                    .thenThrow(new SignatureVerificationException("bad signature", "sig"));
            ResponseEntity<String> resp = controller.handleStripeWebhook(mockRequestWithBody(payload, "sig"));
            assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(resp.getBody()).contains("Invalid signature");
        }
    }

    @Test
    void withSecret_validSignature_unknownType_returnsOk() throws Exception {
        ReflectionTestUtils.setField(controller, "webhookSecret", "whsec_test");
        // Use a type that hits the default branch — avoids needing to populate
        // Stripe's complex Event.Data.object hierarchy for happy-path parsing.
        String payload = "{\"id\":\"evt_v\",\"type\":\"customer.created\",\"data\":{\"object\":{}}}";
        Event event = MAPPER.readValue(payload, Event.class);
        try (MockedStatic<Webhook> mocked = mockStatic(Webhook.class)) {
            mocked.when(() -> Webhook.constructEvent(anyString(), anyString(), anyString())).thenReturn(event);
            ResponseEntity<String> resp = controller.handleStripeWebhook(mockRequestWithBody(payload, "sig"));
            assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        }
    }

    /**
     * Helper that drives the verified-signature happy path with a real parsed Event
     * built via Stripe SDK's own Gson deserializer (avoids the Jackson↔StripeObject
     * incompat when the object payload has fields).
     */
    private ResponseEntity<String> runWithSignedPayload(String payload) throws Exception {
        ReflectionTestUtils.setField(controller, "webhookSecret", "whsec_test");
        Event event = com.stripe.net.ApiResource.GSON.fromJson(payload, Event.class);
        try (MockedStatic<Webhook> mocked = mockStatic(Webhook.class)) {
            mocked.when(() -> Webhook.constructEvent(anyString(), anyString(), anyString())).thenReturn(event);
            return controller.handleStripeWebhook(mockRequestWithBody(payload, "sig"));
        }
    }

    @Test
    void withSecret_succeededWithMetadata_returnsOk() throws Exception {
        String payload = "{\"id\":\"evt_md\",\"type\":\"payment_intent.succeeded\","
                + "\"data\":{\"object\":{\"id\":\"pi_5\",\"object\":\"payment_intent\","
                + "\"metadata\":{\"paymentId\":\"pay-77\"}}}}";
        assertThat(runWithSignedPayload(payload).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void withSecret_failedWithLastError_returnsOk() throws Exception {
        String payload = "{\"id\":\"evt_fl\",\"type\":\"payment_intent.payment_failed\","
                + "\"data\":{\"object\":{\"id\":\"pi_6\",\"object\":\"payment_intent\","
                + "\"last_payment_error\":{\"message\":\"card declined\"},"
                + "\"metadata\":{\"paymentId\":\"pay-99\"}}}}";
        assertThat(runWithSignedPayload(payload).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void withSecret_failedWithoutLastError_returnsOk() throws Exception {
        String payload = "{\"id\":\"evt_fl2\",\"type\":\"payment_intent.payment_failed\","
                + "\"data\":{\"object\":{\"id\":\"pi_7\",\"object\":\"payment_intent\"}}}";
        assertThat(runWithSignedPayload(payload).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void withSecret_chargeRefundedWithAmount_returnsOk() throws Exception {
        String payload = "{\"id\":\"evt_cr\",\"type\":\"charge.refunded\","
                + "\"data\":{\"object\":{\"id\":\"ch_1\",\"object\":\"charge\",\"amount_refunded\":1500}}}";
        assertThat(runWithSignedPayload(payload).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void withSecret_chargeRefundedMissingAmount_returnsOk() throws Exception {
        String payload = "{\"id\":\"evt_cr2\",\"type\":\"charge.refunded\","
                + "\"data\":{\"object\":{\"id\":\"ch_2\",\"object\":\"charge\"}}}";
        assertThat(runWithSignedPayload(payload).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void withSecret_succeededMissingId_returnsOk() throws Exception {
        String payload = "{\"id\":\"evt_si\",\"type\":\"payment_intent.succeeded\","
                + "\"data\":{\"object\":{\"object\":\"payment_intent\"}}}";
        assertThat(runWithSignedPayload(payload).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void withSecret_failedMissingId_returnsOk() throws Exception {
        String payload = "{\"id\":\"evt_fi\",\"type\":\"payment_intent.payment_failed\","
                + "\"data\":{\"object\":{\"object\":\"payment_intent\"}}}";
        assertThat(runWithSignedPayload(payload).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void withSecret_succeededWithoutMetadata_returnsOk() throws Exception {
        // Drives the `metadataNode == null` branch in handlePaymentIntentSucceeded.
        String payload = "{\"id\":\"evt_nm\",\"type\":\"payment_intent.succeeded\","
                + "\"data\":{\"object\":{\"id\":\"pi_x\",\"object\":\"payment_intent\"}}}";
        assertThat(runWithSignedPayload(payload).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void withSecret_failedWithoutMetadataAndError_returnsOk() throws Exception {
        // Drives both `errorNode == null` (default "Unknown error") AND
        // `paymentIntent.has("metadata")` false branches.
        String payload = "{\"id\":\"evt_fbm\",\"type\":\"payment_intent.payment_failed\","
                + "\"data\":{\"object\":{\"id\":\"pi_b\",\"object\":\"payment_intent\"}}}";
        assertThat(runWithSignedPayload(payload).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void getInputStream_throws_returnsBadRequest() throws Exception {
        HttpServletRequest req = mock(HttpServletRequest.class);
        when(req.getInputStream()).thenThrow(new IOException("io fail"));
        when(req.getHeader("Stripe-Signature")).thenReturn(null);
        // payload becomes "" → readValue throws IOException → 400
        ResponseEntity<String> resp = controller.handleStripeWebhook(req);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }
}
