package com.backandwhite.application.usecase.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.backandwhite.application.port.out.PaymentEventPort;
import com.backandwhite.application.service.PaymentCurrencyRouter;
import com.backandwhite.common.currency.CurrencyRateCache;
import com.backandwhite.common.domain.model.PageResult;
import com.backandwhite.common.domain.valueobject.Money;
import com.backandwhite.common.exception.ArgumentException;
import com.backandwhite.common.exception.BusinessException;
import com.backandwhite.common.exception.EntityNotFoundException;
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
import com.backandwhite.infrastructure.client.coinbase.CoinbaseCommerceClient;
import com.backandwhite.infrastructure.gateway.config.PaymentGatewayProperties;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Event;
import com.stripe.net.Webhook;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

class PaymentUseCaseImplTest {

    private PaymentRepository repository;
    private PaymentGateway cardGateway;
    private PaymentGateway paypalGateway;
    private PaymentGateway cryptoGateway;
    private PaymentEventPort eventPort;
    private PaymentCurrencyRouter currencyRouter;
    private CurrencyRateCache rateCache;
    private PaymentGatewayProperties gatewayProps;
    private CoinbaseCommerceClient coinbaseClient;
    private PaymentUseCaseImpl useCase;

    @BeforeEach
    void setUp() {
        repository = mock(PaymentRepository.class);
        cardGateway = mock(PaymentGateway.class);
        paypalGateway = mock(PaymentGateway.class);
        cryptoGateway = mock(PaymentGateway.class);
        eventPort = mock(PaymentEventPort.class);
        currencyRouter = mock(PaymentCurrencyRouter.class);
        rateCache = mock(CurrencyRateCache.class);
        gatewayProps = new PaymentGatewayProperties();
        gatewayProps.getStripe().setWebhookSecret("whsec_test");
        gatewayProps.getPaypal().setClientSecret("paypal-secret");
        gatewayProps.getCrypto().getCoinbase().setWebhookSecret("coinbase-secret");
        coinbaseClient = mock(CoinbaseCommerceClient.class);

        when(cardGateway.supports(PaymentMethod.CARD)).thenReturn(true);
        when(paypalGateway.supports(PaymentMethod.PAYPAL)).thenReturn(true);
        when(cryptoGateway.supports(PaymentMethod.USDT)).thenReturn(true);
        when(cryptoGateway.supports(PaymentMethod.BTC)).thenReturn(true);

        List<PaymentGateway> gateways = List.of(cardGateway, paypalGateway, cryptoGateway);
        useCase = new PaymentUseCaseImpl(repository, gateways, eventPort, currencyRouter, rateCache, gatewayProps,
                coinbaseClient);
    }

    // -------- processPayment --------

    @Test
    void processPayment_withExistingIdempotency_returnsExisting() {
        Payment existing = Payment.builder().id("pay-x").build();
        when(repository.findByIdempotencyKey("idem-1")).thenReturn(Optional.of(existing));

        Payment result = useCase.processPayment("o", "u", "e@x", Money.of(BigDecimal.TEN), "USD", PaymentMethod.CARD,
                "idem-1");
        assertThat(result).isSameAs(existing);
    }

