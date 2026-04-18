package com.backandwhite;

import com.backandwhite.common.configuration.annotation.EnableCoreApplication;
import com.backandwhite.infrastructure.gateway.config.PaymentGatewayProperties;
import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@EnableCoreApplication
@EnableConfigurationProperties(PaymentGatewayProperties.class)
@OpenAPIDefinition(info = @Info(title = "Payment Service API", version = "1.0", description = "Card, PayPal and Crypto payment processing, refunds and webhooks"))
public class MicPaymentserviceApplication {

    public static void main(String[] args) {
        SpringApplication app = new SpringApplication(MicPaymentserviceApplication.class);
        app.setDefaultProperties(java.util.Map.of("RAILWAY_PUBLIC_DOMAIN", "localhost:6007"));
        app.run(args);
    }
}
