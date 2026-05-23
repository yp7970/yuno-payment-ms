package com.yuno.payment.dto;

import lombok.*;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class IdempotencyStoreRequest {
    private String idempotencyKey;
    private String responseBody;   // serialised PaymentResponse JSON
    private int httpStatus;
}
