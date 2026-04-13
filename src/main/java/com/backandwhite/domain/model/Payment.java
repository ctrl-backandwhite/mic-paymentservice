package com.backandwhite.domain.model;

import com.backandwhite.common.domain.valueobject.Money;
import com.backandwhite.domain.valueobject.PaymentMethod;
import com.backandwhite.domain.valueobject.PaymentStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.With;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

@Data
@With
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Payment {

    private String id;
    private String orderId;
    private String userId;
    private Money amount;
    private String currency;

    // Settlement fields — actual currency charged to the provider
    private Money settlementAmount;
    private String settlementCurrency;
    private BigDecimal exchangeRate;
    private PaymentStatus status;
    private PaymentMethod paymentMethod;
    private String providerRef;
    private Map<String, Object> providerResponse;
    private String errorMessage;
    private String idempotencyKey;
    private String cryptoAddress;
    private Instant cryptoExpiresAt;
    private String qrCodeUrl;
    private Instant createdAt;
    private Instant updatedAt;
}
