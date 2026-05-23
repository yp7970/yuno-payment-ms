package com.yuno.notification.service;

import com.yuno.commons.enums.PaymentStatus;
import com.yuno.commons.events.PaymentNotificationEvent;
import com.yuno.notification.mapper.NotificationMapper;
import com.yuno.notification.model.NotificationLog;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Notification service — stub implementation.
 *
 * In production this would dispatch:
 *   - Email via SES / SendGrid
 *   - Push notification via FCM
 *   - Webhook to merchant callback URL
 *
 * For this assessment it logs the notification and persists
 * a delivery record via MyBatis INSERT for audit purposes.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {

    private final NotificationMapper notificationMapper;

    public void sendNotification(PaymentNotificationEvent event) {
        String type = event.getStatus() == PaymentStatus.SUCCESS
                ? "PAYMENT_SUCCESS" : "PAYMENT_FAILED";

        log.info("[NOTIFICATION] Sending {} notification | paymentId={} | userId={} | amount={} {}",
                type, event.getPaymentId(), event.getUserId(),
                event.getAmount(), event.getCurrency());

        // In production: dispatch email/push/webhook here

        // Persist audit log via MyBatis INSERT — no JPA/Hibernate
        NotificationLog record = NotificationLog.builder()
                .paymentId(event.getPaymentId())
                .userId(event.getUserId())
                .paymentStatus(event.getStatus())
                .paymentMethod(event.getPaymentMethod())
                .amount(event.getAmount())
                .currency(event.getCurrency())
                .providerTransactionId(event.getProviderTransactionId())
                .notificationType(type)
                .deliveryStatus("DELIVERED")
                .correlationId(event.getCorrelationId())
                .build();

        notificationMapper.insert(record);
        log.info("[NOTIFICATION] Log persisted via MyBatis | paymentId={} | type={}", event.getPaymentId(), type);
    }
}
