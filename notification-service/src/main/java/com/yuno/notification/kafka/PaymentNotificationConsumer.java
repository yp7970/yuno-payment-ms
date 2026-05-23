package com.yuno.notification.kafka;

import com.yuno.commons.events.PaymentNotificationEvent;
import com.yuno.commons.mdc.MDCUtil;
import com.yuno.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Kafka consumer: receives PaymentNotificationEvent from payment-service.
 * Topic: payment.notification
 *
 * MDC restoration completes the full trace chain:
 *   HTTP → payment-service → [Kafka] → provider-service → [Kafka]
 *   → payment-service → [Kafka] → notification-service
 *
 * At every hop, the same transactionId and correlationId appear in logs,
 * making the entire payment journey queryable with a single search.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentNotificationConsumer {

    @Value("${spring.application.name}")
    private String serviceName;

    private final NotificationService notificationService;

    @KafkaListener(
            topics = "payment.notification",
            groupId = "notification-service-group",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void consume(ConsumerRecord<String, PaymentNotificationEvent> record) {
        MDCUtil.populateFromConsumerRecord(record, serviceName);

        try {
            PaymentNotificationEvent event = record.value();
            log.info("Consumed PaymentNotificationEvent | paymentId={} | status={} | userId={}",
                    event.getPaymentId(), event.getStatus(), event.getUserId());

            notificationService.sendNotification(event);

        } catch (Exception e) {
            log.error("Error processing PaymentNotificationEvent | paymentId={} | error={}",
                    record.key(), e.getMessage(), e);
            throw e;
        } finally {
            MDCUtil.clear();
        }
    }
}
