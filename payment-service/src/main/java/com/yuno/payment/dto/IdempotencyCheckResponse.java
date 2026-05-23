package com.yuno.payment.dto;

import lombok.*;

/**
 * Response from GET /idempotency/{key} — carries cached PaymentResponse JSON.
 */
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class IdempotencyCheckResponse {
    private boolean found;
    private String responseBody;  // serialised PaymentResponse JSON
    private int httpStatus;
}
