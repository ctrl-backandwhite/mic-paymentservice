package com.backandwhite.domain.exception;

import com.backandwhite.common.exception.BusinessException;
import com.backandwhite.common.exception.EntityNotFoundException;
import lombok.Getter;
import lombok.extern.log4j.Log4j2;

@Log4j2
@Getter
public enum Message {

    PAYMENT_ALREADY_PROCESSED("PA001", "Payment already processed for idempotency key: %s"), INVALID_STATUS_TRANSITION(
            "PA002", "Cannot transition payment from %s to %s"), PAYMENT_NOT_COMPLETED("PA003",
                    "Payment %s is not in COMPLETED status, cannot refund"), REFUND_EXCEEDS_AMOUNT("PA004",
                            "Refund amount %s exceeds remaining refundable amount %s"), CRYPTO_PAYMENT_EXPIRED("PA005",
                                    "Crypto payment %s has expired"), UNSUPPORTED_PAYMENT_METHOD("PA006",
                                            "Payment method %s is not supported"), PAYMENT_PROCESSING_FAILED("PA007",
                                                    "Payment processing failed: %s"), WEBHOOK_SIGNATURE_INVALID("PA008",
                                                            "Webhook signature verification failed for provider: %s"), SAVED_CARD_UNUSABLE(
                                                                    "PA009",
                                                                    "La tarjeta guardada ya no es válida. Por favor ingresa una nueva.");

    private final String code;
    private final String detail;

    Message(String code, String detail) {
        this.code = code;
        this.detail = detail;
    }

    public BusinessException toBusinessException(Object... args) {
        String formatted = String.format(detail, args);
        log.warn("[{}] {}", code, formatted);
        return new BusinessException(code, formatted);
    }

    public EntityNotFoundException toEntityNotFound(Object... args) {
        String formatted = String.format(detail, args);
        log.warn("[{}] {}", code, formatted);
        return new EntityNotFoundException(code, formatted);
    }
}
