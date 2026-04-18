package com.backandwhite.api.mapper;

import com.backandwhite.api.dto.out.PaymentDtoOut;
import com.backandwhite.api.dto.out.PaymentRefundDtoOut;
import com.backandwhite.common.domain.valueobject.Money;
import com.backandwhite.domain.model.Payment;
import com.backandwhite.domain.model.PaymentRefund;
import java.math.BigDecimal;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface PaymentApiMapper {

    PaymentDtoOut toDto(Payment domain);

    PaymentRefundDtoOut toRefundDto(PaymentRefund domain);

    default BigDecimal moneyToBigDecimal(Money money) {
        return money != null ? money.getAmount() : null;
    }
}
