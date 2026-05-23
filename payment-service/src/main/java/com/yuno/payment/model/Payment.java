package com.yuno.payment.model;

import com.yuno.commons.enums.PaymentMethod;
import com.yuno.commons.enums.PaymentStatus;
import com.yuno.commons.enums.ProviderType;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Payment domain object — plain POJO, no JPA/Hibernate annotations.
 *
 * MyBatis maps ResultSet columns to this object via PaymentMapper.xml
 * using explicit ResultMap definitions. This gives us full control over
 * the SQL, avoids N+1 problems, and removes the ORM overhead of dirty
 * checking and proxy object creation that Hibernate introduces.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
public class Payment {

    private UUID id;
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
    private String userId;
    private String correlationId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
