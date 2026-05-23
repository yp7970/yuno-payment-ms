package com.yuno.payment.kafka;

import com.yuno.commons.events.PaymentResultEvent;
import com.yuno.commons.mdc.MDCUtil;
import com.yuno.payment.service.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Kafka consumer: receives PaymentResultEvent from provider-service.
 * Topic: payment.result
 *
 * MDC restoration pattern:
 *   1. populateFromConsumerRecord() restores transactionId, correlationId, userId
 *      from Kafka headers (injected by provider-service's producer).
 *   2. Every log line in the processing thread now carries the same MDC
 *      as the original HTTP request — full end-to-end trace in logs.
 *   3. finally { MDCUtil.clear() } — prevents MDC leakage to next message
 *      since Kafka consumers run on a thread pool.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentResultConsumer {

    public static final String TOPIC_PAYMENT_RESULT = "payment.result";

    @Value("${spring.application.name}")
    private String serviceName;

    private final PaymentService paymentService;

    @KafkaListener(
            topics = TOPIC_PAYMENT_RESULT,
            groupId = "payment-service-group",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void consume(ConsumerRecord<String, PaymentResultEvent> record) {
        // Step 1: Restore MDC from Kafka headers before any logging
        MDCUtil.populateFromConsumerRecord(record, serviceName);

        try {
            PaymentResultEvent event = record.value();

            log.info("Consumed PaymentResultEvent | paymentId={} | success={} | provider={} | failover={}",
                    event.getPaymentId(), event.isSuccess(),
                    event.getProviderUsed(), event.isFailoverUsed());

            paymentService.handlePaymentResult(event);

        } catch (Exception e) {
            log.error("Error processing PaymentResultEvent | paymentId={} | error={}",
                    record.key(), e.getMessage(), e);
            // Re-throw so KafkaErrorHandler can retry or route to DLQ
            throw e;
        } finally {
            // Step 3: Always clear — thread pool reuse protection
            MDCUtil.clear();
        }
    }
}
