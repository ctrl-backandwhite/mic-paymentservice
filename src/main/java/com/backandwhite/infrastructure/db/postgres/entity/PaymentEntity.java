package com.backandwhite.infrastructure.db.postgres.entity;

import com.backandwhite.common.domain.valueobject.Money;
import com.backandwhite.common.domain.valueobject.MoneyConverter;
import com.backandwhite.common.infrastructure.entity.AuditableEntity;
import com.backandwhite.domain.valueobject.PaymentMethod;
import com.backandwhite.domain.valueobject.PaymentStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.With;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@With
@Getter
@Setter
@Entity
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "payments")
public class PaymentEntity extends AuditableEntity {

    @Id
    @Column(length = 64)
    private String id;

    @Column(name = "order_id", nullable = false, length = 64)
    private String orderId;

    @Column(name = "user_id", nullable = false, length = 64)
    private String userId;

    @Convert(converter = MoneyConverter.class)
    @Column(nullable = false, precision = 15, scale = 2)
    private Money amount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Convert(converter = MoneyConverter.class)
    @Column(name = "settlement_amount", precision = 15, scale = 2)
    private Money settlementAmount;

    @Column(name = "settlement_currency", length = 10)
    private String settlementCurrency;

    @Column(name = "exchange_rate", precision = 18, scale = 8)
    private java.math.BigDecimal exchangeRate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private PaymentStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", nullable = false, length = 20)
    private PaymentMethod paymentMethod;

    @Column(name = "provider_ref")
    private String providerRef;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "provider_response", columnDefinition = "jsonb")
    private Map<String, Object> providerResponse;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "idempotency_key", unique = true, length = 128)
    private String idempotencyKey;

    @Column(name = "crypto_address")
    private String cryptoAddress;

    @Column(name = "crypto_expires_at")
    private Instant cryptoExpiresAt;

    @Column(name = "qr_code_url", length = 512)
    private String qrCodeUrl;
}
