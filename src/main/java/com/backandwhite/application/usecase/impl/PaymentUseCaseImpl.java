package com.backandwhite.application.usecase.impl;

import static com.backandwhite.common.exception.Message.ENTITY_NOT_FOUND;
import static com.backandwhite.domain.exception.Message.CRYPTO_PAYMENT_EXPIRED;
import static com.backandwhite.domain.exception.Message.PAYMENT_NOT_COMPLETED;
import static com.backandwhite.domain.exception.Message.REFUND_EXCEEDS_AMOUNT;
import static com.backandwhite.domain.exception.Message.UNSUPPORTED_PAYMENT_METHOD;

import com.backandwhite.application.port.out.PaymentEventPort;
import com.backandwhite.application.service.PaymentCurrencyRouter;
import com.backandwhite.application.usecase.PaymentUseCase;
import com.backandwhite.common.currency.CurrencyRateCache;
import com.backandwhite.common.domain.model.PageResult;
import com.backandwhite.common.domain.valueobject.Money;
import com.backandwhite.common.exception.ArgumentException;
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
import com.backandwhite.infrastructure.client.coinbase.CoinbaseCommerceClient;
import com.backandwhite.infrastructure.gateway.config.PaymentGatewayProperties;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.net.Webhook;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Log4j2
@Service
@RequiredArgsConstructor
public class PaymentUseCaseImpl implements PaymentUseCase {

    private final PaymentRepository repository;
    private final List<PaymentGateway> gateways;
    private final PaymentEventPort paymentEventPort;
    private final PaymentCurrencyRouter currencyRouter;
    private final CurrencyRateCache currencyRateCache;
    private final PaymentGatewayProperties gatewayProperties;
    private final CoinbaseCommerceClient coinbaseClient;

    @Override
    @Transactional
    public Payment processPayment(String orderId, String userId, String email, Money amount, String currency,
            PaymentMethod method, String idempotencyKey) {

        // Idempotency check
        if (idempotencyKey != null) {
            Optional<Payment> existing = repository.findByIdempotencyKey(idempotencyKey);
            if (existing.isPresent()) {
                log.info("Returning existing payment for idempotency key={}", idempotencyKey);
                return existing.get();
            }
        }

        PaymentGateway gateway = resolveGateway(method);

        // ── Currency routing ─────────────────────────────────────────
        String displayCurrency = currency != null ? currency : "USD";
        String settlementCurrency = currencyRouter.resolveSettlementCurrency(method);
        Money settlementAmount = amount;
        BigDecimal exchangeRate = BigDecimal.ONE;

        if (!settlementCurrency.equalsIgnoreCase(displayCurrency)) {
            exchangeRate = resolveExchangeRate(displayCurrency, settlementCurrency);
            BigDecimal converted = amount.getAmount().multiply(exchangeRate).setScale(2, RoundingMode.HALF_UP);
            settlementAmount = Money.of(converted);
            log.info("Currency routing: {} {} → {} {} (rate={})", amount.toPlainString(), displayCurrency,
                    settlementAmount.toPlainString(), settlementCurrency, exchangeRate);
        }

        Payment payment = repository.save(Payment.builder().orderId(orderId).userId(userId).amount(amount)
                .currency(displayCurrency).settlementAmount(settlementAmount).settlementCurrency(settlementCurrency)
                .exchangeRate(exchangeRate).status(PaymentStatus.PROCESSING).paymentMethod(method)
                .idempotencyKey(idempotencyKey).build());

        // Publish payment.initiated
        String gwName = gateway.getClass().getSimpleName().replace("GatewayAdapter", "").toLowerCase();
        final String paymentId = payment.getId();
        paymentEventPort.publishPaymentInitiated(paymentId, orderId, userId, amount.toPlainString(), displayCurrency,
                method.name(), gwName);

        // Send settlement amount/currency to the gateway
        PaymentResult result = gateway.process(PaymentRequest.builder().paymentId(payment.getId()).orderId(orderId)
                .userId(userId).amount(settlementAmount.getAmount()).currency(settlementCurrency).method(method)
                .idempotencyKey(idempotencyKey).build());

        if (result.isSuccess()) {
            payment = payment.withStatus(PaymentStatus.COMPLETED).withProviderRef(result.getProviderRef())
                    .withProviderResponse(result.getProviderResponse());
            final Payment completed = payment;
            final String gw = gwName;
            paymentEventPort.publishPaymentConfirmed(completed.getId(), orderId, userId, email, amount.toPlainString(),
                    completed.getCurrency(), method.name(), gw, result.getProviderRef());
        } else {
            payment = payment.withStatus(PaymentStatus.FAILED).withErrorMessage(result.getErrorMessage())
                    .withProviderResponse(result.getProviderResponse());
            final Payment failed = payment;
            final String gw = gwName;
            paymentEventPort.publishPaymentFailed(failed.getId(), orderId, userId, email, amount.toPlainString(),
                    result.getErrorMessage(), gw);
        }

        return repository.update(payment);
    }

