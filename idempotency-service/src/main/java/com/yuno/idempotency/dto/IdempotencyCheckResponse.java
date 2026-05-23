package com.yuno.idempotency.dto;

import lombok.*;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class IdempotencyCheckResponse {
    private boolean found;
    private String responseBody;
    private int httpStatus;
}
