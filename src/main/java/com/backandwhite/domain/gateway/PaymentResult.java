package com.backandwhite.domain.gateway;

import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentResult {

    private boolean success;
    private String providerRef;
    private Map<String, Object> providerResponse;
    private String errorMessage;
    private String cryptoAddress;
    private String qrCodeUrl;
}
