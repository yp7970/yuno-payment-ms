package com.yuno.provider.kafka;

import com.yuno.commons.events.PaymentInitiatedEvent;
import com.yuno.commons.mdc.MDCUtil;
import com.yuno.provider.service.ProviderOrchestrationService;
import com.yuno.commons.events.PaymentResultEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Kafka consumer: receives PaymentInitiatedEvent from payment-service.
 * Topic: payment.initiated
 *
 * MDC restoration pattern (same as PaymentResultConsumer in payment-service):
 *   1. populateFromConsumerRecord() → restores transactionId, correlationId, userId
 *      from Kafka headers written by payment-service's producer
 *   2. All log lines in this thread now carry the same MDC as the original request
 *   3. finally { MDCUtil.clear() } → prevents thread-local leakage
 *
 * This means a single search for transactionId=<paymentId> in your log aggregator
 * shows the COMPLETE journey: HTTP entry → payment saved → Kafka hop →
 * provider routing → provider call → result published → status updated.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentInitiatedConsumer {

    public static final String TOPIC_PAYMENT_INITIATED = "payment.initiated";

    @Value("${spring.application.name}")
    private String serviceName;

    private final ProviderOrchestrationService orchestrationService;
    private final PaymentResultProducer resultProducer;

    @KafkaListener(
            topics = TOPIC_PAYMENT_INITIATED,
            groupId = "provider-service-group",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void consume(ConsumerRecord<String, PaymentInitiatedEvent> record) {
        // Step 1: Restore MDC from Kafka headers before any logging
        MDCUtil.populateFromConsumerRecord(record, serviceName);

        try {
            PaymentInitiatedEvent event = record.value();

            log.info("Consumed PaymentInitiatedEvent | paymentId={} | method={} | amount={} {}",
                    event.getPaymentId(), event.getPaymentMethod(),
                    event.getAmount(), event.getCurrency());

            // Orchestrate: route → call provider → retry/failover → record audit
            PaymentResultEvent result = orchestrationService.orchestrate(event);

            // Publish result back to payment-service (MDC already in scope)
            resultProducer.publishResult(result);

        } catch (Exception e) {
            log.error("Error processing PaymentInitiatedEvent | paymentId={} | error={}",
                    record.key(), e.getMessage(), e);
            throw e; // re-throw for DLQ routing
        } finally {
            // Step 3: Always clear — thread pool reuse protection
            MDCUtil.clear();
        }
    }
}
