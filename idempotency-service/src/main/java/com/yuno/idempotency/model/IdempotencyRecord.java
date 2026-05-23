package com.yuno.idempotency.model;

import lombok.*;
import java.time.LocalDateTime;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class IdempotencyRecord {
    private Long id;
    private String idempotencyKey;
    private String responseBody;
    private int httpStatus;
    private LocalDateTime expiresAt;
    private LocalDateTime createdAt;
}
