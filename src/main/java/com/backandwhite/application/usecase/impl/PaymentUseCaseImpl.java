package com.backandwhite.application.usecase.impl;

import com.backandwhite.common.domain.model.PageResult;
import com.backandwhite.application.usecase.PaymentUseCase;
import com.backandwhite.domain.gateway.PaymentGateway;
import com.backandwhite.domain.gateway.PaymentRequest;
import com.backandwhite.domain.gateway.PaymentResult;
import com.backandwhite.domain.gateway.RefundRequest;
import com.backandwhite.domain.gateway.RefundResult;
import com.backandwhite.domain.model.Payment;
import com.backandwhite.domain.model.PaymentRefund;
import com.backandwhite.domain.repository.PaymentRepository;
import com.backandwhite.domain.valueobject.PaymentMethod;
import com.backandwhite.domain.valueobject.PaymentStatus;
import com.backandwhite.domain.valueobject.RefundStatus;
import com.backandwhite.application.port.out.PaymentEventPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.backandwhite.common.exception.Message.ENTITY_NOT_FOUND;
import static com.backandwhite.domain.exception.Message.CRYPTO_PAYMENT_EXPIRED;
import static com.backandwhite.domain.exception.Message.PAYMENT_NOT_COMPLETED;
import static com.backandwhite.domain.exception.Message.PAYMENT_PROCESSING_FAILED;
import static com.backandwhite.domain.exception.Message.REFUND_EXCEEDS_AMOUNT;
import static com.backandwhite.domain.exception.Message.UNSUPPORTED_PAYMENT_METHOD;

@Log4j2
@Service
@RequiredArgsConstructor
public class PaymentUseCaseImpl implements PaymentUseCase {

    private final PaymentRepository repository;
    private final List<PaymentGateway> gateways;
    private final PaymentEventPort paymentEventPort;

    @Override
    @Transactional
    public Payment processPayment(String orderId, String userId, String email, BigDecimal amount,
            String currency, PaymentMethod method, String idempotencyKey) {

        // Idempotency check
        if (idempotencyKey != null) {
            Optional<Payment> existing = repository.findByIdempotencyKey(idempotencyKey);
            if (existing.isPresent()) {
                log.info("Returning existing payment for idempotency key={}", idempotencyKey);
                return existing.get();
            }
        }

        PaymentGateway gateway = resolveGateway(method);

        Payment payment = repository.save(Payment.builder()
                .orderId(orderId)
                .userId(userId)
                .amount(amount)
                .currency(currency != null ? currency : "EUR")
                .status(PaymentStatus.PROCESSING)
                .paymentMethod(method)
                .idempotencyKey(idempotencyKey)
                .build());

        // Publish payment.initiated
        String gwName = gateway.getClass().getSimpleName().replace("GatewayAdapter", "").toLowerCase();
        final String paymentId = payment.getId();
        final String cur = payment.getCurrency();
        paymentEventPort.publishPaymentInitiated(
                paymentId, orderId, userId,
                amount.toPlainString(), cur,
                method.name(), gwName);

        PaymentResult result = gateway.process(PaymentRequest.builder()
                .paymentId(payment.getId())
                .orderId(orderId)
                .userId(userId)
                .amount(amount)
                .currency(payment.getCurrency())
                .method(method)
                .idempotencyKey(idempotencyKey)
                .build());

        if (result.isSuccess()) {
            payment = payment
                    .withStatus(PaymentStatus.COMPLETED)
                    .withProviderRef(result.getProviderRef())
                    .withProviderResponse(result.getProviderResponse());
            final Payment completed = payment;
            final String gw = gwName;
            paymentEventPort.publishPaymentConfirmed(
                    completed.getId(), orderId, userId, email,
                    amount.toPlainString(), completed.getCurrency(),
                    method.name(), gw, result.getProviderRef());
        } else {
            payment = payment
                    .withStatus(PaymentStatus.FAILED)
                    .withErrorMessage(result.getErrorMessage())
                    .withProviderResponse(result.getProviderResponse());
            final Payment failed = payment;
            final String gw = gwName;
            paymentEventPort.publishPaymentFailed(
                    failed.getId(), orderId, userId, email,
                    amount.toPlainString(), result.getErrorMessage(), gw);
        }

        return repository.update(payment);
    }

    @Override
    @Transactional(readOnly = true)
    public Payment findById(String id) {
        return repository.findById(id)
                .orElseThrow(() -> ENTITY_NOT_FOUND.toEntityNotFound("Payment", id));
    }

