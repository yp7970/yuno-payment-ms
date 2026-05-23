package com.yuno.commons.mdc;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.Headers;
import org.slf4j.MDC;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

/**
 * Utility for propagating MDC context across HTTP boundaries and Kafka hops.
 *
 * ─────────────────────────────────────────────────────────────────────────
 * HTTP Flow (handled by MDCFilter in each service):
 *   Request IN  → set(transactionId, correlationId, userId, serviceName)
 *   Request OUT → clear()
 *
 * Kafka Producer Flow (called by each Kafka producer before sending):
 *   enrichProducerRecord() → copies current MDC into ProducerRecord headers
 *
 * Kafka Consumer Flow (called at start of each @KafkaListener method):
 *   populateFromConsumerRecord() → restores MDC from ConsumerRecord headers
 *   clear() in finally block → avoids thread-local leakage in thread pools
 *
 * ─────────────────────────────────────────────────────────────────────────
 * Result: every log line across payment-service, provider-service,
 * idempotency-service, and notification-service carries the same
 * transactionId and correlationId for a given payment, enabling
 * end-to-end trace reconstruction in any log aggregator.
 */
public final class MDCUtil {

    private MDCUtil() {}

    /** Set core MDC fields for a new request or Kafka consumer invocation. */
    public static void set(String transactionId, String correlationId, String userId, String serviceName) {
        setIfNotBlank(MDCKeys.TRANSACTION_ID, transactionId);
        setIfNotBlank(MDCKeys.CORRELATION_ID, correlationId);
        setIfNotBlank(MDCKeys.USER_ID, userId);
        setIfNotBlank(MDCKeys.SERVICE_NAME, serviceName);
    }

    public static void setTransactionId(String transactionId) {
        setIfNotBlank(MDCKeys.TRANSACTION_ID, transactionId);
    }

    public static void setCorrelationId(String correlationId) {
        setIfNotBlank(MDCKeys.CORRELATION_ID, correlationId);
    }

    public static void setUserId(String userId) {
        setIfNotBlank(MDCKeys.USER_ID, userId);
    }

    /**
     * Copy current MDC context into Kafka ProducerRecord headers.
     * Call this in every Kafka producer BEFORE sending the record.
     */
    public static void enrichProducerRecord(ProducerRecord<?, ?> record) {
        Headers headers = record.headers();
        addHeader(headers, MDCKeys.KAFKA_HEADER_TRANSACTION_ID, MDC.get(MDCKeys.TRANSACTION_ID));
        addHeader(headers, MDCKeys.KAFKA_HEADER_CORRELATION_ID, MDC.get(MDCKeys.CORRELATION_ID));
        addHeader(headers, MDCKeys.KAFKA_HEADER_USER_ID,        MDC.get(MDCKeys.USER_ID));
    }

    /**
     * Restore MDC from Kafka ConsumerRecord headers.
     * Call this at the START of every @KafkaListener method.
     * Always pair with clear() in a finally block.
     */
    public static void populateFromConsumerRecord(ConsumerRecord<?, ?> record, String serviceName) {
        Headers headers = record.headers();
        setIfNotBlank(MDCKeys.TRANSACTION_ID, extractHeader(headers, MDCKeys.KAFKA_HEADER_TRANSACTION_ID));
        setIfNotBlank(MDCKeys.CORRELATION_ID, extractHeader(headers, MDCKeys.KAFKA_HEADER_CORRELATION_ID));
        setIfNotBlank(MDCKeys.USER_ID,        extractHeader(headers, MDCKeys.KAFKA_HEADER_USER_ID));
        setIfNotBlank(MDCKeys.SERVICE_NAME,   serviceName);
    }

    /** Clear all MDC entries. Always call in finally blocks. */
    public static void clear() {
        MDC.remove(MDCKeys.TRANSACTION_ID);
        MDC.remove(MDCKeys.CORRELATION_ID);
        MDC.remove(MDCKeys.USER_ID);
        MDC.remove(MDCKeys.SERVICE_NAME);
    }

    public static Optional<String> getTransactionId() {
        return Optional.ofNullable(MDC.get(MDCKeys.TRANSACTION_ID));
    }

    // ── private helpers ──────────────────────────────────────────────────────

    private static void setIfNotBlank(String key, String value) {
        if (value != null && !value.isBlank()) {
            MDC.put(key, value);
        }
    }

    private static void addHeader(Headers headers, String key, String value) {
        if (value != null && !value.isBlank()) {
            headers.add(key, value.getBytes(StandardCharsets.UTF_8));
        }
    }

    private static String extractHeader(Headers headers, String key) {
        var header = headers.lastHeader(key);
        return header != null ? new String(header.value(), StandardCharsets.UTF_8) : null;
    }
}
