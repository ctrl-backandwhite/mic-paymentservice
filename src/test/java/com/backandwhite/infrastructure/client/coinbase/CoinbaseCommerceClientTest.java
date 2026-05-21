package com.backandwhite.infrastructure.client.coinbase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import com.backandwhite.infrastructure.gateway.config.PaymentGatewayProperties;
import java.math.BigDecimal;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

class CoinbaseCommerceClientTest {

    private PaymentGatewayProperties props;
    private CoinbaseCommerceClient client;

    @BeforeEach
    void setUp() {
        props = new PaymentGatewayProperties();
        props.getCrypto().getCoinbase().setApiKey("api-key");
        props.getCrypto().getCoinbase().setBaseUrl("https://api.commerce.coinbase.com");
        client = new CoinbaseCommerceClient(props);
    }

    private RestClient stubPostClient(Map<String, Object> response) {
        RestClient rc = mock(RestClient.class);
        RestClient.RequestBodyUriSpec uriSpec = mock(RestClient.RequestBodyUriSpec.class);
        RestClient.RequestBodySpec bodySpec = mock(RestClient.RequestBodySpec.class);
        RestClient.ResponseSpec respSpec = mock(RestClient.ResponseSpec.class);
        when(rc.post()).thenReturn(uriSpec);
        when(uriSpec.uri(anyString())).thenReturn(bodySpec);
        when(bodySpec.body(any(Object.class))).thenReturn(bodySpec);
        when(bodySpec.retrieve()).thenReturn(respSpec);
        when(respSpec.body(Map.class)).thenReturn(response);
        return rc;
    }

    private RestClient stubPostClientThrowing(RuntimeException ex) {
        RestClient rc = mock(RestClient.class);
        RestClient.RequestBodyUriSpec uriSpec = mock(RestClient.RequestBodyUriSpec.class);
        RestClient.RequestBodySpec bodySpec = mock(RestClient.RequestBodySpec.class);
        RestClient.ResponseSpec respSpec = mock(RestClient.ResponseSpec.class);
        when(rc.post()).thenReturn(uriSpec);
        when(uriSpec.uri(anyString())).thenReturn(bodySpec);
        when(bodySpec.body(any(Object.class))).thenReturn(bodySpec);
        when(bodySpec.retrieve()).thenReturn(respSpec);
        when(respSpec.body(Map.class)).thenThrow(ex);
        return rc;
    }

    private RestClient stubGetClient(Map<String, Object> response) {
        RestClient rc = mock(RestClient.class);
        RestClient.RequestHeadersUriSpec<?> uriSpec = mock(RestClient.RequestHeadersUriSpec.class);
        RestClient.RequestHeadersSpec<?> headersSpec = mock(RestClient.RequestHeadersSpec.class);
        RestClient.ResponseSpec respSpec = mock(RestClient.ResponseSpec.class);
        org.mockito.Mockito.doReturn(uriSpec).when(rc).get();
        org.mockito.Mockito.doReturn(headersSpec).when(uriSpec).uri(anyString());
        org.mockito.Mockito.doReturn(respSpec).when(headersSpec).retrieve();
        when(respSpec.body(Map.class)).thenReturn(response);
        return rc;
    }

    private RestClient.Builder stubbedBuilder(RestClient out) {
        RestClient.Builder builder = mock(RestClient.Builder.class);
        when(builder.baseUrl(anyString())).thenReturn(builder);
        when(builder.defaultHeader(anyString(), anyString())).thenReturn(builder);
        when(builder.build()).thenReturn(out);
        return builder;
    }

    @Test
    void createCharge_returnsDataFromResponse() {
        Map<String, Object> data = Map.of("id", "id-1", "code", "CHRG1", "hosted_url", "https://host/c", "pricing",
                Map.of("local", Map.of("amount", "10.00", "currency", "USD")));
        Map<String, Object> response = Map.of("data", data);
        RestClient rc = stubPostClient(response);
        RestClient.Builder builder = stubbedBuilder(rc);

        try (MockedStatic<RestClient> mocked = mockStatic(RestClient.class)) {
            mocked.when(RestClient::builder).thenReturn(builder);

            Map<String, Object> result = client.createCharge("o1", "p1", new BigDecimal("10"), "usd", "bitcoin");

            assertThat(result).isEqualTo(data);
            assertThat(result).containsEntry("code", "CHRG1");
            assertThat(result).containsEntry("hosted_url", "https://host/c");
        }
    }

    @Test
    void createCharge_emptyResponse_throws() {
        RestClient rc = stubPostClient(null);
        RestClient.Builder builder = stubbedBuilder(rc);

        try (MockedStatic<RestClient> mocked = mockStatic(RestClient.class)) {
            mocked.when(RestClient::builder).thenReturn(builder);

            BigDecimal amount = new BigDecimal("10");
            assertThatThrownBy(() -> client.createCharge("o1", "p1", amount, "USD", "bitcoin"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Empty Coinbase Commerce create charge response");
        }
    }

    @Test
    void createCharge_4xxResponse_propagatesException() {
        RestClientResponseException ex = new RestClientResponseException("Bad Request", 400, "Bad Request", null, null,
                null);
        RestClient rc = stubPostClientThrowing(ex);
        RestClient.Builder builder = stubbedBuilder(rc);

        try (MockedStatic<RestClient> mocked = mockStatic(RestClient.class)) {
            mocked.when(RestClient::builder).thenReturn(builder);

            BigDecimal amount = new BigDecimal("10");
            assertThatThrownBy(() -> client.createCharge("o1", "p1", amount, "USD", "bitcoin"))
                    .isInstanceOf(RestClientResponseException.class);
        }
    }

    @Test
    void getCharge_returnsDataFromResponse() {
        Map<String, Object> data = Map.of("id", "id-x", "code", "CHRG-X");
        Map<String, Object> response = Map.of("data", data);
        RestClient rc = stubGetClient(response);
        RestClient.Builder builder = stubbedBuilder(rc);

        try (MockedStatic<RestClient> mocked = mockStatic(RestClient.class)) {
            mocked.when(RestClient::builder).thenReturn(builder);

            Map<String, Object> result = client.getCharge("CHRG-X");

            assertThat(result).isEqualTo(data);
        }
    }

    @Test
    void getCharge_emptyResponse_throws() {
        RestClient rc = stubGetClient(null);
        RestClient.Builder builder = stubbedBuilder(rc);

        try (MockedStatic<RestClient> mocked = mockStatic(RestClient.class)) {
            mocked.when(RestClient::builder).thenReturn(builder);

            assertThatThrownBy(() -> client.getCharge("CHRG-X")).isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Empty Coinbase Commerce get charge response");
        }
    }
}
