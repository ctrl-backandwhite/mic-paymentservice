package com.backandwhite.api.controller;

import com.backandwhite.BaseIntegrationTest;
import com.backandwhite.core.test.JwtTestUtil;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class PaymentControllerIT extends BaseIntegrationTest {

    @Autowired
    private JwtTestUtil jwtTestUtil;

    @Test
    void getAllPayments_withAdminToken_contextLoadsAndResponds() {
        String adminToken = jwtTestUtil.getToken("admin-user", List.of("ADMIN"));

        webTestClient.get().uri("/api/v1/payments").header("Authorization", adminToken)
                .header("X-nx036-auth", "internal-test-token").exchange().expectStatus().isOk();
    }

    @Test
    void getPayments_withoutToken_returns4xx() {
        webTestClient.get().uri("/api/v1/payments").exchange().expectStatus().is4xxClientError();
    }
}
