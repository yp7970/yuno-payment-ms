package com.yuno.payment.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.yuno.commons.enums.PaymentMethod;
import com.yuno.commons.enums.PaymentStatus;
import com.yuno.commons.enums.ProviderType;
import com.yuno.commons.events.PaymentResultEvent;
import com.yuno.payment.client.IdempotencyFeignClient;
import com.yuno.payment.dto.*;
import com.yuno.payment.exception.PaymentNotFoundException;
import com.yuno.payment.kafka.PaymentEventProducer;
import com.yuno.payment.mapper.PaymentMapper;
import com.yuno.payment.model.Payment;
import feign.FeignException;
import feign.Request;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("PaymentService Unit Tests")
class PaymentServiceTest {

    @Mock private PaymentMapper paymentMapper;
    @Mock private PaymentEventProducer eventProducer;
    @Mock private IdempotencyFeignClient idempotencyClient;

    @InjectMocks private PaymentService paymentService;

    private ObjectMapper objectMapper;
    private static final String IDEMPOTENCY_KEY = "test-key-" + UUID.randomUUID();

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        // Inject via reflection since @InjectMocks doesn't handle final fields easily
        org.springframework.test.util.ReflectionTestUtils.setField(
                paymentService, "objectMapper", objectMapper);
    }

    private CreatePaymentRequest cardRequest() {
        return CreatePaymentRequest.builder()
                .paymentMethod(PaymentMethod.CARD)
                .amount(new BigDecimal("150.00"))
                .currency("USD")
                .description("Order test")
                .build();
    }

    private Payment pendingPayment(String id) {
        return Payment.builder()
                .id(UUID.fromString(id))
                .idempotencyKey(IDEMPOTENCY_KEY)
                .paymentMethod(PaymentMethod.CARD)
                .amount(new BigDecimal("150.00"))
                .currency("USD")
                .status(PaymentStatus.PENDING)
                .retryCount(0)
                .failoverUsed(false)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    // ── createPayment — new payment ─────────────────────────────────────────

    @Test
    @DisplayName("New payment: idempotency miss → saves PENDING → publishes event → returns 202 response")
    void createPayment_newKey_savesAndPublishesEvent() {
        when(idempotencyClient.check(IDEMPOTENCY_KEY))
                .thenThrow(new FeignException.NotFound(
                        "not found",
                        Request.create(Request.HttpMethod.POST, "/idempotency",
                                Map.of(), null, null, null),
                        null, null));
        doNothing().when(paymentMapper).insert(any(Payment.class));
        doNothing().when(eventProducer).publishPaymentInitiated(any());

        PaymentResponse response = paymentService.createPayment(cardRequest(), IDEMPOTENCY_KEY);

        assertThat(response.getStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(response.getPaymentId()).isNotNull();
        assertThat(response.getPaymentMethod()).isEqualTo(PaymentMethod.CARD);

        verify(paymentMapper).insert(any(Payment.class));
        verify(eventProducer).publishPaymentInitiated(any());
        verifyNoMoreInteractions(eventProducer);
    }

    @Test
    @DisplayName("Idempotency HIT: returns cached response without calling mapper or producer")
    void createPayment_duplicateKey_returnsCached_withoutSavingOrPublishing() throws Exception {
        PaymentResponse cached = PaymentResponse.builder()
                .paymentId(UUID.randomUUID())
                .status(PaymentStatus.SUCCESS)
                .providerUsed(ProviderType.PROVIDER_A)
                .build();
        String cachedJson = objectMapper.writeValueAsString(cached);

        // Wrap in ApiResponse to match the fixed Feign client signature
        com.yuno.commons.dto.ApiResponse<IdempotencyCheckResponse> apiResp =
                com.yuno.commons.dto.ApiResponse.success(
                        new IdempotencyCheckResponse(true, cachedJson, 202));
        when(idempotencyClient.check(IDEMPOTENCY_KEY))
                .thenReturn(ResponseEntity.ok(apiResp));

        PaymentResponse response = paymentService.createPayment(cardRequest(), IDEMPOTENCY_KEY);

        assertThat(response.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
        assertThat(response.getProviderUsed()).isEqualTo(ProviderType.PROVIDER_A);

        verifyNoInteractions(paymentMapper);
        verifyNoInteractions(eventProducer);
    }

    @Test
    @DisplayName("Idempotency service DOWN: logs warning, proceeds with new payment")
    void createPayment_idempotencyServiceDown_proceedsWithNewPayment() {
        Request dummyRequest = Request.create(
                Request.HttpMethod.POST,
                "/idempotency",
                Map.of(),
                null, null, null);

        when(idempotencyClient.check(IDEMPOTENCY_KEY))
                .thenThrow(new FeignException.ServiceUnavailable(
                        "down", dummyRequest, null, null));
        doNothing().when(paymentMapper).insert(any(Payment.class));

        // Should not throw — graceful degradation
        assertThatNoException().isThrownBy(() ->
                paymentService.createPayment(cardRequest(), IDEMPOTENCY_KEY));

        verify(paymentMapper).insert(any(Payment.class));
    }

    // ── handlePaymentResult ─────────────────────────────────────────────────

    @Test
    @DisplayName("Successful result: updates payment to SUCCESS, stores idempotency, publishes notification")
    void handlePaymentResult_success_updatesStatusToSuccess() {
        String paymentId = UUID.randomUUID().toString();
        Payment payment = pendingPayment(paymentId).toBuilder()
                .status(PaymentStatus.SUCCESS)
                .providerUsed(ProviderType.PROVIDER_A)
                .providerTransactionId("PA-TXN001")
                .build();

        doNothing().when(paymentMapper).updateStatusAfterResult(any(), any(), any(), any(), anyInt(), anyBoolean(), any());
        when(paymentMapper.findById(UUID.fromString(paymentId))).thenReturn(payment);
        when(idempotencyClient.store(any())).thenReturn(ResponseEntity.ok().build());
        doNothing().when(eventProducer).publishPaymentNotification(any());

        PaymentResultEvent event = PaymentResultEvent.builder()
                .paymentId(paymentId)
                .success(true)
                .providerUsed(ProviderType.PROVIDER_A)
                .providerTransactionId("PA-TXN001")
                .retryCount(1)
                .failoverUsed(false)
                .build();

        paymentService.handlePaymentResult(event);

        verify(paymentMapper).updateStatusAfterResult(
                UUID.fromString(paymentId),
                PaymentStatus.SUCCESS,
                ProviderType.PROVIDER_A,
                "PA-TXN001",
                1, false, null
        );
        verify(eventProducer).publishPaymentNotification(any());
    }

    @Test
    @DisplayName("Failed result: updates payment to FAILED, stores idempotency, publishes notification")
    void handlePaymentResult_failure_updatesStatusToFailed() {
        String paymentId = UUID.randomUUID().toString();
        Payment payment = pendingPayment(paymentId).toBuilder()
                .status(PaymentStatus.FAILED)
                .failureReason("All providers failed")
                .build();

        doNothing().when(paymentMapper).updateStatusAfterResult(any(), any(), any(), any(), anyInt(), anyBoolean(), any());
        when(paymentMapper.findById(UUID.fromString(paymentId))).thenReturn(payment);
        when(idempotencyClient.store(any())).thenReturn(ResponseEntity.ok().build());

        PaymentResultEvent event = PaymentResultEvent.builder()
                .paymentId(paymentId)
                .success(false)
                .retryCount(3)
                .failoverUsed(true)
                .errorMessage("All providers failed")
                .build();

        paymentService.handlePaymentResult(event);

        verify(paymentMapper).updateStatusAfterResult(
                UUID.fromString(paymentId),
                PaymentStatus.FAILED,
                null, null, 3, true, "All providers failed"
        );
    }

    // ── getPayment ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("getPayment: found → returns mapped PaymentResponse")
    void getPayment_found_returnsResponse() {
        UUID id = UUID.randomUUID();
        when(paymentMapper.findById(id)).thenReturn(pendingPayment(id.toString()));

        PaymentResponse resp = paymentService.getPayment(id);

        assertThat(resp.getPaymentId()).isEqualTo(id);
        assertThat(resp.getStatus()).isEqualTo(PaymentStatus.PENDING);
    }

    @Test
    @DisplayName("getPayment: not found → throws PaymentNotFoundException")
    void getPayment_notFound_throwsNotFoundException() {
        UUID id = UUID.randomUUID();
        when(paymentMapper.findById(id)).thenReturn(null);

        assertThatThrownBy(() -> paymentService.getPayment(id))
                .isInstanceOf(PaymentNotFoundException.class)
                .hasMessageContaining(id.toString());
    }

    // ── getPaymentsByStatus ─────────────────────────────────────────────────

    @Test
    @DisplayName("getPaymentsByStatus: returns mapped list")
    void getPaymentsByStatus_returnsFilteredList() {
        UUID id1 = UUID.randomUUID(), id2 = UUID.randomUUID();
        when(paymentMapper.findByStatus(PaymentStatus.SUCCESS))
                .thenReturn(List.of(
                        pendingPayment(id1.toString()).toBuilder().status(PaymentStatus.SUCCESS).build(),
                        pendingPayment(id2.toString()).toBuilder().status(PaymentStatus.SUCCESS).build()
                ));

        List<PaymentResponse> result = paymentService.getPaymentsByStatus(PaymentStatus.SUCCESS);

        assertThat(result).hasSize(2);
        assertThat(result).allMatch(r -> r.getStatus() == PaymentStatus.SUCCESS);
    }

    @Test
    @DisplayName("getPaymentsByStatus: empty list when no matches")
    void getPaymentsByStatus_noMatches_returnsEmpty() {
        when(paymentMapper.findByStatus(PaymentStatus.FAILED)).thenReturn(List.of());
        assertThat(paymentService.getPaymentsByStatus(PaymentStatus.FAILED)).isEmpty();
    }

}
