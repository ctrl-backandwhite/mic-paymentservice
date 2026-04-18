package com.backandwhite.api.dto.in;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentRefundDtoIn {

    @NotNull
    @DecimalMin("0.01")
    private BigDecimal amount;

    private String reason;
}
