package com.yuno.payment.dto;

import com.yuno.commons.enums.PaymentMethod;
import jakarta.validation.constraints.*;
import lombok.*;
import java.math.BigDecimal;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class CreatePaymentRequest {

    @NotNull(message = "paymentMethod is required")
    private PaymentMethod paymentMethod;

    @NotNull(message = "amount is required")
    @DecimalMin(value = "0.0001", message = "amount must be greater than zero")
    @Digits(integer = 15, fraction = 4, message = "amount exceeds allowed precision")
    private BigDecimal amount;

    @NotBlank(message = "currency is required")
    @Size(min = 3, max = 3, message = "currency must be ISO 4217 3-letter code")
    @Pattern(regexp = "^[A-Z]{3}$", message = "currency must be uppercase (e.g. USD, INR)")
    private String currency;

    @Size(max = 256, message = "description must not exceed 256 characters")
    private String description;
}
