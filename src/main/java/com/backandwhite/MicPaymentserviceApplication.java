package com.backandwhite;

import com.backandwhite.common.configuration.annotation.EnableCoreApplication;
import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import org.springframework.boot.SpringApplication;

@EnableCoreApplication
@OpenAPIDefinition(info = @Info(title = "Payment Service API", version = "1.0", description = "Card, PayPal and Crypto payment processing, refunds and webhooks"))
public class MicPaymentserviceApplication {

    public static void main(String[] args) {
        SpringApplication app = new SpringApplication(MicPaymentserviceApplication.class);
        app.setDefaultProperties(java.util.Map.of(
                "RAILWAY_PUBLIC_DOMAIN", "localhost:6007"));
        app.run(args);
    }
}
