package com.backandwhite.domain.gateway;

import com.backandwhite.domain.valueobject.PaymentMethod;

public interface PaymentGateway {

    PaymentResult process(PaymentRequest request);

    RefundResult refund(RefundRequest request);

    boolean supports(PaymentMethod method);
}
