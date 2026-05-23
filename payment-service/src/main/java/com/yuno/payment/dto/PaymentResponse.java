package com.yuno.payment.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.yuno.commons.enums.PaymentMethod;
import com.yuno.commons.enums.PaymentStatus;
import com.yuno.commons.enums.ProviderType;
import com.yuno.payment.model.Payment;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder(toBuilder = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PaymentResponse {

    private UUID paymentId;
    private String idempotencyKey;
    private PaymentMethod paymentMethod;
    private BigDecimal amount;
    private String currency;
    private String description;
    private PaymentStatus status;
    private ProviderType providerUsed;
    private String providerTransactionId;
    private int retryCount;
    private boolean failoverUsed;
    private String failureReason;
    private String correlationId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static PaymentResponse from(Payment p) {
        return PaymentResponse.builder()
                .paymentId(p.getId())
                .idempotencyKey(p.getIdempotencyKey())
                .paymentMethod(p.getPaymentMethod())
                .amount(p.getAmount())
                .currency(p.getCurrency())
                .description(p.getDescription())
                .status(p.getStatus())
                .providerUsed(p.getProviderUsed())
                .providerTransactionId(p.getProviderTransactionId())
                .retryCount(p.getRetryCount())
                .failoverUsed(p.isFailoverUsed())
                .failureReason(p.getFailureReason())
                .correlationId(p.getCorrelationId())
                .createdAt(p.getCreatedAt())
                .updatedAt(p.getUpdatedAt())
                .build();
    }
}
