package com.yuno.commons.events;

import com.yuno.commons.enums.PaymentMethod;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Kafka event: payment-service → provider-service
 * Topic: payment.initiated
 *
 * Carries everything provider-service needs to route and execute the payment.
 * MDC fields (correlationId, userId) are included so provider-service can
 * restore full logging context from a Kafka header + this payload.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
public class PaymentInitiatedEvent {

    /** Internal payment UUID — becomes transactionId in MDC. */
    private String paymentId;

    private String idempotencyKey;
    private PaymentMethod paymentMethod;
    private BigDecimal amount;
    private String currency;
    private String description;

    /** From X-User-Id header — propagated for logging and notifications. */
    private String userId;

    /** From X-Correlation-Id header — ties gateway request to all Kafka hops. */
    private String correlationId;

    @Builder.Default
    private LocalDateTime timestamp = LocalDateTime.now();
}
