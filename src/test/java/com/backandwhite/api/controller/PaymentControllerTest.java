package com.backandwhite.api.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.backandwhite.api.dto.PaginationDtoOut;
import com.backandwhite.api.dto.in.CryptoCreateDtoIn;
import com.backandwhite.api.dto.in.PaymentProcessDtoIn;
import com.backandwhite.api.dto.in.PaymentRefundDtoIn;
import com.backandwhite.api.dto.out.PaymentDtoOut;
import com.backandwhite.api.dto.out.PaymentRefundDtoOut;
import com.backandwhite.api.mapper.PaymentApiMapper;
import com.backandwhite.application.usecase.PaymentUseCase;
import com.backandwhite.common.domain.model.PageResult;
import com.backandwhite.common.domain.valueobject.Money;
import com.backandwhite.domain.model.Payment;
import com.backandwhite.domain.model.PaymentRefund;
import com.backandwhite.domain.valueobject.PaymentMethod;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class PaymentControllerTest {

    private PaymentUseCase useCase;
    private PaymentApiMapper mapper;
    private PaymentController controller;

    @BeforeEach
    void setUp() {
        useCase = mock(PaymentUseCase.class);
        mapper = mock(PaymentApiMapper.class);
        controller = new PaymentController(useCase, mapper);
    }

    @Test
    void processPayment_returnsCreated() {
        Payment p = Payment.builder().id("p-1").build();
        PaymentDtoOut dto = PaymentDtoOut.builder().id("p-1").build();
        when(useCase.processPayment(anyString(), anyString(), any(), any(Money.class), anyString(),
                any(PaymentMethod.class), any(), any())).thenReturn(p);
        when(mapper.toDto(p)).thenReturn(dto);

        PaymentProcessDtoIn input = PaymentProcessDtoIn.builder().orderId("o").userId("u").email("e@x")
                .amount(new BigDecimal("10.00")).currency("USD").paymentMethod(PaymentMethod.CARD).idempotencyKey("i")
                .build();

        ResponseEntity<PaymentDtoOut> resp = controller.processPayment("auth", input);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(resp.getBody().getId()).isEqualTo("p-1");
    }

    @Test
    void findById_returnsOk() {
        Payment p = Payment.builder().id("p").build();
        when(useCase.findById("p")).thenReturn(p);
        when(mapper.toDto(p)).thenReturn(PaymentDtoOut.builder().id("p").build());

        ResponseEntity<PaymentDtoOut> resp = controller.findById("auth", "p");
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody().getId()).isEqualTo("p");
    }

    @Test
    void findByOrderId_returnsOk() {
        Payment p = Payment.builder().id("p").build();
        when(useCase.findByOrderId("ord-1")).thenReturn(p);
        when(mapper.toDto(p)).thenReturn(PaymentDtoOut.builder().id("p").build());
        ResponseEntity<PaymentDtoOut> resp = controller.findByOrderId("auth", "ord-1");
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void findByUserId_returnsPaginated() {
        Payment p = Payment.builder().id("p").build();
        PageResult<Payment> result = new PageResult<>(List.of(p), 1L, 1, 0, 10, false, false);
        when(useCase.findByUserId(eq("u"), anyInt(), anyInt(), anyString(), anyBoolean())).thenReturn(result);
        when(mapper.toDto(p)).thenReturn(PaymentDtoOut.builder().id("p").build());

        ResponseEntity<PaginationDtoOut<PaymentDtoOut>> resp = controller.findByUserId("auth", "u", 0, 10, "createdAt",
                false);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody()).isNotNull();
    }

    @Test
    void findAll_applyAllFilters() {
        PageResult<Payment> result = new PageResult<>(List.of(), 0L, 0, 0, 10, false, false);
        when(useCase.findAll(any(), anyInt(), anyInt(), anyString(), anyBoolean())).thenReturn(result);
        controller.findAll("auth", "search", "COMPLETED", "CARD", 0, 20, "createdAt", false);
        verify(useCase).findAll(any(), anyInt(), anyInt(), anyString(), anyBoolean());
    }

    @Test
    void findAll_noFilters() {
        PageResult<Payment> result = new PageResult<>(List.of(), 0L, 0, 0, 10, false, false);
        when(useCase.findAll(any(), anyInt(), anyInt(), anyString(), anyBoolean())).thenReturn(result);
        ResponseEntity<PaginationDtoOut<PaymentDtoOut>> resp = controller.findAll("auth", null, null, null, 0, 20,
                "createdAt", false);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void refundPayment_returnsCreated() {
        PaymentRefund refund = PaymentRefund.builder().id("r-1").build();
        when(useCase.refundPayment(anyString(), any(Money.class), any())).thenReturn(refund);
        when(mapper.toRefundDto(refund)).thenReturn(PaymentRefundDtoOut.builder().id("r-1").build());

        PaymentRefundDtoIn input = PaymentRefundDtoIn.builder().amount(new BigDecimal("5.00")).reason("bad").build();
        ResponseEntity<PaymentRefundDtoOut> resp = controller.refundPayment("auth", "p", input);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    @Test
    void findRefunds_returnsPaginated() {
        PaymentRefund r = PaymentRefund.builder().id("r").build();
        PageResult<PaymentRefund> result = new PageResult<>(List.of(r), 1L, 1, 0, 10, false, false);
        when(useCase.findRefunds(eq("p"), anyInt(), anyInt(), anyString(), anyBoolean())).thenReturn(result);
        when(mapper.toRefundDto(r)).thenReturn(PaymentRefundDtoOut.builder().id("r").build());
        ResponseEntity<PaginationDtoOut<PaymentRefundDtoOut>> resp = controller.findRefunds("auth", "p", 0, 10,
                "createdAt", false);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void createCryptoPayment_returnsCreated() {
        Payment p = Payment.builder().id("p").build();
        when(useCase.createCryptoPayment(anyString(), anyString(), any(Money.class), anyString(),
                any(PaymentMethod.class))).thenReturn(p);
        when(mapper.toDto(p)).thenReturn(PaymentDtoOut.builder().id("p").build());

        CryptoCreateDtoIn input = CryptoCreateDtoIn.builder().orderId("o").userId("u").amount(new BigDecimal("0.01"))
                .currency("USDT").paymentMethod(PaymentMethod.USDT).build();
        ResponseEntity<PaymentDtoOut> resp = controller.createCryptoPayment("auth", input);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    @Test
    void verifyCryptoPayment_returnsOk() {
        Payment p = Payment.builder().id("p").build();
        when(useCase.verifyCryptoPayment("p")).thenReturn(p);
        when(mapper.toDto(p)).thenReturn(PaymentDtoOut.builder().id("p").build());
        ResponseEntity<PaymentDtoOut> resp = controller.verifyCryptoPayment("auth", "p");
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void stripeWebhook_ok() {
        ResponseEntity<Void> resp = controller.stripeWebhook("{}", "sig");
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(useCase).handleWebhook("stripe", "{}", "sig");
    }

    @Test
    void paypalWebhook_ok() {
        ResponseEntity<Void> resp = controller.paypalWebhook("{}", "sig");
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(useCase).handleWebhook("paypal", "{}", "sig");
    }

    @Test
    void cryptoWebhook_ok() {
        ResponseEntity<Void> resp = controller.cryptoWebhook("{}", "sig");
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(useCase).handleWebhook("crypto", "{}", "sig");
    }

    @Test
    void initiatePayPal_withApproveUrl_includesItInBody() {
        PaymentUseCase.PayPalInitiation init = new PaymentUseCase.PayPalInitiation("pay-1", "pp-ord-1",
                "https://approve.paypal/x");
        when(useCase.initiatePayPalPayment(anyString(), anyString(), any(), any(Money.class), anyString(), any()))
                .thenReturn(init);

        PaymentProcessDtoIn input = PaymentProcessDtoIn.builder().orderId("o").userId("u").email("e@x")
                .amount(new BigDecimal("10.00")).currency("USD").paymentMethod(PaymentMethod.PAYPAL).idempotencyKey("i")
                .build();

        ResponseEntity<java.util.Map<String, String>> resp = controller.initiatePayPal("auth", input);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(resp.getBody()).isNotNull().containsEntry("paymentId", "pay-1")
                .containsEntry("paypalOrderId", "pp-ord-1").containsEntry("approveUrl", "https://approve.paypal/x");
    }

    @Test
    void initiatePayPal_nullApproveUrl_omitsKey() {
        // Drives the false branch where the controller skips adding approveUrl
        // when the use-case returned a null URL.
        PaymentUseCase.PayPalInitiation init = new PaymentUseCase.PayPalInitiation("pay-2", "pp-ord-2", null);
        when(useCase.initiatePayPalPayment(anyString(), anyString(), any(), any(Money.class), anyString(), any()))
                .thenReturn(init);

        PaymentProcessDtoIn input = PaymentProcessDtoIn.builder().orderId("o").userId("u").email("e@x")
                .amount(new BigDecimal("5.00")).currency("USD").paymentMethod(PaymentMethod.PAYPAL).build();

        ResponseEntity<java.util.Map<String, String>> resp = controller.initiatePayPal("auth", input);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(resp.getBody()).isNotNull().containsEntry("paymentId", "pay-2")
                .containsEntry("paypalOrderId", "pp-ord-2").doesNotContainKey("approveUrl");
    }

    @Test
    void capturePayPal_returnsOk() {
        Payment p = Payment.builder().id("pay-cap").build();
        when(useCase.capturePayPalPayment("pp-ord-z")).thenReturn(p);
        when(mapper.toDto(p)).thenReturn(PaymentDtoOut.builder().id("pay-cap").build());

        ResponseEntity<PaymentDtoOut> resp = controller.capturePayPal("auth", "pp-ord-z");
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody().getId()).isEqualTo("pay-cap");
        verify(useCase).capturePayPalPayment("pp-ord-z");
    }

    @Test
    void findAll_returnsBodyWithPayments() {
        Payment p = Payment.builder().id("p-all").build();
        PageResult<Payment> result = new PageResult<>(List.of(p), 1L, 1, 0, 10, false, false);
        when(useCase.findAll(any(), anyInt(), anyInt(), anyString(), anyBoolean())).thenReturn(result);
        when(mapper.toDto(p)).thenReturn(PaymentDtoOut.builder().id("p-all").build());

        ResponseEntity<PaginationDtoOut<PaymentDtoOut>> resp = controller.findAll("auth", "search", null, null, 0, 20,
                "createdAt", true);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody()).isNotNull();
    }

    @Test
    void findByUserId_ascendingFlag_passedThrough() {
        // Covers ascending=true branch with non-default sortBy.
        Payment p = Payment.builder().id("p-asc").build();
        PageResult<Payment> result = new PageResult<>(List.of(p), 1L, 1, 0, 5, false, false);
        when(useCase.findByUserId(eq("u-asc"), anyInt(), anyInt(), anyString(), anyBoolean())).thenReturn(result);
        when(mapper.toDto(p)).thenReturn(PaymentDtoOut.builder().id("p-asc").build());

        ResponseEntity<PaginationDtoOut<PaymentDtoOut>> resp = controller.findByUserId("auth", "u-asc", 0, 5, "amount",
                true);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(useCase).findByUserId("u-asc", 0, 5, "amount", true);
    }

    @Test
    void findRefunds_ascendingFlag_passedThrough() {
        PaymentRefund r = PaymentRefund.builder().id("r-asc").build();
        PageResult<PaymentRefund> result = new PageResult<>(List.of(r), 1L, 1, 0, 5, false, false);
        when(useCase.findRefunds(eq("p-x"), anyInt(), anyInt(), anyString(), anyBoolean())).thenReturn(result);
        when(mapper.toRefundDto(r)).thenReturn(PaymentRefundDtoOut.builder().id("r-asc").build());

        ResponseEntity<PaginationDtoOut<PaymentRefundDtoOut>> resp = controller.findRefunds("auth", "p-x", 0, 5,
                "amount", true);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(useCase).findRefunds("p-x", 0, 5, "amount", true);
    }
}
