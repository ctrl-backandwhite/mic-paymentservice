package com.backandwhite.infrastructure.db.postgres.mapper;

import com.backandwhite.domain.model.Payment;
import com.backandwhite.domain.model.PaymentRefund;
import com.backandwhite.infrastructure.db.postgres.entity.PaymentEntity;
import com.backandwhite.infrastructure.db.postgres.entity.PaymentRefundEntity;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface PaymentInfraMapper {

    Payment toDomain(PaymentEntity entity);

    PaymentEntity toEntity(Payment domain);

    PaymentRefund toRefundDomain(PaymentRefundEntity entity);

    PaymentRefundEntity toRefundEntity(PaymentRefund domain);
}
