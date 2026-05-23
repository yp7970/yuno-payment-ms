package com.yuno.payment.controller;

import com.yuno.commons.dto.ApiResponse;
import com.yuno.commons.enums.PaymentStatus;
import com.yuno.commons.mdc.MDCKeys;
import com.yuno.payment.dto.CreatePaymentRequest;
import com.yuno.payment.dto.PaymentResponse;
import com.yuno.payment.service.PaymentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * REST controller — thin layer, delegates everything to PaymentService.
 *
 * POST /payments  → 202 Accepted (async, client polls for result)
 * GET  /payments/{id} → 200 OK with current payment state
 * GET  /payments?status= → 200 OK with filtered list
 *
 * Note: The gateway routes /api/payments → /payments (strips /api prefix).
 */
@RestController
@RequestMapping("/payments")
@RequiredArgsConstructor
@Slf4j
public class PaymentController {

    private final PaymentService paymentService;

    @PostMapping
    public ResponseEntity<ApiResponse<PaymentResponse>> createPayment(
            @RequestHeader(MDCKeys.HEADER_IDEMPOTENCY_KEY) String idempotencyKey,
            @Valid @RequestBody CreatePaymentRequest request) {

        log.info("POST /payments | method={} | amount={} {} | key={}",
                request.getPaymentMethod(), request.getAmount(), request.getCurrency(), idempotencyKey);

        PaymentResponse response = paymentService.createPayment(request, idempotencyKey);

        // 202 Accepted — payment is queued, not yet processed
        return ResponseEntity
                .status(HttpStatus.ACCEPTED)
                .body(ApiResponse.success(response, "Payment queued. Poll GET /payments/{id} for status."));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<PaymentResponse>> getPayment(@PathVariable UUID id) {
        log.info("GET /payments/{}", id);
        return ResponseEntity.ok(ApiResponse.success(paymentService.getPayment(id)));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<PaymentResponse>>> listPayments(
            @RequestParam(required = false) PaymentStatus status) {
        log.info("GET /payments | statusFilter={}", status);
        return ResponseEntity.ok(ApiResponse.success(paymentService.getPaymentsByStatus(status)));
    }
}
