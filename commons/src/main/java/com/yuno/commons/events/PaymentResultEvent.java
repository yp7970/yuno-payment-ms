package com.yuno.commons.events;

import com.yuno.commons.enums.ProviderType;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Kafka event: provider-service → payment-service
 * Topic: payment.result
 *
 * Carries the outcome of the provider execution so payment-service
 * can update payment status in its own database.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
public class PaymentResultEvent {

    private String paymentId;
    private boolean success;
    private ProviderType providerUsed;

    /** Set on success — provider's own reference for the transaction. */
    private String providerTransactionId;

    /** Set on failure — combined error from primary + failover providers. */
    private String errorMessage;

    private int retryCount;
    private boolean failoverUsed;

    /** Propagated for MDC restoration in payment-service consumer. */
    private String correlationId;
    private String userId;

    @Builder.Default
    private LocalDateTime timestamp = LocalDateTime.now();
}
