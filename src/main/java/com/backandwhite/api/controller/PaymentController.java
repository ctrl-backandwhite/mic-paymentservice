package com.backandwhite.api.controller;

import com.backandwhite.api.dto.PaginationDtoOut;
import com.backandwhite.api.dto.in.CryptoCreateDtoIn;
import com.backandwhite.api.dto.in.PaymentProcessDtoIn;
import com.backandwhite.api.dto.in.PaymentRefundDtoIn;
import com.backandwhite.api.dto.out.PaymentDtoOut;
import com.backandwhite.api.dto.out.PaymentRefundDtoOut;
import com.backandwhite.api.mapper.PaymentApiMapper;
import com.backandwhite.api.util.PageableUtils;
import com.backandwhite.application.usecase.PaymentUseCase;
import com.backandwhite.common.constants.AppConstants;
import com.backandwhite.common.domain.model.PageResult;
import com.backandwhite.common.domain.valueobject.Money;
import com.backandwhite.common.security.annotation.NxAdmin;
import com.backandwhite.common.security.annotation.NxPublic;
import com.backandwhite.common.security.annotation.NxUser;
import com.backandwhite.domain.model.Payment;
import com.backandwhite.domain.model.PaymentRefund;
import jakarta.validation.Valid;
import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/payments")
public class PaymentController {

    private final PaymentUseCase useCase;
    private final PaymentApiMapper mapper;

    @PostMapping("/process")
    @NxUser
    public ResponseEntity<PaymentDtoOut> processPayment(@RequestHeader(AppConstants.HEADER_NX036_AUTH) String nxAuth,
            @Valid @RequestBody PaymentProcessDtoIn dto) {
        Payment payment = useCase.processPayment(dto.getOrderId(), dto.getUserId(), dto.getEmail(),
                Money.of(dto.getAmount()), dto.getCurrency(), dto.getPaymentMethod(), dto.getIdempotencyKey());
        return ResponseEntity.status(HttpStatus.CREATED).body(mapper.toDto(payment));
    }

    @GetMapping("/{id}")
    @NxUser
    public ResponseEntity<PaymentDtoOut> findById(@RequestHeader(AppConstants.HEADER_NX036_AUTH) String nxAuth,
            @PathVariable String id) {
        return ResponseEntity.ok(mapper.toDto(useCase.findById(id)));
    }

    @GetMapping("/order/{orderId}")
    @NxUser
    public ResponseEntity<PaymentDtoOut> findByOrderId(@RequestHeader(AppConstants.HEADER_NX036_AUTH) String nxAuth,
            @PathVariable String orderId) {
        return ResponseEntity.ok(mapper.toDto(useCase.findByOrderId(orderId)));
    }

    @GetMapping("/user/{userId}")
    @NxUser
    public ResponseEntity<PaginationDtoOut<PaymentDtoOut>> findByUserId(
            @RequestHeader(AppConstants.HEADER_NX036_AUTH) String nxAuth, @PathVariable String userId,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "false") boolean ascending) {
        PageResult<Payment> result = useCase.findByUserId(userId, page, size, sortBy, ascending);
        return ResponseEntity.ok(PageableUtils.toResponse(result, mapper::toDto));
    }

    @GetMapping
    @NxAdmin
    public ResponseEntity<PaginationDtoOut<PaymentDtoOut>> findAll(
            @RequestHeader(AppConstants.HEADER_NX036_AUTH) String nxAuth, @RequestParam(required = false) String search,
            @RequestParam(required = false) String status, @RequestParam(required = false) String paymentMethod,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "false") boolean ascending) {
        Map<String, Object> filters = new HashMap<>();
        if (search != null)
            filters.put("search", search);
        if (status != null)
            filters.put("status", status);
        if (paymentMethod != null)
            filters.put("paymentMethod", paymentMethod);
        PageResult<Payment> result = useCase.findAll(filters, page, size, sortBy, ascending);
        return ResponseEntity.ok(PageableUtils.toResponse(result, mapper::toDto));
    }

    @PostMapping("/{id}/refund")
    @NxAdmin
    public ResponseEntity<PaymentRefundDtoOut> refundPayment(
            @RequestHeader(AppConstants.HEADER_NX036_AUTH) String nxAuth, @PathVariable String id,
            @Valid @RequestBody PaymentRefundDtoIn dto) {
        PaymentRefund refund = useCase.refundPayment(id, Money.of(dto.getAmount()), dto.getReason());
        return ResponseEntity.status(HttpStatus.CREATED).body(mapper.toRefundDto(refund));
    }

    @GetMapping("/{id}/refunds")
    @NxUser
    public ResponseEntity<PaginationDtoOut<PaymentRefundDtoOut>> findRefunds(
            @RequestHeader(AppConstants.HEADER_NX036_AUTH) String nxAuth, @PathVariable String id,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "false") boolean ascending) {
        PageResult<PaymentRefund> result = useCase.findRefunds(id, page, size, sortBy, ascending);
        return ResponseEntity.ok(PageableUtils.toResponse(result, mapper::toRefundDto));
    }

    // --- Crypto endpoints ---

    @PostMapping("/crypto/create")
    @NxUser
    public ResponseEntity<PaymentDtoOut> createCryptoPayment(
            @RequestHeader(AppConstants.HEADER_NX036_AUTH) String nxAuth, @Valid @RequestBody CryptoCreateDtoIn dto) {
        Payment payment = useCase.createCryptoPayment(dto.getOrderId(), dto.getUserId(), Money.of(dto.getAmount()),
                dto.getCurrency(), dto.getPaymentMethod());
        return ResponseEntity.status(HttpStatus.CREATED).body(mapper.toDto(payment));
    }

    @GetMapping("/crypto/{id}/verify")
    @NxUser
    public ResponseEntity<PaymentDtoOut> verifyCryptoPayment(
            @RequestHeader(AppConstants.HEADER_NX036_AUTH) String nxAuth, @PathVariable String id) {
        return ResponseEntity.ok(mapper.toDto(useCase.verifyCryptoPayment(id)));
    }

    // --- Webhook endpoints (externos, sin X-nx036-auth requerido) ---

    @PostMapping("/webhook/stripe")
    @NxPublic
    public ResponseEntity<Void> stripeWebhook(@RequestBody String payload,
            @RequestHeader(value = "Stripe-Signature", required = false) String signature) {
        useCase.handleWebhook("stripe", payload, signature);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/webhook/paypal")
    @NxPublic
    public ResponseEntity<Void> paypalWebhook(@RequestBody String payload,
            @RequestHeader(value = "PayPal-Transmission-Sig", required = false) String signature) {
        useCase.handleWebhook("paypal", payload, signature);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/webhook/crypto")
    @NxPublic
    public ResponseEntity<Void> cryptoWebhook(@RequestBody String payload,
            @RequestHeader(value = "X-Webhook-Signature", required = false) String signature) {
        useCase.handleWebhook("crypto", payload, signature);
        return ResponseEntity.ok().build();
    }
}
