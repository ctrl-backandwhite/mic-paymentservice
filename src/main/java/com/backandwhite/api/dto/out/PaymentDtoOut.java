package com.backandwhite.api.dto.out;

import com.backandwhite.domain.valueobject.PaymentMethod;
import com.backandwhite.domain.valueobject.PaymentStatus;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentDtoOut {

    private String id;
    private String orderId;
    private String userId;
    private BigDecimal amount;
    private String currency;

    private BigDecimal settlementAmount;
    private String settlementCurrency;
    private BigDecimal exchangeRate;

    private PaymentStatus status;
    private PaymentMethod paymentMethod;
    private String providerRef;
    private String errorMessage;
    private String idempotencyKey;
    private String cryptoAddress;
    private Instant cryptoExpiresAt;
    private String qrCodeUrl;
    private Instant createdAt;
    private Instant updatedAt;
}