    @Test
    void processPayment_card_sameCurrency_success() {
        when(currencyRouter.resolveSettlementCurrency(PaymentMethod.CARD)).thenReturn("USD");
        Payment saved = Payment.builder().id("pay-1").orderId("o1").userId("u1").amount(Money.of(BigDecimal.TEN))
                .currency("USD").status(PaymentStatus.PROCESSING).paymentMethod(PaymentMethod.CARD).build();
        when(repository.save(any(Payment.class))).thenReturn(saved);
        when(repository.update(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));
        when(cardGateway.process(any(PaymentRequest.class))).thenReturn(
                PaymentResult.builder().success(true).providerRef("ref-1").providerResponse(Map.of("x", "y")).build());

        Payment result = useCase.processPayment("o1", "u1", "e@x", Money.of(BigDecimal.TEN), "USD", PaymentMethod.CARD,
                null);

        assertThat(result.getStatus()).isEqualTo(PaymentStatus.COMPLETED);
        assertThat(result.getProviderRef()).isEqualTo("ref-1");
        verify(eventPort).publishPaymentInitiated(anyString(), anyString(), anyString(), anyString(), anyString(),
                anyString(), anyString());
        verify(eventPort).publishPaymentConfirmed(anyString(), anyString(), anyString(), anyString(), anyString(),
                anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void processPayment_card_differentCurrency_usesExchangeRate() {
        when(currencyRouter.resolveSettlementCurrency(PaymentMethod.CARD)).thenReturn("USDT");
        when(rateCache.getRate("EUR")).thenReturn(new BigDecimal("0.92"));
        when(rateCache.getRate("USD")).thenReturn(BigDecimal.ONE);
        Payment saved = Payment.builder().id("pay-2").orderId("o").userId("u").amount(Money.of(new BigDecimal("10.00")))
                .currency("EUR").settlementCurrency("USDT").status(PaymentStatus.PROCESSING)
                .paymentMethod(PaymentMethod.CARD).build();
        when(repository.save(any(Payment.class))).thenReturn(saved);
        when(repository.update(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));
        when(cardGateway.process(any(PaymentRequest.class)))
                .thenReturn(PaymentResult.builder().success(true).providerRef("ref").build());

        Payment result = useCase.processPayment("o", "u", "e", Money.of(new BigDecimal("10.00")), "EUR",
                PaymentMethod.CARD, null);
        assertThat(result.getStatus()).isEqualTo(PaymentStatus.COMPLETED);
    }

    @Test
    void processPayment_nullCurrency_defaultsUsd() {
        when(currencyRouter.resolveSettlementCurrency(PaymentMethod.PAYPAL)).thenReturn("USD");
        Payment saved = Payment.builder().id("pay-3").orderId("o").userId("u").amount(Money.of(BigDecimal.TEN))
                .currency("USD").status(PaymentStatus.PROCESSING).paymentMethod(PaymentMethod.PAYPAL).build();
        when(repository.save(any(Payment.class))).thenReturn(saved);
        when(repository.update(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));
        when(paypalGateway.process(any(PaymentRequest.class)))
                .thenReturn(PaymentResult.builder().success(true).providerRef("ref-pp").build());

        Payment result = useCase.processPayment("o", "u", "e", Money.of(BigDecimal.TEN), null, PaymentMethod.PAYPAL,
                null);
        assertThat(result.getCurrency()).isEqualTo("USD");
    }

    @Test
    void processPayment_gatewayFails_setsFailed() {
        when(currencyRouter.resolveSettlementCurrency(PaymentMethod.CARD)).thenReturn("USD");
        Payment saved = Payment.builder().id("p").orderId("o").userId("u").amount(Money.of(BigDecimal.TEN))
                .currency("USD").status(PaymentStatus.PROCESSING).paymentMethod(PaymentMethod.CARD).build();
        when(repository.save(any(Payment.class))).thenReturn(saved);
        when(repository.update(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));
        when(cardGateway.process(any(PaymentRequest.class))).thenReturn(PaymentResult.builder().success(false)
                .errorMessage("declined").providerResponse(Map.of("err", "1")).build());

        Payment result = useCase.processPayment("o", "u", "e@x", Money.of(BigDecimal.TEN), "USD", PaymentMethod.CARD,
                null);
        assertThat(result.getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(result.getErrorMessage()).isEqualTo("declined");
        verify(eventPort).publishPaymentFailed(anyString(), anyString(), anyString(), anyString(), anyString(),
                anyString(), anyString());
    }

    @Test
    void processPayment_exchangeRateZero_defaultsOne() {
        when(currencyRouter.resolveSettlementCurrency(PaymentMethod.CARD)).thenReturn("USDT");
        when(rateCache.getRate("XYZ")).thenReturn(BigDecimal.ZERO);
        when(rateCache.getRate("USD")).thenReturn(BigDecimal.ONE);
        Payment saved = Payment.builder().id("p").orderId("o").userId("u").amount(Money.of(BigDecimal.TEN))
                .currency("XYZ").status(PaymentStatus.PROCESSING).paymentMethod(PaymentMethod.CARD).build();
        when(repository.save(any(Payment.class))).thenReturn(saved);
        when(repository.update(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));
        when(cardGateway.process(any(PaymentRequest.class)))
                .thenReturn(PaymentResult.builder().success(true).providerRef("r").build());

        Payment result = useCase.processPayment("o", "u", "e", Money.of(BigDecimal.TEN), "XYZ", PaymentMethod.CARD,
                null);
        assertThat(result).isNotNull();
    }

    @Test
    void processPayment_usdtDisplayToUsdSettle_usesOnePeg() {
        when(currencyRouter.resolveSettlementCurrency(PaymentMethod.PAYPAL)).thenReturn("USD");
        Payment saved = Payment.builder().id("p").orderId("o").userId("u").amount(Money.of(BigDecimal.TEN))
                .currency("USDT").status(PaymentStatus.PROCESSING).paymentMethod(PaymentMethod.PAYPAL).build();
        when(repository.save(any(Payment.class))).thenReturn(saved);
        when(repository.update(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));
        when(paypalGateway.process(any(PaymentRequest.class)))
                .thenReturn(PaymentResult.builder().success(true).providerRef("r").build());

        Payment result = useCase.processPayment("o", "u", "e", Money.of(BigDecimal.TEN), "USDT", PaymentMethod.PAYPAL,
                null);
        assertThat(result.getStatus()).isEqualTo(PaymentStatus.COMPLETED);
    }

    // -------- findById / findByOrderId / findByUserId / findAll --------

    @Test
    void findById_found() {
        Payment p = Payment.builder().id("p").build();
        when(repository.findById("p")).thenReturn(Optional.of(p));
        assertThat(useCase.findById("p")).isSameAs(p);
    }

    @Test
    void findById_notFound_throws() {
        when(repository.findById("x")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> useCase.findById("x")).isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    void findByOrderId_notFound_throws() {
        when(repository.findByOrderId("x")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> useCase.findByOrderId("x")).isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    void findByOrderId_found() {
        Payment p = Payment.builder().id("p").build();
        when(repository.findByOrderId("ord")).thenReturn(Optional.of(p));
        assertThat(useCase.findByOrderId("ord")).isSameAs(p);
    }

    @Test
    void findByUserId_descending_defaultSort() {
        Page<Payment> page = new PageImpl<>(List.of(Payment.builder().id("p").build()));
        when(repository.findByUserId(anyString(), any(Pageable.class))).thenReturn(page);
        PageResult<Payment> result = useCase.findByUserId("u", 0, 10, null, false);
        assertThat(result.content()).hasSize(1);
    }

    @Test
    void findByUserId_ascending_customSort() {
        Page<Payment> page = new PageImpl<>(List.of());
        when(repository.findByUserId(anyString(), any(Pageable.class))).thenReturn(page);
        PageResult<Payment> result = useCase.findByUserId("u", 0, 5, "amount", true);
        assertThat(result.content()).isEmpty();
    }

    @Test
    void findByUserId_blankSortBy_usesDefault() {
        Page<Payment> page = new PageImpl<>(List.of());
        when(repository.findByUserId(anyString(), any(Pageable.class))).thenReturn(page);
        PageResult<Payment> result = useCase.findByUserId("u", 0, 5, "   ", true);
        assertThat(result.content()).isEmpty();
    }

    @Test
    void findRefunds_blankSortBy_usesDefault() {
        Page<PaymentRefund> page = new PageImpl<>(List.of());
        when(repository.findRefundsByPaymentId(anyString(), any(Pageable.class))).thenReturn(page);
        useCase.findRefunds("p", 0, 5, "  ", true);
    }

    @Test
    void findAll_blankSortBy_usesDefault() {
        Page<Payment> page = new PageImpl<>(List.of());
        when(repository.findAll(any(), any(Pageable.class))).thenReturn(page);
        useCase.findAll(Map.of(), 0, 5, "", true);
    }

    @Test
    void findAll_defaultAndCustom() {
        Page<Payment> page = new PageImpl<>(List.of(Payment.builder().id("p").build()));
        when(repository.findAll(any(), any(Pageable.class))).thenReturn(page);
        PageResult<Payment> result = useCase.findAll(Map.of("status", "COMPLETED"), 0, 20, "", false);
        assertThat(result.content()).hasSize(1);
    }

    @Test
    void findAll_customFieldAsc() {
        Page<Payment> page = new PageImpl<>(List.of());
        when(repository.findAll(any(), any(Pageable.class))).thenReturn(page);
        useCase.findAll(Map.of(), 1, 5, "orderId", true);
    }

    // -------- refundPayment --------

    @Test
    void refundPayment_success_fullRefund_setsRefunded() {
        Payment payment = Payment.builder().id("pay-1").orderId("o").userId("u")
                .amount(Money.of(new BigDecimal("100.00"))).status(PaymentStatus.COMPLETED)
                .paymentMethod(PaymentMethod.CARD).providerRef("ref").build();
        when(repository.findById("pay-1")).thenReturn(Optional.of(payment));
        when(cardGateway.refund(any(RefundRequest.class)))
                .thenReturn(RefundResult.builder().success(true).providerRef("re-1").build());
        PaymentRefund refund = PaymentRefund.builder().id("r").paymentId("pay-1")
                .amount(Money.of(new BigDecimal("100.00"))).build();
        when(repository.saveRefund(any(PaymentRefund.class))).thenReturn(refund);

        PaymentRefund result = useCase.refundPayment("pay-1", Money.of(new BigDecimal("100.00")), "customer");
        assertThat(result).isSameAs(refund);
        verify(repository).update(any(Payment.class));
        verify(eventPort).publishRefundCompleted(anyString(), anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void refundPayment_success_partialRefund() {
        Payment payment = Payment.builder().id("pay-1").orderId("o").userId("u")
                .amount(Money.of(new BigDecimal("100.00"))).status(PaymentStatus.COMPLETED)
                .paymentMethod(PaymentMethod.CARD).providerRef("ref").build();
        when(repository.findById("pay-1")).thenReturn(Optional.of(payment));
        when(cardGateway.refund(any(RefundRequest.class)))
                .thenReturn(RefundResult.builder().success(true).providerRef("re-1").build());
        PaymentRefund refund = PaymentRefund.builder().id("r").paymentId("pay-1")
                .amount(Money.of(new BigDecimal("40.00"))).build();
        when(repository.saveRefund(any(PaymentRefund.class))).thenReturn(refund);

        PaymentRefund result = useCase.refundPayment("pay-1", Money.of(new BigDecimal("40.00")), "customer");
        assertThat(result).isSameAs(refund);
    }

    @Test
    void refundPayment_gatewayFails_noEventPublished() {
        Payment payment = Payment.builder().id("pay-1").orderId("o").userId("u")
                .amount(Money.of(new BigDecimal("100.00"))).status(PaymentStatus.COMPLETED)
                .paymentMethod(PaymentMethod.CARD).providerRef("ref").build();
        when(repository.findById("pay-1")).thenReturn(Optional.of(payment));
        when(cardGateway.refund(any(RefundRequest.class)))
                .thenReturn(RefundResult.builder().success(false).errorMessage("no").build());
        PaymentRefund refund = PaymentRefund.builder().id("r").paymentId("pay-1")
                .amount(Money.of(new BigDecimal("10.00"))).build();
        when(repository.saveRefund(any(PaymentRefund.class))).thenReturn(refund);

        useCase.refundPayment("pay-1", Money.of(new BigDecimal("10.00")), "reason");
        verify(eventPort, never()).publishRefundCompleted(anyString(), anyString(), anyString(), anyString(),
                anyString());
    }

    @Test
    void refundPayment_paymentNotCompleted_throws() {
        Payment payment = Payment.builder().id("p").status(PaymentStatus.PENDING).paymentMethod(PaymentMethod.CARD)
                .amount(Money.of(BigDecimal.TEN)).build();
        when(repository.findById("p")).thenReturn(Optional.of(payment));
        assertThatThrownBy(() -> useCase.refundPayment("p", Money.of(BigDecimal.ONE), "r"))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void refundPayment_amountExceedsRefundable_throws() {
        Payment payment = Payment.builder().id("p").status(PaymentStatus.COMPLETED).paymentMethod(PaymentMethod.CARD)
                .amount(Money.of(new BigDecimal("10.00"))).providerRef("r").build();
        when(repository.findById("p")).thenReturn(Optional.of(payment));
        assertThatThrownBy(() -> useCase.refundPayment("p", Money.of(new BigDecimal("100.00")), "r"))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void refundPayment_partiallyRefundedAllowed() {
        Payment payment = Payment.builder().id("p").status(PaymentStatus.PARTIALLY_REFUNDED)
                .paymentMethod(PaymentMethod.CARD).amount(Money.of(new BigDecimal("100.00"))).providerRef("r").build();
        when(repository.findById("p")).thenReturn(Optional.of(payment));
        when(cardGateway.refund(any(RefundRequest.class)))
                .thenReturn(RefundResult.builder().success(true).providerRef("re").build());
        when(repository.saveRefund(any(PaymentRefund.class)))
                .thenReturn(PaymentRefund.builder().id("r").amount(Money.of(new BigDecimal("50.00"))).build());

        useCase.refundPayment("p", Money.of(new BigDecimal("50.00")), "r");
    }

    @Test
    void findRefunds_defaultSort() {
        Page<PaymentRefund> page = new PageImpl<>(List.of());
        when(repository.findRefundsByPaymentId(anyString(), any(Pageable.class))).thenReturn(page);
        PageResult<PaymentRefund> result = useCase.findRefunds("p", 0, 10, null, false);
        assertThat(result.content()).isEmpty();
    }

    @Test
    void findRefunds_customSort() {
        Page<PaymentRefund> page = new PageImpl<>(List.of());
        when(repository.findRefundsByPaymentId(anyString(), any(Pageable.class))).thenReturn(page);
        useCase.findRefunds("p", 0, 10, "amount", true);
    }

    // -------- createCryptoPayment --------

    @Test
    void createCryptoPayment_sameCurrency_success() {
        when(currencyRouter.resolveSettlementCurrency(PaymentMethod.USDT)).thenReturn("USDT");
        Payment saved = Payment.builder().id("p-usdt").orderId("o").userId("u")
                .amount(Money.of(new BigDecimal("10.00"))).currency("USDT").paymentMethod(PaymentMethod.USDT)
                .status(PaymentStatus.PENDING).build();
        when(repository.save(any(Payment.class))).thenReturn(saved);
        when(repository.update(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));
        when(cryptoGateway.process(any(PaymentRequest.class))).thenReturn(PaymentResult.builder().success(true)
                .providerRef("CHRG-1").cryptoAddress("addr").qrCodeUrl("url").build());

        Payment result = useCase.createCryptoPayment("o", "u", Money.of(new BigDecimal("10.00")), "USDT",
                PaymentMethod.USDT);
        assertThat(result.getProviderRef()).isEqualTo("CHRG-1");
        assertThat(result.getCryptoAddress()).isEqualTo("addr");
    }

    @Test
    void createCryptoPayment_differentCurrency_convertsWithCryptoScale() {
        when(currencyRouter.resolveSettlementCurrency(PaymentMethod.BTC)).thenReturn("BTC");
        when(rateCache.getRate("USD")).thenReturn(BigDecimal.ONE);
        when(rateCache.getRate("BTC")).thenReturn(new BigDecimal("0.000015"));
        Payment saved = Payment.builder().id("p").orderId("o").userId("u").amount(Money.of(new BigDecimal("100.00")))
                .currency("USD").paymentMethod(PaymentMethod.BTC).status(PaymentStatus.PENDING).build();
        when(repository.save(any(Payment.class))).thenReturn(saved);
        when(repository.update(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));
        when(cryptoGateway.process(any(PaymentRequest.class))).thenReturn(
                PaymentResult.builder().success(true).providerRef("C").cryptoAddress("a").qrCodeUrl("u").build());

        Payment result = useCase.createCryptoPayment("o", "u", Money.of(new BigDecimal("100.00")), "USD",
                PaymentMethod.BTC);
        assertThat(result).isNotNull();
    }

    @Test
    void createCryptoPayment_nullCurrency_defaultsUsd() {
        when(currencyRouter.resolveSettlementCurrency(PaymentMethod.USDT)).thenReturn("USDT");
        when(rateCache.getRate("USD")).thenReturn(BigDecimal.ONE);
        Payment saved = Payment.builder().id("p").orderId("o").userId("u").amount(Money.of(BigDecimal.TEN))
                .currency("USD").paymentMethod(PaymentMethod.USDT).build();
        when(repository.save(any(Payment.class))).thenReturn(saved);
        when(repository.update(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));
        when(cryptoGateway.process(any(PaymentRequest.class)))
                .thenReturn(PaymentResult.builder().success(true).providerRef("C").build());

        Payment result = useCase.createCryptoPayment("o", "u", Money.of(BigDecimal.TEN), null, PaymentMethod.USDT);
        assertThat(result).isNotNull();
    }

    // -------- verifyCryptoPayment --------

    @Test
    void verifyCryptoPayment_expired_throws() {
        Payment payment = Payment.builder().id("p").paymentMethod(PaymentMethod.USDT)
                .cryptoExpiresAt(Instant.now().minusSeconds(60)).status(PaymentStatus.PENDING).build();
        when(repository.findById("p")).thenReturn(Optional.of(payment));
        when(repository.update(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));
        assertThatThrownBy(() -> useCase.verifyCryptoPayment("p")).isInstanceOf(BusinessException.class);
    }

    @Test
    void verifyCryptoPayment_timelineCompleted_setsCompleted() {
        Payment payment = Payment.builder().id("p").orderId("o").userId("u").amount(Money.of(new BigDecimal("1.0")))
                .currency("USDT").paymentMethod(PaymentMethod.USDT).providerRef("CHRG").status(PaymentStatus.PENDING)
                .cryptoExpiresAt(Instant.now().plusSeconds(600)).build();
        when(repository.findById("p")).thenReturn(Optional.of(payment));
        when(coinbaseClient.getCharge("CHRG")).thenReturn(Map.of("timeline", List.of(Map.of("status", "COMPLETED"))));
        when(repository.update(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));

        Payment result = useCase.verifyCryptoPayment("p");
        assertThat(result.getStatus()).isEqualTo(PaymentStatus.COMPLETED);
        verify(eventPort).publishPaymentConfirmed(anyString(), anyString(), anyString(), any(), anyString(),
                anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void verifyCryptoPayment_timelinePending_logsAndReturnsOriginal() {
        Payment payment = Payment.builder().id("p").providerRef("CHRG").status(PaymentStatus.PENDING).build();
        when(repository.findById("p")).thenReturn(Optional.of(payment));
        when(coinbaseClient.getCharge("CHRG")).thenReturn(Map.of("timeline", List.of(Map.of("status", "PENDING"))));
        Payment result = useCase.verifyCryptoPayment("p");
        assertThat(result).isSameAs(payment);
    }

    @Test
    void verifyCryptoPayment_noTimeline_returnsOriginal() {
        Payment payment = Payment.builder().id("p").providerRef("CHRG").status(PaymentStatus.PENDING).build();
        when(repository.findById("p")).thenReturn(Optional.of(payment));
        when(coinbaseClient.getCharge("CHRG")).thenReturn(Map.of());
        Payment result = useCase.verifyCryptoPayment("p");
        assertThat(result).isSameAs(payment);
    }

    @Test
    void verifyCryptoPayment_clientThrows_returnsOriginal() {
        Payment payment = Payment.builder().id("p").providerRef("CHRG").status(PaymentStatus.PENDING).build();
        when(repository.findById("p")).thenReturn(Optional.of(payment));
        when(coinbaseClient.getCharge("CHRG")).thenThrow(new RuntimeException("boom"));
        Payment result = useCase.verifyCryptoPayment("p");
        assertThat(result).isSameAs(payment);
    }

    @Test
    void verifyCryptoPayment_noExpiry_proceeds() {
        Payment payment = Payment.builder().id("p").providerRef("CHRG").status(PaymentStatus.PENDING)
                .cryptoExpiresAt(null).build();
        when(repository.findById("p")).thenReturn(Optional.of(payment));
        when(coinbaseClient.getCharge("CHRG")).thenReturn(Map.of());
        Payment result = useCase.verifyCryptoPayment("p");
        assertThat(result).isSameAs(payment);
    }

    // -------- handleWebhook --------

    @Test
    void handleWebhook_unknown_provider_noop() {
        useCase.handleWebhook("ACH", "{}", "sig");
    }

    @Test
    void handleWebhook_stripe_missingSignature_throws() {
        assertThatThrownBy(() -> useCase.handleWebhook("stripe", "{}", null)).isInstanceOf(ArgumentException.class);
        assertThatThrownBy(() -> useCase.handleWebhook("stripe", "{}", "  ")).isInstanceOf(ArgumentException.class);
    }

    @Test
    void handleWebhook_stripe_validSignature_succeeds() {
        try (MockedStatic<Webhook> mocked = mockStatic(Webhook.class)) {
            Event e = mock(Event.class);
            when(e.getType()).thenReturn("payment_intent.succeeded");
            mocked.when(() -> Webhook.constructEvent(anyString(), anyString(), anyString())).thenReturn(e);
            useCase.handleWebhook("stripe", "{}", "sig");
        }
    }

    @Test
    void handleWebhook_stripe_invalidSignature_throws() {
        try (MockedStatic<Webhook> mocked = mockStatic(Webhook.class)) {
            mocked.when(() -> Webhook.constructEvent(anyString(), anyString(), anyString()))
                    .thenThrow(new SignatureVerificationException("bad", "sig"));
            assertThatThrownBy(() -> useCase.handleWebhook("stripe", "{}", "bad"))
                    .isInstanceOf(ArgumentException.class);
        }
    }

    @Test
    void handleWebhook_paypal_missingSignature_throws() {
        assertThatThrownBy(() -> useCase.handleWebhook("paypal", "{}", "")).isInstanceOf(ArgumentException.class);
        assertThatThrownBy(() -> useCase.handleWebhook("paypal", "{}", null)).isInstanceOf(ArgumentException.class);
    }

    @Test
    void handleWebhook_paypal_validSignature_succeeds() throws Exception {
        String payload = "{\"event\":\"ok\"}";
        String sig = hmacSha256("paypal-secret", payload);
        useCase.handleWebhook("paypal", payload, sig);
    }

    @Test
    void handleWebhook_paypal_invalidSignature_throws() {
        assertThatThrownBy(() -> useCase.handleWebhook("paypal", "{}", "deadbeef"))
                .isInstanceOf(ArgumentException.class);
    }

    @Test
    void handleWebhook_crypto_missingSignature_throws() {
        assertThatThrownBy(() -> useCase.handleWebhook("crypto", "{}", "")).isInstanceOf(ArgumentException.class);
        assertThatThrownBy(() -> useCase.handleWebhook("crypto", "{}", null)).isInstanceOf(ArgumentException.class);
    }

    @Test
    void handleWebhook_crypto_validSignature_succeeds() throws Exception {
        String payload = "{\"type\":\"charge:confirmed\"}";
        String sig = hmacSha256("coinbase-secret", payload);
        useCase.handleWebhook("crypto", payload, sig);
    }

    @Test
    void handleWebhook_crypto_invalidSignature_throws() {
        assertThatThrownBy(() -> useCase.handleWebhook("crypto", "{}", "0".repeat(64)))
                .isInstanceOf(ArgumentException.class);
    }

    @Test
    void processPayment_unsupportedMethod_throws() {
        // All gateways return false for supports
        List<PaymentGateway> empty = List.of(mock(PaymentGateway.class));
        PaymentUseCaseImpl uc = new PaymentUseCaseImpl(repository, empty, eventPort, currencyRouter, rateCache,
                gatewayProps, coinbaseClient);
        assertThatThrownBy(
                () -> uc.processPayment("o", "u", "e", Money.of(BigDecimal.ONE), "USD", PaymentMethod.CARD, null))
                .isInstanceOf(BusinessException.class);
    }

    private static String hmacSha256(String secret, String payload) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
    }
}
