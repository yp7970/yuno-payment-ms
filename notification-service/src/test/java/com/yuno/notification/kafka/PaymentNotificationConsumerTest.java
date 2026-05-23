package com.yuno.notification.kafka;

import com.yuno.commons.enums.PaymentMethod;
import com.yuno.commons.enums.PaymentStatus;
import com.yuno.commons.events.PaymentNotificationEvent;
import com.yuno.notification.service.NotificationService;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.slf4j.MDC;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@MockitoSettings(strictness = Strictness.LENIENT)
@ExtendWith(MockitoExtension.class)
@DisplayName("PaymentNotificationConsumer Tests")
class PaymentNotificationConsumerTest {

    @Mock private NotificationService notificationService;
    @InjectMocks private PaymentNotificationConsumer consumer;

    @BeforeEach
    void setUp() {
        org.springframework.test.util.ReflectionTestUtils.setField(
                consumer, "serviceName", "notification-service");
    }

    @Test
    @DisplayName("consume: calls sendNotification with correct event")
    void consume_validRecord_callsService() {
        PaymentNotificationEvent event = PaymentNotificationEvent.builder()
                .paymentId(UUID.randomUUID().toString())
                .userId("user-1")
                .status(PaymentStatus.SUCCESS)
                .paymentMethod(PaymentMethod.CARD)
                .amount(new BigDecimal("99.00"))
                .currency("USD")
                .build();

        ConsumerRecord<String, PaymentNotificationEvent> record =
                new ConsumerRecord<>("payment.notification", 0, 0L, event.getPaymentId(), event);

        consumer.consume(record);

        verify(notificationService).sendNotification(event);
    }

    @Test
    @DisplayName("consume: MDC is cleared after processing even on success")
    void consume_afterProcessing_mdcIsCleared() {
        PaymentNotificationEvent event = PaymentNotificationEvent.builder()
                .paymentId(UUID.randomUUID().toString())
                .status(PaymentStatus.FAILED)
                .paymentMethod(PaymentMethod.UPI)
                .build();
        ConsumerRecord<String, PaymentNotificationEvent> record =
                new ConsumerRecord<>("payment.notification", 0, 0L, event.getPaymentId(), event);
        doNothing().when(notificationService).sendNotification(any());

        consumer.consume(record);

        assertThat(MDC.get("transactionId")).isNull();
        assertThat(MDC.get("correlationId")).isNull();
    }

    @Test
    @DisplayName("consume: re-throws exception for DLQ routing when service fails")
    void consume_serviceThrows_rethrowsForDlq() {
        PaymentNotificationEvent event = PaymentNotificationEvent.builder()
                .paymentId(UUID.randomUUID().toString())
                .status(PaymentStatus.SUCCESS)
                .paymentMethod(PaymentMethod.CARD)
                .build();
        ConsumerRecord<String, PaymentNotificationEvent> record =
                new ConsumerRecord<>("payment.notification", 0, 0L, event.getPaymentId(), event);
        doThrow(new RuntimeException("DB error")).when(notificationService).sendNotification(any());

        assertThatThrownBy(() -> consumer.consume(record))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("DB error");

        // MDC still cleared even on error
        assertThat(MDC.get("transactionId")).isNull();
    }
}
