package com.backandwhite.domain.exception;

import static org.assertj.core.api.Assertions.assertThat;

import com.backandwhite.common.exception.BusinessException;
import com.backandwhite.common.exception.EntityNotFoundException;
import org.junit.jupiter.api.Test;

class MessageTest {

    @Test
    void enumValues_exist() {
        assertThat(Message.values()).isNotEmpty();
        assertThat(Message.valueOf("PAYMENT_ALREADY_PROCESSED")).isNotNull();
    }

    @Test
    void toBusinessException_formatsArgs() {
        BusinessException ex = Message.PAYMENT_NOT_COMPLETED.toBusinessException("pay-1");
        assertThat(ex).isNotNull();
        assertThat(ex.getMessage()).contains("pay-1");
    }

    @Test
    void toEntityNotFound_formatsArgs() {
        EntityNotFoundException ex = Message.PAYMENT_ALREADY_PROCESSED.toEntityNotFound("idem-1");
        assertThat(ex).isNotNull();
        assertThat(ex.getMessage()).contains("idem-1");
    }

    @Test
    void codeAndDetail() {
        assertThat(Message.WEBHOOK_SIGNATURE_INVALID.getCode()).isEqualTo("PA008");
        assertThat(Message.WEBHOOK_SIGNATURE_INVALID.getDetail()).contains("provider");
    }

    @Test
    void allEnumEntries() {
        for (Message m : Message.values()) {
            assertThat(m.getCode()).isNotBlank();
            assertThat(m.getDetail()).isNotBlank();
        }
    }
}
