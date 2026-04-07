package com.backandwhite.domain.model;

import com.backandwhite.domain.valueobject.RefundStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.With;

import java.math.BigDecimal;
import java.time.Instant;

@Data
@With
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentRefund {

    private String id;
    private String paymentId;
    private BigDecimal amount;
    private RefundStatus status;
    private String reason;
    private String providerRef;
    private Instant createdAt;
    private Instant updatedAt;
}
