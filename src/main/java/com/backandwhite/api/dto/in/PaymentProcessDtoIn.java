package com.backandwhite.api.dto.in;

import com.backandwhite.domain.valueobject.PaymentMethod;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
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
public class PaymentProcessDtoIn {

    @NotBlank
    private String orderId;

    @NotBlank
    private String userId;

    private String email;

    @NotNull
    @DecimalMin("0.01")
    private BigDecimal amount;

    private String currency;

    @NotNull
    private PaymentMethod paymentMethod;

    private String idempotencyKey;
}
