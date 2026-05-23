package com.yuno.provider.model;

import com.yuno.commons.enums.PaymentMethod;
import com.yuno.commons.enums.ProviderType;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Audit record of each provider call attempt.
 * Inserted by MyBatis after each provider invocation — success or failure.
 */
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ProviderCall {
    private UUID id;
    private UUID paymentId;
    private ProviderType providerType;
    private PaymentMethod paymentMethod;
    private BigDecimal amount;
    private String currency;
    private boolean success;
    private String providerTransactionId;
    private String errorMessage;
    private int retryCount;
    private boolean failoverUsed;
    private long processingTimeMs;
    private String correlationId;
    private LocalDateTime createdAt;
}
