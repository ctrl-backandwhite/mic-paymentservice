package com.backandwhite.api.dto.out;

import com.backandwhite.domain.valueobject.RefundStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentRefundDtoOut {

    private String id;
    private String paymentId;
    private BigDecimal amount;
    private RefundStatus status;
    private String reason;
    private String providerRef;
    private Instant createdAt;
    private Instant updatedAt;
}