    @Override
    @Transactional(readOnly = true)
    public Payment findByOrderId(String orderId) {
        return repository.findByOrderId(orderId)
                .orElseThrow(() -> ENTITY_NOT_FOUND.toEntityNotFound("Payment", orderId));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<Payment> findByUserId(String userId, int page, int size,
            String sortBy, boolean ascending) {
        String field = (sortBy != null && !sortBy.isBlank()) ? sortBy : "createdAt";
        Pageable pageable = PageRequest.of(page, size,
                ascending ? Sort.by(field).ascending() : Sort.by(field).descending());
        return PageResult.from(repository.findByUserId(userId, pageable));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<Payment> findAll(Map<String, Object> filters, int page, int size,
            String sortBy, boolean ascending) {
        String field = (sortBy != null && !sortBy.isBlank()) ? sortBy : "createdAt";
        Pageable pageable = PageRequest.of(page, size,
                ascending ? Sort.by(field).ascending() : Sort.by(field).descending());
        return PageResult.from(repository.findAll(filters, pageable));
    }

    @Override
    @Transactional
    public PaymentRefund refundPayment(String paymentId, BigDecimal amount, String reason) {
        Payment payment = findById(paymentId);

        if (payment.getStatus() != PaymentStatus.COMPLETED
                && payment.getStatus() != PaymentStatus.PARTIALLY_REFUNDED) {
            throw PAYMENT_NOT_COMPLETED.toBusinessException(paymentId);
        }

        // Check refund amount against remaining
        BigDecimal refundable = payment.getAmount();
        if (amount.compareTo(refundable) > 0) {
            throw REFUND_EXCEEDS_AMOUNT.toBusinessException(amount, refundable);
        }

        PaymentGateway gateway = resolveGateway(payment.getPaymentMethod());

        RefundResult result = gateway.refund(RefundRequest.builder()
                .paymentId(paymentId)
                .providerRef(payment.getProviderRef())
                .amount(amount)
                .reason(reason)
                .build());

        PaymentRefund refund = repository.saveRefund(PaymentRefund.builder()
                .paymentId(paymentId)
                .amount(amount)
                .status(result.isSuccess() ? RefundStatus.COMPLETED : RefundStatus.FAILED)
                .reason(reason)
                .providerRef(result.getProviderRef())
                .build());

        // Publish refund events
        if (result.isSuccess()) {
            paymentEventPort.publishRefundCompleted(
                    paymentId, refund.getId(), payment.getOrderId(),
                    payment.getUserId(), amount.toPlainString());
        }

        // Update payment status
        if (result.isSuccess()) {
            PaymentStatus newStatus = amount.compareTo(payment.getAmount()) >= 0
                    ? PaymentStatus.REFUNDED
                    : PaymentStatus.PARTIALLY_REFUNDED;
            repository.update(payment.withStatus(newStatus));
        }

        return refund;
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<PaymentRefund> findRefunds(String paymentId, int page, int size,
            String sortBy, boolean ascending) {
        String field = (sortBy != null && !sortBy.isBlank()) ? sortBy : "createdAt";
        Pageable pageable = PageRequest.of(page, size,
                ascending ? Sort.by(field).ascending() : Sort.by(field).descending());
        return PageResult.from(repository.findRefundsByPaymentId(paymentId, pageable));
    }

    @Override
    @Transactional
    public Payment createCryptoPayment(String orderId, String userId, BigDecimal amount,
            String currency, PaymentMethod method) {
        PaymentGateway gateway = resolveGateway(method);

        Payment payment = repository.save(Payment.builder()
                .orderId(orderId)
                .userId(userId)
                .amount(amount)
                .currency(currency != null ? currency : "EUR")
                .status(PaymentStatus.PENDING)
                .paymentMethod(method)
                .cryptoExpiresAt(Instant.now().plus(30, ChronoUnit.MINUTES))
                .build());

        PaymentResult result = gateway.process(PaymentRequest.builder()
                .paymentId(payment.getId())
                .orderId(orderId)
                .userId(userId)
                .amount(amount)
                .currency(payment.getCurrency())
                .method(method)
                .build());

        payment = payment
                .withProviderRef(result.getProviderRef())
                .withCryptoAddress(result.getCryptoAddress())
                .withQrCodeUrl(result.getQrCodeUrl())
                .withProviderResponse(result.getProviderResponse());

        return repository.update(payment);
    }

    @Override
    @Transactional
    public Payment verifyCryptoPayment(String paymentId) {
        Payment payment = findById(paymentId);

        if (payment.getCryptoExpiresAt() != null && Instant.now().isAfter(payment.getCryptoExpiresAt())) {
            repository.update(payment.withStatus(PaymentStatus.FAILED)
                    .withErrorMessage("Payment expired"));
            throw CRYPTO_PAYMENT_EXPIRED.toBusinessException(paymentId);
        }

        // TODO: Check blockchain/provider for actual confirmation
        return payment;
    }

    @Override
    @Transactional
    public void handleWebhook(String provider, String payload, String signature) {
        log.info("Received webhook from provider={} payload length={}", provider, payload.length());
        // TODO: Implement webhook signature verification and payment status updates
    }

    private PaymentGateway resolveGateway(PaymentMethod method) {
        return gateways.stream()
                .filter(g -> g.supports(method))
                .findFirst()
                .orElseThrow(() -> UNSUPPORTED_PAYMENT_METHOD.toBusinessException(method));
    }
}
