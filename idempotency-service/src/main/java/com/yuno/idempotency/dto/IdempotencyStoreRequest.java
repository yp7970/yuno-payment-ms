package com.yuno.idempotency.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class IdempotencyStoreRequest {
    @NotBlank private String idempotencyKey;
    @NotBlank private String responseBody;
    private int httpStatus;
}
