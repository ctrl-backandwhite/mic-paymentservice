package com.backandwhite.infrastructure.client.paypal;

import com.backandwhite.infrastructure.gateway.config.PaymentGatewayProperties;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Log4j2
@Component
@RequiredArgsConstructor
public class PayPalTokenManager {

    private final PaymentGatewayProperties props;
    private String accessToken;
    private Instant expiresAt = Instant.MIN;
    private final ReentrantLock lock = new ReentrantLock();

    public String getAccessToken() {
        lock.lock();
        try {
            if (accessToken == null || Instant.now().isAfter(expiresAt.minusSeconds(30))) {
                refresh();
            }
            return accessToken;
        } finally {
            lock.unlock();
        }
    }

    @SuppressWarnings("unchecked")
    private void refresh() {
        String credentials = Base64.getEncoder().encodeToString(
                (props.getPaypal().getClientId() + ":" + props.getPaypal().getClientSecret()).getBytes());

        RestClient client = RestClient.create();
        Map<String, Object> response = client.post().uri(props.getPaypal().getBaseUrl() + "/v1/oauth2/token")
                .header("Authorization", "Basic " + credentials)
                .header("Content-Type", "application/x-www-form-urlencoded").body("grant_type=client_credentials")
                .retrieve().body(Map.class);

        if (response == null) {
            throw new IllegalStateException("Empty PayPal token response");
        }

        this.accessToken = (String) response.get("access_token");
        int expiresIn = (int) response.getOrDefault("expires_in", 3600);
        this.expiresAt = Instant.now().plusSeconds(expiresIn);
        log.info("PayPal access token refreshed, expires in {}s", expiresIn);
    }
}
