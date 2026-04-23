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

    /**
     * When {@code true}, skip the real payment gateway (Stripe / PayPal / Coinbase)
     * and synthesise a successful {@link PaymentResult}. The rest of the flow runs
     * exactly as in production: ledger inbound is recorded, snapshots persisted, CJ
     * pipeline kicked off (which itself can be in mock mode). Useful to drive
     * end-to-end tests without actually moving money.
     */
    @org.springframework.beans.factory.annotation.Value("${app.payment.mock-mode:false}")
    private boolean paymentMockModeFlag;

    /**
     * Effective mock mode by payment method.
     *
     * Business logic: - CARD payment: Real gateway ONLY if both (a) Stripe API key
     * configured AND (b) PaymentRequest.preparedPaymentMethod = true (frontend sent
     * a Stripe token). If frontend hasn't prepared a token, fall back to mock to
     * avoid "PaymentIntent missing payment method" errors. - PAYPAL / USDT / BTC:
     * Always mock (no outbound real charge).
     */
    private boolean paymentMockMode(PaymentMethod method, PaymentRequest request) {
        if (paymentMockModeFlag) {
            return true;
        }

        // CARD requires both Stripe API key AND a prepared payment method token from
        // frontend
        if (method == PaymentMethod.CARD) {
            boolean stripeConfigured = hasText(gatewayProperties.getStripe().getApiKey());
            boolean hasPaymentMethodToken = request != null && hasText(request.getStripePaymentMethodId());
            if (stripeConfigured && hasPaymentMethodToken) {
                log.debug("CARD payment: Stripe configured and payment method token present — using real gateway");
                return false; // Use real gateway
            }
            if (!hasPaymentMethodToken) {
                log.debug("CARD payment: payment method token not provided by frontend — falling back to mock");
            }
            return true; // Use mock
        }

        // PAYPAL / USDT / BTC => always mock (no outbound real charge)
        return true;
    }

    /**
     * Overload for refund/crypto methods where PaymentRequest is not available. For
     * CARD: always use mock (since we don't have token info in these contexts).
     */
    private boolean paymentMockMode(PaymentMethod method) {
        return paymentMockMode(method, null);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    @jakarta.annotation.PostConstruct
    void logMockState() {
        boolean stripeConfigured = hasText(gatewayProperties.getStripe().getApiKey());
        if (paymentMockModeFlag) {
            log.warn("╔═══════════════════════════════════════════════════════════════╗");
            log.warn("║  PAYMENT MOCK MODE ACTIVE — no real charges will be made.   ║");
            log.warn("║  Flag app.payment.mock-mode=true                             ║");
            log.warn("╚═══════════════════════════════════════════════════════════════╝");
            return;
        }

        if (stripeConfigured) {
            log.info("::> Hybrid payment mode ACTIVE — CARD goes real via Stripe; PAYPAL/USDT/BTC stay mocked.");
        } else {
            log.warn("╔═══════════════════════════════════════════════════════════════╗");
            log.warn("║  STRIPE_API_KEY missing — all methods run in mock mode.     ║");
            log.warn("║  Configure STRIPE_API_KEY to enable real CARD payments.     ║");
            log.warn("╚═══════════════════════════════════════════════════════════════╝");
        }
    }

    @Override
    @Transactional
    public Payment processPayment(String orderId, String userId, String email, Money amount, String currency,
            PaymentMethod method, String idempotencyKey, String stripePaymentMethodId) {

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

        // Build the payment request (Stripe payment method token is provided by
        // frontend if available)
        PaymentRequest paymentRequest = PaymentRequest.builder().paymentId(payment.getId()).orderId(orderId)
                .userId(userId).email(email).amount(settlementAmount.getAmount()).currency(settlementCurrency)
                .method(method).idempotencyKey(idempotencyKey).stripePaymentMethodId(stripePaymentMethodId).build();

        // Mock-mode short-circuit: skip the real gateway call but keep the rest of
        // the flow identical — downstream (order service) cannot tell the difference.
        PaymentResult result;
        if (paymentMockMode(method, paymentRequest)) {
            String mockRef = "mock_" + gwName + "_" + java.util.UUID.randomUUID();
            log.warn("::> PAYMENT MOCK MODE — skipping real gateway, synthesising success ref={}", mockRef);
            result = PaymentResult.builder().success(true).providerRef(mockRef)
                    .providerResponse(java.util.Map.of("mockMode", true, "gateway", gwName)).build();
        } else {
            // Send settlement amount/currency to the gateway (with prepared payment method
            // token)
            result = gateway.process(paymentRequest);
        }

        if (result.isSuccess()) {
            payment = payment.withStatus(PaymentStatus.COMPLETED).withProviderRef(result.getProviderRef())
                    .withProviderResponse(result.getProviderResponse());
            final Payment completed = payment;
            final String gw = gwName;
            paymentEventPort.publishPaymentConfirmed(completed.getId(), orderId, userId, email, amount.toPlainString(),
                    completed.getCurrency(), method.name(), gw, result.getProviderRef());
            return repository.update(payment);
        }

        // Gateway rejected the payment — persist the FAILED row, fire the
        // event so the order service can compensate asynchronously, and then
        // throw so the HTTP caller sees a 4xx. The frontend needs this to
        // stop the checkout flow before it fires confirmOrder on a doomed
        // order. Returning 200 + status=FAILED invited a nasty race where
        // the consumer cancelled the order mid-confirm.
        payment = payment.withStatus(PaymentStatus.FAILED).withErrorMessage(result.getErrorMessage())
                .withProviderResponse(result.getProviderResponse());
        final Payment failed = payment;
        final String gw = gwName;
        paymentEventPort.publishPaymentFailed(failed.getId(), orderId, userId, email, amount.toPlainString(),
                result.getErrorMessage(), gw);
        repository.update(payment);
        throw PAYMENT_NOT_COMPLETED.toBusinessException(orderId,
                result.getErrorMessage() != null ? result.getErrorMessage() : "Gateway rejected the payment");
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

        RefundResult result;
        if (paymentMockMode(payment.getPaymentMethod())) {
            String mockRef = "mock_refund_" + payment.getPaymentMethod().name().toLowerCase() + "_"
                    + java.util.UUID.randomUUID();
            log.warn("::> PAYMENT MOCK MODE — skipping real refund, synthesising success ref={}", mockRef);
            result = RefundResult.builder().success(true).providerRef(mockRef).build();
        } else {
            result = gateway.refund(RefundRequest.builder().paymentId(paymentId).providerRef(payment.getProviderRef())
                    .amount(amount.getAmount()).reason(reason).build());
        }

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

        String gwName = gateway.getClass().getSimpleName().replace("GatewayAdapter", "").toLowerCase();
        PaymentResult result;
        if (paymentMockMode(method)) {
            String mockRef = "mock_" + gwName + "_" + java.util.UUID.randomUUID();
            log.warn("::> PAYMENT MOCK MODE — skipping real crypto gateway, synthesising ref={}", mockRef);
            result = PaymentResult.builder().success(true).providerRef(mockRef)
                    .cryptoAddress("mock_" + method.name().toLowerCase() + "_address")
                    .qrCodeUrl("mock://" + method.name().toLowerCase() + "/" + payment.getId())
                    .providerResponse(java.util.Map.of("mockMode", true, "gateway", gwName)).build();
        } else {
            result = gateway.process(PaymentRequest.builder().paymentId(payment.getId()).orderId(orderId).userId(userId)
                    .amount(settlementAmount.getAmount()).currency(settlementCurrency).method(method).build());
        }

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
        com.stripe.model.Event event;
        try {
            event = Webhook.constructEvent(payload, signature, gatewayProperties.getStripe().getWebhookSecret());
            log.info("Stripe webhook event type={} id={}", event.getType(), event.getId());
        } catch (SignatureVerificationException e) {
            log.warn("Invalid Stripe webhook signature: {}", e.getMessage());
            throw new ArgumentException("SE002", "Invalid Stripe webhook signature");
        }

        // Only act on PaymentIntent confirmations — other event types are logged and
        // ignored. Async flows (3DS redirects, delayed capture) land here and drive
        // the order from PENDING → COMPLETED via the same Kafka event as the
        // synchronous path, so the order service consumer can reconcile uniformly.
        if (!"payment_intent.succeeded".equals(event.getType())) {
            return;
        }

        var deserializer = event.getDataObjectDeserializer();
        var dataObject = deserializer != null
                ? deserializer.getObject()
                : java.util.Optional.<com.stripe.model.StripeObject>empty();
        if (dataObject.isEmpty() || !(dataObject.get() instanceof com.stripe.model.PaymentIntent pi)) {
            log.warn("Stripe webhook payment_intent.succeeded missing deserialised object");
            return;
        }

        String orderId = pi.getMetadata() != null ? pi.getMetadata().get("orderId") : null;
        if (orderId == null || orderId.isBlank()) {
            log.warn("Stripe payment_intent.succeeded has no metadata.orderId — cannot correlate");
            return;
        }

        Payment payment = repository.findByOrderId(orderId).orElse(null);
        if (payment == null) {
            log.warn("Stripe payment_intent.succeeded for orderId={} but no local Payment row exists", orderId);
            return;
        }
        if (payment.getStatus() == PaymentStatus.COMPLETED) {
            log.info("Stripe webhook duplicate: payment {} already COMPLETED", payment.getId());
            return;
        }

        payment = payment.withStatus(PaymentStatus.COMPLETED).withProviderRef(pi.getId());
        repository.update(payment);

        BigDecimal amount = payment.getAmount() != null && payment.getAmount().getAmount() != null
                ? payment.getAmount().getAmount()
                : BigDecimal.ZERO;
        paymentEventPort.publishPaymentConfirmed(payment.getId(), orderId, payment.getUserId(), null,
                amount.toPlainString(), payment.getCurrency(),
                payment.getPaymentMethod() != null ? payment.getPaymentMethod().name() : "CARD", "stripe", pi.getId());
        log.info("::> Stripe async confirmation published PaymentConfirmed for orderId={} paymentId={}", orderId,
                payment.getId());
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
