package com.backandwhite.api.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.backandwhite.api.dto.out.PaymentDtoOut;
import com.backandwhite.api.dto.out.PaymentRefundDtoOut;
import com.backandwhite.common.domain.valueobject.Money;
import com.backandwhite.domain.model.Payment;
import com.backandwhite.domain.model.PaymentRefund;
import com.backandwhite.domain.valueobject.PaymentMethod;
import com.backandwhite.domain.valueobject.PaymentStatus;
import com.backandwhite.domain.valueobject.RefundStatus;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PaymentApiMapperTest {

    private PaymentApiMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new PaymentApiMapperImpl();
    }

    @Test
    void toDto_mapsAllFields() {
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        Payment payment = Payment.builder().id("pay-1").orderId("ord-1").userId("u-1")
                .amount(Money.of(new BigDecimal("100.00"))).currency("USD")
                .settlementAmount(Money.of(new BigDecimal("100.00"))).settlementCurrency("USDT")
                .exchangeRate(BigDecimal.ONE).status(PaymentStatus.COMPLETED).paymentMethod(PaymentMethod.CARD)
                .providerRef("ref-1").errorMessage("none").idempotencyKey("idem-1").cryptoAddress("addr")
                .cryptoExpiresAt(now).qrCodeUrl("url").createdAt(now).updatedAt(now).build();

        PaymentDtoOut dto = mapper.toDto(payment);

        assertThat(dto).isNotNull();
        assertThat(dto.getId()).isEqualTo("pay-1");
        assertThat(dto.getOrderId()).isEqualTo("ord-1");
        assertThat(dto.getUserId()).isEqualTo("u-1");
        assertThat(dto.getAmount()).isEqualByComparingTo(new BigDecimal("100.00"));
        assertThat(dto.getCurrency()).isEqualTo("USD");
        assertThat(dto.getSettlementAmount()).isEqualByComparingTo(new BigDecimal("100.00"));
        assertThat(dto.getSettlementCurrency()).isEqualTo("USDT");
        assertThat(dto.getExchangeRate()).isEqualByComparingTo(BigDecimal.ONE);
        assertThat(dto.getStatus()).isEqualTo(PaymentStatus.COMPLETED);
        assertThat(dto.getPaymentMethod()).isEqualTo(PaymentMethod.CARD);
        assertThat(dto.getProviderRef()).isEqualTo("ref-1");
        assertThat(dto.getErrorMessage()).isEqualTo("none");
        assertThat(dto.getIdempotencyKey()).isEqualTo("idem-1");
        assertThat(dto.getCryptoAddress()).isEqualTo("addr");
        assertThat(dto.getCryptoExpiresAt()).isEqualTo(now);
        assertThat(dto.getQrCodeUrl()).isEqualTo("url");
        assertThat(dto.getCreatedAt()).isEqualTo(now);
        assertThat(dto.getUpdatedAt()).isEqualTo(now);
    }

    @Test
    void toDto_null_returnsNull() {
        assertThat(mapper.toDto(null)).isNull();
    }

    @Test
    void toDto_nullMoney_returnsNullAmount() {
        Payment payment = Payment.builder().id("p").build();
        PaymentDtoOut dto = mapper.toDto(payment);
        assertThat(dto.getAmount()).isNull();
    }

    @Test
    void toRefundDto_mapsAllFields() {
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        PaymentRefund refund = PaymentRefund.builder().id("r-1").paymentId("p-1")
                .amount(Money.of(new BigDecimal("25.00"))).status(RefundStatus.COMPLETED).reason("reason-x")
                .providerRef("ref-x").createdAt(now).updatedAt(now).build();

        PaymentRefundDtoOut dto = mapper.toRefundDto(refund);

        assertThat(dto).isNotNull();
        assertThat(dto.getId()).isEqualTo("r-1");
        assertThat(dto.getPaymentId()).isEqualTo("p-1");
        assertThat(dto.getAmount()).isEqualByComparingTo(new BigDecimal("25.00"));
        assertThat(dto.getStatus()).isEqualTo(RefundStatus.COMPLETED);
        assertThat(dto.getReason()).isEqualTo("reason-x");
        assertThat(dto.getProviderRef()).isEqualTo("ref-x");
        assertThat(dto.getCreatedAt()).isEqualTo(now);
        assertThat(dto.getUpdatedAt()).isEqualTo(now);
    }

    @Test
    void toRefundDto_null_returnsNull() {
        assertThat(mapper.toRefundDto(null)).isNull();
    }

    @Test
    void moneyToBigDecimal_null_returnsNull() {
        assertThat(mapper.moneyToBigDecimal(null)).isNull();
    }

    @Test
    void moneyToBigDecimal_value_returnsAmount() {
        assertThat(mapper.moneyToBigDecimal(Money.of(new BigDecimal("50.00"))))
                .isEqualByComparingTo(new BigDecimal("50.00"));
    }
}
