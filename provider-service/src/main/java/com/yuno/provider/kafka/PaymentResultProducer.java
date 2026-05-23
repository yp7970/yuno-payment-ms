package com.yuno.provider.kafka;

import com.yuno.commons.events.PaymentResultEvent;
import com.yuno.commons.mdc.MDCUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Publishes PaymentResultEvent to payment-service.
 * MDC is copied into Kafka headers so payment-service consumer
 * can restore transactionId/correlationId/userId for its own logs.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentResultProducer {

    public static final String TOPIC_PAYMENT_RESULT = "payment.result";

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public void publishResult(PaymentResultEvent event) {
        ProducerRecord<String, Object> record =
                new ProducerRecord<>(TOPIC_PAYMENT_RESULT, event.getPaymentId(), event);

        // Copy current MDC → Kafka headers for downstream consumer MDC restoration
        MDCUtil.enrichProducerRecord(record);

        kafkaTemplate.send(record)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish PaymentResultEvent for paymentId={}: {}",
                                event.getPaymentId(), ex.getMessage(), ex);
                    } else {
                        log.info("Published PaymentResultEvent | paymentId={} | success={} | partition={} | offset={}",
                                event.getPaymentId(), event.isSuccess(),
                                result.getRecordMetadata().partition(),
                                result.getRecordMetadata().offset());
                    }
                });
    }
}
