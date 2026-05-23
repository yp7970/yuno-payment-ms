package com.yuno.notification.service;

import com.yuno.commons.enums.PaymentMethod;
import com.yuno.commons.enums.PaymentStatus;
import com.yuno.commons.events.PaymentNotificationEvent;
import com.yuno.notification.mapper.NotificationMapper;
import com.yuno.notification.model.NotificationLog;
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
@DisplayName("NotificationService Tests")
class NotificationServiceTest {

    @Mock private NotificationMapper notificationMapper;
    @InjectMocks private NotificationService service;

    private PaymentNotificationEvent successEvent;
    private PaymentNotificationEvent failureEvent;

    @BeforeEach
    void setUp() {
        successEvent = PaymentNotificationEvent.builder()
                .paymentId(UUID.randomUUID().toString())
                .userId("user-123")
                .paymentMethod(PaymentMethod.CARD)
                .amount(new BigDecimal("100.00"))
                .currency("USD")
                .status(PaymentStatus.SUCCESS)
                .providerTransactionId("PA-TXN001")
                .correlationId("corr-abc")
                .build();

        failureEvent = successEvent.toBuilder()
                .status(PaymentStatus.FAILED)
                .providerTransactionId(null)
                .failureReason("All providers failed")
                .build();
    }

    @Test
    @DisplayName("sendNotification: SUCCESS event → inserts PAYMENT_SUCCESS log via MyBatis")
    void sendNotification_success_insertsCorrectLog() {
        doNothing().when(notificationMapper).insert(any(NotificationLog.class));

        service.sendNotification(successEvent);

        ArgumentCaptor<NotificationLog> captor = ArgumentCaptor.forClass(NotificationLog.class);
        verify(notificationMapper).insert(captor.capture());

        NotificationLog log = captor.getValue();
        assertThat(log.getNotificationType()).isEqualTo("PAYMENT_SUCCESS");
        assertThat(log.getDeliveryStatus()).isEqualTo("DELIVERED");
        assertThat(log.getPaymentStatus()).isEqualTo(PaymentStatus.SUCCESS);
        assertThat(log.getPaymentId()).isEqualTo(successEvent.getPaymentId());
        assertThat(log.getUserId()).isEqualTo("user-123");
        assertThat(log.getCorrelationId()).isEqualTo("corr-abc");
        assertThat(log.getProviderTransactionId()).isEqualTo("PA-TXN001");
    }

    @Test
    @DisplayName("sendNotification: FAILED event → inserts PAYMENT_FAILED log via MyBatis")
    void sendNotification_failure_insertsCorrectLog() {
        doNothing().when(notificationMapper).insert(any(NotificationLog.class));

        service.sendNotification(failureEvent);

        ArgumentCaptor<NotificationLog> captor = ArgumentCaptor.forClass(NotificationLog.class);
        verify(notificationMapper).insert(captor.capture());
        assertThat(captor.getValue().getNotificationType()).isEqualTo("PAYMENT_FAILED");
        assertThat(captor.getValue().getPaymentStatus()).isEqualTo(PaymentStatus.FAILED);
    }

    @Test
    @DisplayName("sendNotification: always calls mapper exactly once")
    void sendNotification_alwaysCallsMapperOnce() {
        doNothing().when(notificationMapper).insert(any());

        service.sendNotification(successEvent);
        service.sendNotification(failureEvent);

        verify(notificationMapper, times(2)).insert(any(NotificationLog.class));
    }
}