    @Override
    @Transactional(readOnly = true)
    public Payment findById(String id) {
        return repository.findById(id).orElseThrow(() -> ENTITY_NOT_FOUND.toEntityNotFound("Payment", id));
    }

    @Override
    @Transactional(readOnly = true)
    public Payment findByOrderId(String orderId) {
        return repository.findByOrderId(orderId)
                .orElseThrow(() -> ENTITY_NOT_FOUND.toEntityNotFound("Payment", orderId));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<Payment> findByUserId(String userId, int page, int size, String sortBy, boolean ascending) {
        String field = (sortBy != null && !sortBy.isBlank()) ? sortBy : "createdAt";
        Pageable pageable = PageRequest.of(page, size,
                ascending ? Sort.by(field).ascending() : Sort.by(field).descending());
        return PageResult.from(repository.findByUserId(userId, pageable));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<Payment> findAll(Map<String, Object> filters, int page, int size, String sortBy,
            boolean ascending) {
        String field = (sortBy != null && !sortBy.isBlank()) ? sortBy : "createdAt";
        Pageable pageable = PageRequest.of(page, size,
                ascending ? Sort.by(field).ascending() : Sort.by(field).descending());
        return PageResult.from(repository.findAll(filters, pageable));
    }

    @Override
    @Transactional
    public PaymentRefund refundPayment(String paymentId, Money amount, String reason) {
        Payment payment = findById(paymentId);

        if (payment.getStatus() != PaymentStatus.COMPLETED && payment.getStatus() != PaymentStatus.PARTIALLY_REFUNDED) {
            throw PAYMENT_NOT_COMPLETED.toBusinessException(paymentId);
        }

        // Check refund amount against remaining
        Money refundable = payment.getAmount();
        if (amount.isGreaterThan(refundable)) {
            throw REFUND_EXCEEDS_AMOUNT.toBusinessException(amount.getAmount(), refundable.getAmount());
        }

        PaymentGateway gateway = resolveGateway(payment.getPaymentMethod());

        RefundResult result = gateway.refund(RefundRequest.builder().paymentId(paymentId)
                .providerRef(payment.getProviderRef()).amount(amount.getAmount()).reason(reason).build());

        PaymentRefund refund = repository.saveRefund(PaymentRefund.builder().paymentId(paymentId).amount(amount)
                .status(result.isSuccess() ? RefundStatus.COMPLETED : RefundStatus.FAILED).reason(reason)
                .providerRef(result.getProviderRef()).build());

        // Publish refund events
        if (result.isSuccess()) {
            paymentEventPort.publishRefundCompleted(paymentId, refund.getId(), payment.getOrderId(),
                    payment.getUserId(), amount.toPlainString());
        }

        // Update payment status
        if (result.isSuccess()) {
            PaymentStatus newStatus = amount.isGreaterThanOrEqual(payment.getAmount())
                    ? PaymentStatus.REFUNDED
                    : PaymentStatus.PARTIALLY_REFUNDED;
            repository.update(payment.withStatus(newStatus));
        }

        return refund;
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<PaymentRefund> findRefunds(String paymentId, int page, int size, String sortBy,
            boolean ascending) {
        String field = (sortBy != null && !sortBy.isBlank()) ? sortBy : "createdAt";
        Pageable pageable = PageRequest.of(page, size,
                ascending ? Sort.by(field).ascending() : Sort.by(field).descending());
        return PageResult.from(repository.findRefundsByPaymentId(paymentId, pageable));
    }

    @Override
    @Transactional
    public Payment createCryptoPayment(String orderId, String userId, Money amount, String currency,
            PaymentMethod method) {
        PaymentGateway gateway = resolveGateway(method);

        // ── Currency routing for crypto ──────────────────────────────
        String displayCurrency = currency != null ? currency : "USD";
        String settlementCurrency = currencyRouter.resolveSettlementCurrency(method);
        Money settlementAmount = amount;
        BigDecimal exchangeRate = BigDecimal.ONE;

        if (!settlementCurrency.equalsIgnoreCase(displayCurrency)) {
            exchangeRate = resolveExchangeRate(displayCurrency, settlementCurrency);
            BigDecimal converted = amount.getAmount().multiply(exchangeRate).setScale(8, RoundingMode.HALF_UP); // 8
                                                                                                                // decimals
                                                                                                                // for
                                                                                                                // crypto
            settlementAmount = Money.of(converted);
            log.info("Crypto currency routing: {} {} → {} {} (rate={})", amount.toPlainString(), displayCurrency,
                    settlementAmount.toPlainString(), settlementCurrency, exchangeRate);
        }

        Payment payment = repository.save(Payment.builder().orderId(orderId).userId(userId).amount(amount)
                .currency(displayCurrency).settlementAmount(settlementAmount).settlementCurrency(settlementCurrency)
                .exchangeRate(exchangeRate).status(PaymentStatus.PENDING).paymentMethod(method)
                .cryptoExpiresAt(Instant.now().plus(30, ChronoUnit.MINUTES)).build());

        PaymentResult result = gateway
                .process(PaymentRequest.builder().paymentId(payment.getId()).orderId(orderId).userId(userId)
                        .amount(settlementAmount.getAmount()).currency(settlementCurrency).method(method).build());

        payment = payment.withProviderRef(result.getProviderRef()).withCryptoAddress(result.getCryptoAddress())
                .withQrCodeUrl(result.getQrCodeUrl()).withProviderResponse(result.getProviderResponse());

        return repository.update(payment);
    }

    @Override
    @Transactional
    public Payment verifyCryptoPayment(String paymentId) {
        Payment payment = findById(paymentId);

        if (payment.getCryptoExpiresAt() != null && Instant.now().isAfter(payment.getCryptoExpiresAt())) {
            repository.update(payment.withStatus(PaymentStatus.FAILED).withErrorMessage("Payment expired"));
            throw CRYPTO_PAYMENT_EXPIRED.toBusinessException(paymentId);
        }

        try {
            Map<String, Object> charge = coinbaseClient.getCharge(payment.getProviderRef());
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> timeline = (List<Map<String, Object>>) charge.get("timeline");

            boolean completed = timeline != null
                    && timeline.stream().anyMatch(event -> "COMPLETED".equals(event.get("status")));
            boolean pending = timeline != null
                    && timeline.stream().anyMatch(event -> "PENDING".equals(event.get("status")));

            if (completed) {
                Payment updated = payment.withStatus(PaymentStatus.COMPLETED);
                paymentEventPort.publishPaymentConfirmed(payment.getId(), payment.getOrderId(), payment.getUserId(),
                        null, payment.getAmount().toPlainString(), payment.getCurrency(),
                        payment.getPaymentMethod().name(), "coinbase", payment.getProviderRef());
                return repository.update(updated);
            }

            if (pending) {
                log.info("Crypto payment paymentId={} is pending blockchain confirmation", paymentId);
            }

            return payment;

        } catch (RuntimeException e) {
            log.warn("Could not verify crypto payment paymentId={}: {}", paymentId, e.getMessage());
            return payment;
        }
    }

    @Override
    @Transactional
    public void handleWebhook(String provider, String payload, String signature) {
        log.info("Received webhook from provider={} payloadLength={}", provider, payload.length());

        switch (provider.toLowerCase()) {
            case "stripe" -> handleStripeWebhook(payload, signature);
            case "paypal" -> handlePayPalWebhook(payload, signature);
            case "crypto" -> handleCoinbaseWebhook(payload, signature);
            default -> log.warn("Unknown webhook provider: {}", provider);
        }
    }

    private void handleStripeWebhook(String payload, String signature) {
        if (signature == null || signature.isBlank()) {
            throw new ArgumentException("SE001", "Missing Stripe-Signature header");
        }
        try {
            com.stripe.model.Event event = Webhook.constructEvent(payload, signature,
                    gatewayProperties.getStripe().getWebhookSecret());
            log.info("Stripe webhook event type={}", event.getType());
        } catch (SignatureVerificationException e) {
            log.warn("Invalid Stripe webhook signature: {}", e.getMessage());
            throw new ArgumentException("SE002", "Invalid Stripe webhook signature");
        }
    }

    private void handlePayPalWebhook(String payload, String signature) {
        if (signature == null || signature.isBlank()) {
            throw new ArgumentException("SE003", "Missing PayPal-Transmission-Sig header");
        }
        try {
            String secret = gatewayProperties.getPaypal().getClientSecret();
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            String computed = HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
            if (!computed.equalsIgnoreCase(signature)) {
                throw new ArgumentException("SE004", "Invalid PayPal webhook signature");
            }
            log.info("PayPal webhook signature verified");
        } catch (GeneralSecurityException e) {
            throw new ArgumentException("SE005", "PayPal signature verification error: " + e.getMessage());
        }
    }

    private void handleCoinbaseWebhook(String payload, String signature) {
        if (signature == null || signature.isBlank()) {
            throw new ArgumentException("SE006", "Missing X-Webhook-Signature header");
        }
        try {
            String secret = gatewayProperties.getCrypto().getCoinbase().getWebhookSecret();
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            String computed = HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
            if (!computed.equalsIgnoreCase(signature)) {
                throw new ArgumentException("SE007", "Invalid Coinbase webhook signature");
            }
            log.info("Coinbase webhook signature verified");
        } catch (GeneralSecurityException e) {
            throw new ArgumentException("SE008", "Coinbase signature verification error: " + e.getMessage());
        }
    }

    private PaymentGateway resolveGateway(PaymentMethod method) {
        return gateways.stream().filter(g -> g.supports(method)).findFirst()
                .orElseThrow(() -> UNSUPPORTED_PAYMENT_METHOD.toBusinessException(method));
    }

    /**
     * Compute the exchange rate from {@code displayCurrency} to
     * {@code settlementCurrency}.
     * <p>
     * CurrencyRateCache stores rates as "1 USD = X target". So:
     * <ul>
     * <li>displayCurrency → USD → settlementCurrency</li>
     * <li>rate = rateSettlement / rateDisplay (both vs USD)</li>
     * </ul>
     * For crypto (USDT ≈ 1 USD, BTC via CoinGecko-style rate) we treat USDT as 1:1
     * with USD and BTC rate as stored.
     */
    private BigDecimal resolveExchangeRate(String from, String to) {
        // USDT ≈ USD peg
        String normFrom = "USDT".equalsIgnoreCase(from) ? "USD" : from;
        String normTo = "USDT".equalsIgnoreCase(to) ? "USD" : to;

        if (normFrom.equalsIgnoreCase(normTo)) {
            return BigDecimal.ONE;
        }

        // CurrencyRateCache.getRate(code) returns rate relative to USD base
        // e.g. getRate("EUR") = 0.92 → 1 USD = 0.92 EUR
        // getRate("BRL") = 5.10 → 1 USD = 5.10 BRL
        BigDecimal rateFrom = currencyRateCache.getRate(normFrom); // 1 USD = X from
        BigDecimal rateTo = currencyRateCache.getRate(normTo); // 1 USD = X to

        if (rateFrom.compareTo(BigDecimal.ZERO) == 0) {
            log.warn("Exchange rate for '{}' is zero, defaulting to 1", normFrom);
            return BigDecimal.ONE;
        }

        // from → USD → to ⇒ amount_to = amount_from * (rateTo / rateFrom)
        return rateTo.divide(rateFrom, 8, RoundingMode.HALF_UP);
    }
}
