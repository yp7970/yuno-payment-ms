package com.yuno.commons.enums;

/**
 * Full lifecycle of a payment across all microservices.
 *
 * PENDING    → saved by payment-service, Kafka event published
 * PROCESSING → provider-service is actively calling a provider
 * RETRYING   → primary provider failed; failover in progress
 * SUCCESS    → provider confirmed payment
 * FAILED     → all provider attempts (primary + failover) exhausted
 */
public enum PaymentStatus {
    PENDING,
    PROCESSING,
    RETRYING,
    SUCCESS,
    FAILED
}
