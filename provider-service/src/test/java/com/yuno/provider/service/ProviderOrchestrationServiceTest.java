package com.yuno.provider.service;

import com.yuno.commons.enums.PaymentMethod;
import com.yuno.commons.enums.ProviderType;
import com.yuno.commons.events.PaymentInitiatedEvent;
import com.yuno.commons.events.PaymentResultEvent;
import com.yuno.provider.exception.ProviderException;
import com.yuno.provider.mapper.ProviderCallMapper;
import com.yuno.provider.model.ProviderCall;
import com.yuno.provider.provider.PaymentProviderConnector;
import com.yuno.provider.provider.ProviderCallResponse;
import com.yuno.provider.routing.PaymentRoutingEngine;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@MockitoSettings(strictness = Strictness.LENIENT)
@ExtendWith(MockitoExtension.class)
@DisplayName("ProviderOrchestrationService Tests")
class ProviderOrchestrationServiceTest {

    @Mock private PaymentRoutingEngine routingEngine;
    @Mock private ProviderCallMapper providerCallMapper;
    @Mock private PaymentProviderConnector primaryConnector;
    @Mock private PaymentProviderConnector failoverConnector;

    @InjectMocks private ProviderOrchestrationService service;

    private PaymentInitiatedEvent cardEvent;

    @BeforeEach
    void setUp() {
        cardEvent = PaymentInitiatedEvent.builder()
                .paymentId(UUID.randomUUID().toString())
                .paymentMethod(PaymentMethod.CARD)
                .amount(new BigDecimal("200.00"))
                .currency("USD")
                .correlationId("corr-123")
                .userId("user-456")
                .build();

        when(routingEngine.getPrimary(PaymentMethod.CARD)).thenReturn(primaryConnector);
        when(routingEngine.getFailover(PaymentMethod.CARD)).thenReturn(failoverConnector);
        when(primaryConnector.getProviderType()).thenReturn(ProviderType.PROVIDER_A);
        when(failoverConnector.getProviderType()).thenReturn(ProviderType.PROVIDER_B);
        doNothing().when(providerCallMapper).insert(any(ProviderCall.class));
    }

    // ── Happy path ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("Primary succeeds on first attempt — no failover, success result")
    void orchestrate_primarySucceeds_returnsSuccessResult() {
        when(primaryConnector.process(cardEvent))
                .thenReturn(ProviderCallResponse.success(ProviderType.PROVIDER_A, "PA-TXN001", 200L));

        PaymentResultEvent result = service.orchestrate(cardEvent);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getProviderUsed()).isEqualTo(ProviderType.PROVIDER_A);
        assertThat(result.getProviderTransactionId()).isEqualTo("PA-TXN001");
        assertThat(result.isFailoverUsed()).isFalse();
        assertThat(result.getRetryCount()).isEqualTo(1);

        verify(providerCallMapper).insert(any(ProviderCall.class));
        verifyNoInteractions(failoverConnector);
    }

    // ── Failover ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Primary exhausts retries → failover succeeds → result is SUCCESS with failoverUsed=true")
    void orchestrate_primaryFails_failoverSucceeds() {
        when(primaryConnector.process(cardEvent))
                .thenThrow(new ProviderException(ProviderType.PROVIDER_A, "Timeout"));
        when(failoverConnector.process(cardEvent))
                .thenReturn(ProviderCallResponse.success(ProviderType.PROVIDER_B, "PB-TXN002", 300L));

        PaymentResultEvent result = service.orchestrate(cardEvent);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getProviderUsed()).isEqualTo(ProviderType.PROVIDER_B);
        assertThat(result.isFailoverUsed()).isTrue();
        assertThat(result.getRetryCount()).isGreaterThan(1);

        verify(failoverConnector).process(cardEvent);
        verify(providerCallMapper).insert(any(ProviderCall.class));
    }

    @Test
    @DisplayName("Both primary and failover fail → result is FAILED with both error messages")
    void orchestrate_bothProvidersFail_returnsFailedResult() {
        when(primaryConnector.process(cardEvent))
                .thenThrow(new ProviderException(ProviderType.PROVIDER_A, "Primary down"));
        when(failoverConnector.process(cardEvent))
                .thenThrow(new ProviderException(ProviderType.PROVIDER_B, "Failover down"));

        PaymentResultEvent result = service.orchestrate(cardEvent);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.isFailoverUsed()).isTrue();
        assertThat(result.getErrorMessage()).contains("Primary");
        assertThat(result.getErrorMessage()).contains("Failover");

        verify(providerCallMapper).insert(any(ProviderCall.class));
    }

    // ── Audit record ────────────────────────────────────────────────────────

    @Test
    @DisplayName("Audit record is always persisted via MyBatis regardless of outcome")
    void orchestrate_alwaysInsertsAuditRecord() {
        when(primaryConnector.process(cardEvent))
                .thenReturn(ProviderCallResponse.success(ProviderType.PROVIDER_A, "TX-001", 100L));

        service.orchestrate(cardEvent);

        verify(providerCallMapper, times(1)).insert(any(ProviderCall.class));
    }

    @Test
    @DisplayName("Audit record contains correct paymentId")
    void orchestrate_auditRecord_hasCorrectPaymentId() {
        when(primaryConnector.process(cardEvent))
                .thenReturn(ProviderCallResponse.success(ProviderType.PROVIDER_A, "TX-001", 100L));

        service.orchestrate(cardEvent);

        ArgumentCaptor<ProviderCall> captor = ArgumentCaptor.forClass(ProviderCall.class);
        verify(providerCallMapper).insert(captor.capture());
        assertThat(captor.getValue().getPaymentId())
                .isEqualTo(UUID.fromString(cardEvent.getPaymentId()));
    }

    // ── Result event fields ─────────────────────────────────────────────────

    @Test
    @DisplayName("Result event carries correlationId and userId from initiated event")
    void orchestrate_resultEvent_propagatesTraceFields() {
        when(primaryConnector.process(cardEvent))
                .thenReturn(ProviderCallResponse.success(ProviderType.PROVIDER_A, "TX-001", 100L));

        PaymentResultEvent result = service.orchestrate(cardEvent);

        assertThat(result.getCorrelationId()).isEqualTo("corr-123");
        assertThat(result.getUserId()).isEqualTo("user-456");
        assertThat(result.getPaymentId()).isEqualTo(cardEvent.getPaymentId());
    }

    @Test
    @DisplayName("UPI payment routes correctly to Provider B as primary")
    void orchestrate_upiPayment_routesToProviderB() {
        PaymentInitiatedEvent upiEvent = cardEvent.toBuilder()
                .paymentMethod(PaymentMethod.UPI).build();

        when(routingEngine.getPrimary(PaymentMethod.UPI)).thenReturn(failoverConnector); // B is primary for UPI
        when(routingEngine.getFailover(PaymentMethod.UPI)).thenReturn(primaryConnector);
        when(failoverConnector.getProviderType()).thenReturn(ProviderType.PROVIDER_B);
        when(failoverConnector.process(upiEvent))
                .thenReturn(ProviderCallResponse.success(ProviderType.PROVIDER_B, "PB-UPI-001", 150L));

        PaymentResultEvent result = service.orchestrate(upiEvent);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getProviderUsed()).isEqualTo(ProviderType.PROVIDER_B);
        assertThat(result.isFailoverUsed()).isFalse();
    }
}
