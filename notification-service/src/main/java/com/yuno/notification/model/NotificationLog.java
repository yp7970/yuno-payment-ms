package com.yuno.notification.model;

import com.yuno.commons.enums.PaymentMethod;
import com.yuno.commons.enums.PaymentStatus;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class NotificationLog {
    private Long id;
    private String paymentId;
    private String userId;
    private PaymentStatus paymentStatus;
    private PaymentMethod paymentMethod;
    private BigDecimal amount;
    private String currency;
    private String providerTransactionId;
    private String notificationType;
    private String deliveryStatus;
    private String correlationId;
    private LocalDateTime createdAt;
}
