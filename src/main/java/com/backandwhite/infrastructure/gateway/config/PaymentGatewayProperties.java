package com.backandwhite.infrastructure.gateway.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "payment")
public class PaymentGatewayProperties {

    private Stripe stripe = new Stripe();
    private PayPal paypal = new PayPal();
    private Crypto crypto = new Crypto();

    @Data
    public static class Stripe {
        private String apiKey;
        private String webhookSecret;
    }

    @Data
    public static class PayPal {
        private String clientId;
        private String clientSecret;
        private String baseUrl = "https://api-m.sandbox.paypal.com";
    }

    @Data
    public static class Crypto {
        private String provider = "coinbase";
        private Coinbase coinbase = new Coinbase();

        @Data
        public static class Coinbase {
            private String apiKey;
            private String webhookSecret;
            private String baseUrl = "https://api.commerce.coinbase.com";
        }
    }
}
