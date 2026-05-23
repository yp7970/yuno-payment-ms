package com.yuno.provider.provider;

import com.yuno.commons.enums.PaymentMethod;
import com.yuno.commons.enums.ProviderType;
import com.yuno.commons.events.PaymentInitiatedEvent;
import com.yuno.provider.exception.ProviderException;
import org.junit.jupiter.api.*;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

@DisplayName("Provider Connector Tests")
class ProviderConnectorTest {

    private ProviderAConnector providerA;
    private ProviderBConnector providerB;
    private PaymentInitiatedEvent event;

    @BeforeEach
    void setUp() {
        providerA = new ProviderAConnector();
        ReflectionTestUtils.setField(providerA, "latencyMs", 0L);

        providerB = new ProviderBConnector();
        ReflectionTestUtils.setField(providerB, "latencyMs", 0L);

        event = PaymentInitiatedEvent.builder()
                .paymentId(UUID.randomUUID().toString())
                .paymentMethod(PaymentMethod.CARD)
                .amount(new BigDecimal("500.00"))
                .currency("USD")
                .build();
    }

    // ── Provider A ──────────────────────────────────────────────────────────

    @Test @DisplayName("Provider A: zero failure rate → always succeeds")
    void providerA_zeroFailureRate_succeeds() {
        ReflectionTestUtils.setField(providerA, "failureRate", 0.0);
        ProviderCallResponse r = providerA.process(event);
        assertThat(r.isSuccess()).isTrue();
        assertThat(r.getProvider()).isEqualTo(ProviderType.PROVIDER_A);
        assertThat(r.getTransactionId()).matches("^PA-[A-Z0-9]{16}$");
        assertThat(r.getProcessingTimeMs()).isGreaterThanOrEqualTo(0L);
    }

    @Test @DisplayName("Provider A: full failure rate → throws ProviderException")
    void providerA_fullFailureRate_throwsProviderException() {
        ReflectionTestUtils.setField(providerA, "failureRate", 1.0);
        assertThatThrownBy(() -> providerA.process(event))
                .isInstanceOf(ProviderException.class)
                .satisfies(ex -> assertThat(((ProviderException) ex).getProvider())
                        .isEqualTo(ProviderType.PROVIDER_A));
    }

    @Test @DisplayName("Provider A: returns correct ProviderType")
    void providerA_getProviderType_returnsProviderA() {
        assertThat(providerA.getProviderType()).isEqualTo(ProviderType.PROVIDER_A);
    }

    @Test @DisplayName("Provider A: transaction ID has PA- prefix format")
    void providerA_transactionId_hasPaPrefix() {
        ReflectionTestUtils.setField(providerA, "failureRate", 0.0);
        assertThat(providerA.process(event).getTransactionId()).startsWith("PA-");
    }

    // ── Provider B ──────────────────────────────────────────────────────────

    @Test @DisplayName("Provider B: zero failure rate → always succeeds")
    void providerB_zeroFailureRate_succeeds() {
        ReflectionTestUtils.setField(providerB, "failureRate", 0.0);
        ProviderCallResponse r = providerB.process(event);
        assertThat(r.isSuccess()).isTrue();
        assertThat(r.getProvider()).isEqualTo(ProviderType.PROVIDER_B);
        assertThat(r.getTransactionId()).matches("^PB-[A-Z0-9]{16}$");
    }

    @Test @DisplayName("Provider B: full failure rate → throws ProviderException")
    void providerB_fullFailureRate_throwsProviderException() {
        ReflectionTestUtils.setField(providerB, "failureRate", 1.0);
        assertThatThrownBy(() -> providerB.process(event))
                .isInstanceOf(ProviderException.class)
                .satisfies(ex -> assertThat(((ProviderException) ex).getProvider())
                        .isEqualTo(ProviderType.PROVIDER_B));
    }

    @Test @DisplayName("Provider B: returns correct ProviderType")
    void providerB_getProviderType_returnsProviderB() {
        assertThat(providerB.getProviderType()).isEqualTo(ProviderType.PROVIDER_B);
    }

    // ── ProviderCallResponse factory methods ─────────────────────────────────

    @Test @DisplayName("ProviderCallResponse.success() sets all fields correctly")
    void providerCallResponse_successFactory() {
        ProviderCallResponse r = ProviderCallResponse.success(ProviderType.PROVIDER_A, "TX-001", 150L);
        assertThat(r.isSuccess()).isTrue();
        assertThat(r.getTransactionId()).isEqualTo("TX-001");
        assertThat(r.getProcessingTimeMs()).isEqualTo(150L);
        assertThat(r.getErrorMessage()).isNull();
    }

    @Test @DisplayName("ProviderCallResponse.failure() sets all fields correctly")
    void providerCallResponse_failureFactory() {
        ProviderCallResponse r = ProviderCallResponse.failure(ProviderType.PROVIDER_B, "Timeout", 500L);
        assertThat(r.isSuccess()).isFalse();
        assertThat(r.getErrorMessage()).isEqualTo("Timeout");
        assertThat(r.getTransactionId()).isNull();
    }
}
