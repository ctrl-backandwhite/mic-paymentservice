package com.backandwhite.infrastructure.db.postgres.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.backandwhite.common.domain.valueobject.Money;
import com.backandwhite.domain.model.Payment;
import com.backandwhite.domain.model.PaymentRefund;
import com.backandwhite.domain.valueobject.PaymentMethod;
import com.backandwhite.domain.valueobject.PaymentStatus;
import com.backandwhite.domain.valueobject.RefundStatus;
import com.backandwhite.infrastructure.db.postgres.entity.PaymentEntity;
import com.backandwhite.infrastructure.db.postgres.entity.PaymentRefundEntity;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PaymentInfraMapperTest {

    private PaymentInfraMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new PaymentInfraMapperImpl();
    }

    @Test
    void toDomain_null_returnsNull() {
        assertThat(mapper.toDomain(null)).isNull();
    }

    @Test
    void toEntity_null_returnsNull() {
        assertThat(mapper.toEntity(null)).isNull();
    }

    @Test
    void toRefundDomain_null_returnsNull() {
        assertThat(mapper.toRefundDomain(null)).isNull();
    }

    @Test
    void toRefundEntity_null_returnsNull() {
        assertThat(mapper.toRefundEntity(null)).isNull();
    }

    @Test
    void toDomain_mapsAllFields() {
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        PaymentEntity entity = PaymentEntity.builder().id("p-1").orderId("o-1").userId("u-1")
                .amount(Money.of(new BigDecimal("100.00"))).currency("USD")
                .settlementAmount(Money.of(new BigDecimal("100.00"))).settlementCurrency("USDT")
                .exchangeRate(BigDecimal.ONE).status(PaymentStatus.COMPLETED).paymentMethod(PaymentMethod.CARD)
                .providerRef("ref-1").providerResponse(Map.of("provider", "stripe")).errorMessage("err")
                .idempotencyKey("idem").cryptoAddress("addr").cryptoExpiresAt(now).qrCodeUrl("url").build();
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);

        Payment domain = mapper.toDomain(entity);

        assertThat(domain).isNotNull();
        assertThat(domain.getId()).isEqualTo("p-1");
        assertThat(domain.getOrderId()).isEqualTo("o-1");
        assertThat(domain.getUserId()).isEqualTo("u-1");
        assertThat(domain.getAmount().getAmount()).isEqualByComparingTo("100.00");
        assertThat(domain.getCurrency()).isEqualTo("USD");
        assertThat(domain.getSettlementCurrency()).isEqualTo("USDT");
        assertThat(domain.getStatus()).isEqualTo(PaymentStatus.COMPLETED);
        assertThat(domain.getPaymentMethod()).isEqualTo(PaymentMethod.CARD);
        assertThat(domain.getProviderRef()).isEqualTo("ref-1");
        assertThat(domain.getProviderResponse()).containsEntry("provider", "stripe");
        assertThat(domain.getErrorMessage()).isEqualTo("err");
        assertThat(domain.getIdempotencyKey()).isEqualTo("idem");
        assertThat(domain.getCryptoAddress()).isEqualTo("addr");
        assertThat(domain.getCryptoExpiresAt()).isEqualTo(now);
        assertThat(domain.getQrCodeUrl()).isEqualTo("url");
        assertThat(domain.getCreatedAt()).isEqualTo(now);
        assertThat(domain.getUpdatedAt()).isEqualTo(now);
    }

    @Test
    void toDomain_nullProviderResponse() {
        PaymentEntity entity = PaymentEntity.builder().id("p-1").orderId("o-1").userId("u-1")
                .amount(Money.of(BigDecimal.TEN)).currency("USD").status(PaymentStatus.PENDING)
                .paymentMethod(PaymentMethod.CARD).build();
        Payment domain = mapper.toDomain(entity);
        assertThat(domain.getProviderResponse()).isNull();
    }

    @Test
    void toEntity_mapsAllFields() {
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        Payment domain = Payment.builder().id("p-1").orderId("o-1").userId("u-1")
                .amount(Money.of(new BigDecimal("100.00"))).currency("USD")
                .settlementAmount(Money.of(new BigDecimal("100.00"))).settlementCurrency("USDT")
                .exchangeRate(BigDecimal.ONE).status(PaymentStatus.COMPLETED).paymentMethod(PaymentMethod.CARD)
                .providerRef("ref-1").providerResponse(Map.of("x", "y")).errorMessage("err").idempotencyKey("idem")
                .cryptoAddress("addr").cryptoExpiresAt(now).qrCodeUrl("url").createdAt(now).updatedAt(now).build();

        PaymentEntity entity = mapper.toEntity(domain);

        assertThat(entity).isNotNull();
        assertThat(entity.getId()).isEqualTo("p-1");
        assertThat(entity.getOrderId()).isEqualTo("o-1");
        assertThat(entity.getAmount().getAmount()).isEqualByComparingTo("100.00");
        assertThat(entity.getProviderResponse()).containsEntry("x", "y");
        assertThat(entity.getCreatedAt()).isEqualTo(now);
    }

    @Test
    void toEntity_nullProviderResponse() {
        Payment domain = Payment.builder().id("p").amount(Money.of(BigDecimal.ONE)).build();
        PaymentEntity entity = mapper.toEntity(domain);
        assertThat(entity.getProviderResponse()).isNull();
    }

    @Test
    void toRefundDomain_mapsAllFields() {
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        PaymentRefundEntity entity = PaymentRefundEntity.builder().id("r-1").paymentId("p-1")
                .amount(Money.of(new BigDecimal("25.00"))).status(RefundStatus.COMPLETED).reason("reason")
                .providerRef("ref").build();
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);

        PaymentRefund domain = mapper.toRefundDomain(entity);

        assertThat(domain.getId()).isEqualTo("r-1");
        assertThat(domain.getPaymentId()).isEqualTo("p-1");
        assertThat(domain.getAmount().getAmount()).isEqualByComparingTo("25.00");
        assertThat(domain.getStatus()).isEqualTo(RefundStatus.COMPLETED);
        assertThat(domain.getReason()).isEqualTo("reason");
        assertThat(domain.getProviderRef()).isEqualTo("ref");
        assertThat(domain.getCreatedAt()).isEqualTo(now);
        assertThat(domain.getUpdatedAt()).isEqualTo(now);
    }

    @Test
    void toRefundEntity_mapsAllFields() {
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        PaymentRefund domain = PaymentRefund.builder().id("r-1").paymentId("p-1")
                .amount(Money.of(new BigDecimal("25.00"))).status(RefundStatus.COMPLETED).reason("reason")
                .providerRef("ref").createdAt(now).updatedAt(now).build();

        PaymentRefundEntity entity = mapper.toRefundEntity(domain);

        assertThat(entity.getId()).isEqualTo("r-1");
        assertThat(entity.getPaymentId()).isEqualTo("p-1");
        assertThat(entity.getAmount().getAmount()).isEqualByComparingTo("25.00");
        assertThat(entity.getStatus()).isEqualTo(RefundStatus.COMPLETED);
        assertThat(entity.getCreatedAt()).isEqualTo(now);
    }
}
