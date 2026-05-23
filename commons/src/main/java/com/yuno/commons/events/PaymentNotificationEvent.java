package com.yuno.commons.events;

import com.yuno.commons.enums.PaymentMethod;
import com.yuno.commons.enums.PaymentStatus;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Kafka event: payment-service → notification-service
 * Topic: payment.notification
 *
 * Carries everything notification-service needs to inform the user
 * (email, push, webhook) about their payment outcome.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
public class PaymentNotificationEvent {

    private String paymentId;
    private String userId;
    private PaymentMethod paymentMethod;
    private BigDecimal amount;
    private String currency;
    private PaymentStatus status;
    private String providerTransactionId;
    private String failureReason;

    /** Propagated for MDC restoration in notification-service consumer. */
    private String correlationId;

    @Builder.Default
    private LocalDateTime timestamp = LocalDateTime.now();
}
