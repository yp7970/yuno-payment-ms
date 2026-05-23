package com.yuno.idempotency.controller;

import com.yuno.commons.dto.ApiResponse;
import com.yuno.idempotency.dto.IdempotencyCheckResponse;
import com.yuno.idempotency.dto.IdempotencyStoreRequest;
import com.yuno.idempotency.service.IdempotencyService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Internal REST API — consumed only by payment-service via Feign client.
 * Not exposed to end users through the gateway in production.
 *
 * GET  /idempotency/{key}  → check if key exists (200 found, 404 not found)
 * POST /idempotency        → store a response for future duplicate requests
 */
@RestController
@RequestMapping("/idempotency")
@RequiredArgsConstructor
@Slf4j
public class IdempotencyController {

    private final IdempotencyService idempotencyService;

    @GetMapping("/{key}")
    public ResponseEntity<ApiResponse<IdempotencyCheckResponse>> check(@PathVariable String key) {
        log.info("GET /idempotency/{}", key);
        IdempotencyCheckResponse response = idempotencyService.check(key);

        if (response.isFound()) {
            return ResponseEntity.ok(ApiResponse.success(response));
        }
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error("No idempotency record found for key: " + key));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<Void>> store(@Valid @RequestBody IdempotencyStoreRequest request) {
        log.info("POST /idempotency key={}", request.getIdempotencyKey());
        idempotencyService.store(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(null, "Stored"));
    }
}
