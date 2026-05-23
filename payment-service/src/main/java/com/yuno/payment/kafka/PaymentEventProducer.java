package com.yuno.payment.kafka;

import com.yuno.commons.events.PaymentInitiatedEvent;
import com.yuno.commons.events.PaymentNotificationEvent;
import com.yuno.commons.mdc.MDCUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Publishes payment domain events to Kafka.
 *
 * MDC propagation: before sending each record, MDCUtil.enrichProducerRecord()
 * copies the current MDC context (transactionId, correlationId, userId) into
 * the ProducerRecord headers. The consumer on the other end restores MDC from
 * those headers — maintaining the full trace across the async boundary.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentEventProducer {

    public static final String TOPIC_PAYMENT_INITIATED    = "payment.initiated";
    public static final String TOPIC_PAYMENT_NOTIFICATION = "payment.notification";

    private final KafkaTemplate<String, Object> kafkaTemplate;

    /**
     * Publishes PaymentInitiatedEvent to provider-service.
     * Key = paymentId ensures all events for a payment go to the same partition.
     */
    public void publishPaymentInitiated(PaymentInitiatedEvent event) {
        ProducerRecord<String, Object> record =
                new ProducerRecord<>(TOPIC_PAYMENT_INITIATED, event.getPaymentId(), event);

        // Copy MDC → Kafka headers before sending
        MDCUtil.enrichProducerRecord(record);

        kafkaTemplate.send(record)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish PaymentInitiatedEvent for paymentId={}: {}",
                                event.getPaymentId(), ex.getMessage(), ex);
                    } else {
                        log.info("Published PaymentInitiatedEvent | paymentId={} | partition={} | offset={}",
                                event.getPaymentId(),
                                result.getRecordMetadata().partition(),
                                result.getRecordMetadata().offset());
                    }
                });
    }

    /**
     * Publishes PaymentNotificationEvent to notification-service.
     */
    public void publishPaymentNotification(PaymentNotificationEvent event) {
        ProducerRecord<String, Object> record =
                new ProducerRecord<>(TOPIC_PAYMENT_NOTIFICATION, event.getPaymentId(), event);

        MDCUtil.enrichProducerRecord(record);

        kafkaTemplate.send(record)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish PaymentNotificationEvent for paymentId={}: {}",
                                event.getPaymentId(), ex.getMessage(), ex);
                    } else {
                        log.info("Published PaymentNotificationEvent | paymentId={} | status={}",
                                event.getPaymentId(), event.getStatus());
                    }
                });
    }
}
