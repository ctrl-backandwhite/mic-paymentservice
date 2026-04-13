package com.backandwhite.api.mapper;

import com.backandwhite.api.dto.out.PaymentDtoOut;
import com.backandwhite.api.dto.out.PaymentRefundDtoOut;
import com.backandwhite.common.domain.valueobject.Money;
import com.backandwhite.domain.model.Payment;
import com.backandwhite.domain.model.PaymentRefund;
import org.mapstruct.Mapper;
import org.mapstruct.Named;

import java.math.BigDecimal;

@Mapper(componentModel = "spring")
public interface PaymentApiMapper {

    PaymentDtoOut toDto(Payment domain);

    PaymentRefundDtoOut toRefundDto(PaymentRefund domain);

    default BigDecimal moneyToBigDecimal(Money money) {
        return money != null ? money.getAmount() : null;
    }
}
