package com.backandwhite.infrastructure.client.paypal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.backandwhite.infrastructure.gateway.config.PaymentGatewayProperties;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClient;

class PayPalTokenManagerTest {

    private PaymentGatewayProperties props;
    private PayPalTokenManager manager;

    @BeforeEach
    void setUp() {
        props = new PaymentGatewayProperties();
        props.getPaypal().setClientId("client");
        props.getPaypal().setClientSecret("secret");
        props.getPaypal().setBaseUrl("https://api-m.sandbox.paypal.com");
        manager = new PayPalTokenManager(props);
    }

    private RestClient stubbedRestClient(Map<String, Object> response) {
        RestClient client = mock(RestClient.class);
        RestClient.RequestBodyUriSpec uriSpec = mock(RestClient.RequestBodyUriSpec.class);
        RestClient.RequestBodySpec bodySpec = mock(RestClient.RequestBodySpec.class);
        RestClient.ResponseSpec respSpec = mock(RestClient.ResponseSpec.class);
        when(client.post()).thenReturn(uriSpec);
        when(uriSpec.uri(anyString())).thenReturn(bodySpec);
        when(bodySpec.header(anyString(), anyString())).thenReturn(bodySpec);
        when(bodySpec.body(any(Object.class))).thenReturn(bodySpec);
        when(bodySpec.retrieve()).thenReturn(respSpec);
        when(respSpec.body(Map.class)).thenReturn(response);
        return client;
    }

    @Test
    void getAccessToken_firstCall_fetchesAndCachesToken() {
        Map<String, Object> response = new HashMap<>();
        response.put("access_token", "tk-1");
        response.put("expires_in", 3600);
        RestClient client = stubbedRestClient(response);

        try (MockedStatic<RestClient> mocked = mockStatic(RestClient.class)) {
            mocked.when(RestClient::create).thenReturn(client);

            String token = manager.getAccessToken();

            assertThat(token).isEqualTo("tk-1");
            mocked.verify(RestClient::create, times(1));
        }
    }

    @Test
    void getAccessToken_calledTwice_secondReturnsCached() {
        Map<String, Object> response = new HashMap<>();
        response.put("access_token", "tk-cached");
        response.put("expires_in", 3600);
        RestClient client = stubbedRestClient(response);

        try (MockedStatic<RestClient> mocked = mockStatic(RestClient.class)) {
            mocked.when(RestClient::create).thenReturn(client);

            String token1 = manager.getAccessToken();
            String token2 = manager.getAccessToken();

            assertThat(token1).isEqualTo("tk-cached");
            assertThat(token2).isEqualTo("tk-cached");
            // Only one network call.
            mocked.verify(RestClient::create, times(1));
            verify(client, times(1)).post();
        }
    }

    @Test
    void getAccessToken_expired_refreshesAgain() {
        Map<String, Object> response = new HashMap<>();
        response.put("access_token", "tk-refreshed");
        response.put("expires_in", 3600);
        RestClient client = stubbedRestClient(response);

        try (MockedStatic<RestClient> mocked = mockStatic(RestClient.class)) {
            mocked.when(RestClient::create).thenReturn(client);

            // Pre-populate cache as if a previous token existed but already expired.
            ReflectionTestUtils.setField(manager, "accessToken", "old-tk");
            ReflectionTestUtils.setField(manager, "expiresAt", Instant.now().minusSeconds(60));

            String token = manager.getAccessToken();

            assertThat(token).isEqualTo("tk-refreshed");
            mocked.verify(RestClient::create, times(1));
        }
    }

    @Test
    void getAccessToken_usesDefaultExpiresInWhenMissing() {
        Map<String, Object> response = new HashMap<>();
        response.put("access_token", "tk-default-exp");
        // expires_in not provided -> defaults to 3600
        RestClient client = stubbedRestClient(response);

        try (MockedStatic<RestClient> mocked = mockStatic(RestClient.class)) {
            mocked.when(RestClient::create).thenReturn(client);

            String token = manager.getAccessToken();

            assertThat(token).isEqualTo("tk-default-exp");
            Instant expiresAt = (Instant) ReflectionTestUtils.getField(manager, "expiresAt");
            assertThat(expiresAt).isAfter(Instant.now().plusSeconds(3000));
        }
    }

    @Test
    void getAccessToken_emptyResponse_throwsIllegalStateException() {
        RestClient client = stubbedRestClient(null);

        try (MockedStatic<RestClient> mocked = mockStatic(RestClient.class)) {
            mocked.when(RestClient::create).thenReturn(client);

            assertThatThrownBy(() -> manager.getAccessToken()).isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Empty PayPal token response");
        }
    }
}
