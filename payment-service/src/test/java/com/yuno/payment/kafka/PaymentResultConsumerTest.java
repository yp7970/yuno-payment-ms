package com.yuno.payment.kafka;

import com.yuno.commons.enums.ProviderType;
import com.yuno.commons.events.PaymentResultEvent;
import com.yuno.payment.service.PaymentService;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("PaymentResultConsumer Tests")
class PaymentResultConsumerTest {

    @Mock private PaymentService paymentService;
    @InjectMocks private PaymentResultConsumer consumer;

    @BeforeEach
    void setUp() {
        org.springframework.test.util.ReflectionTestUtils.setField(
                consumer, "serviceName", "payment-service");
    }

    @Test
    @DisplayName("consume: calls handlePaymentResult with correct event")
    void consume_validRecord_callsServiceWithEvent() {
        PaymentResultEvent event = PaymentResultEvent.builder()
                .paymentId(UUID.randomUUID().toString())
                .success(true)
                .providerUsed(ProviderType.PROVIDER_A)
                .providerTransactionId("PA-TXN999")
                .retryCount(1)
                .failoverUsed(false)
                .build();

        ConsumerRecord<String, PaymentResultEvent> record =
                new ConsumerRecord<>("payment.result", 0, 0L, event.getPaymentId(), event);

        consumer.consume(record);

        verify(paymentService).handlePaymentResult(event);
    }

    @Test
    @DisplayName("consume: MDC is cleared even when service throws")
    void consume_serviceThrows_mdcIsCleared() {
        PaymentResultEvent event = PaymentResultEvent.builder()
                .paymentId(UUID.randomUUID().toString())
                .success(false).build();
        ConsumerRecord<String, PaymentResultEvent> record =
                new ConsumerRecord<>("payment.result", 0, 0L, event.getPaymentId(), event);

        doThrow(new RuntimeException("DB error")).when(paymentService).handlePaymentResult(any());

        try {
            consumer.consume(record);
        } catch (RuntimeException e) {
            // Expected — consumer re-throws for Kafka DLQ routing
            org.assertj.core.api.Assertions.assertThat(e.getMessage()).isEqualTo("DB error");
        }
        // MDC must be cleared regardless of outcome
        org.assertj.core.api.Assertions.assertThat(org.slf4j.MDC.get("transactionId")).isNull();
    }
}
