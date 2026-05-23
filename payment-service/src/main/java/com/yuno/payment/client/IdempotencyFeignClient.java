package com.yuno.payment.client;

import com.yuno.commons.dto.ApiResponse;
import com.yuno.payment.dto.IdempotencyCheckResponse;
import com.yuno.payment.dto.IdempotencyStoreRequest;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Feign client for synchronous calls to idempotency-service.
 *
 * Why synchronous (Feign) and not Kafka for idempotency?
 * The idempotency check MUST complete before we process the payment.
 * We need the answer "is this a duplicate?" before saving or routing.
 * Kafka is async — we can't wait for a response in a listener pattern
 * without request-reply complexity. Feign gives us a clean sync call.
 *
 * Resilience: if idempotency-service is down, FeignException propagates.
 * The payment-service catches it and proceeds cautiously (logs a warning
 * and allows the payment through — better to accept a potential duplicate
 * than to reject valid payments due to an infra failure).
 */
@FeignClient(name = "idempotency-service", url = "${service.idempotency.url:http://localhost:8083}")
public interface IdempotencyFeignClient {

    /**
     * Check if an idempotency key has an existing response.
     * Returns 200 with the cached response wrapped in ApiResponse, or 404 if new key.
     *
     * IMPORTANT: The server returns { "success": true, "data": { "found": true, ... } }
     * so the Feign client must declare ApiResponse<IdempotencyCheckResponse> to deserialize
     * correctly. Declaring the raw DTO here would cause Jackson to map the top-level
     * { success, data, timestamp } into the DTO fields, leaving found=false on every call.
     */
    @GetMapping("/idempotency/{key}")
    ResponseEntity<ApiResponse<IdempotencyCheckResponse>> check(@PathVariable("key") String key);

    /**
     * Store a payment response for future duplicate requests.
     * Called after a payment is successfully processed (SUCCESS or FAILED).
     */
    @PostMapping("/idempotency")
    ResponseEntity<Void> store(@RequestBody IdempotencyStoreRequest request);
}
