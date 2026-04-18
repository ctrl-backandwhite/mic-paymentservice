package com.backandwhite.domain.gateway;

import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RefundRequest {

    private String paymentId;
    private String providerRef;
    private BigDecimal amount;
    private String reason;
}
