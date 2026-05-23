package com.yuno.payment.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuno.commons.dto.ApiResponse;
import com.yuno.commons.enums.PaymentStatus;
import com.yuno.commons.events.PaymentInitiatedEvent;
import com.yuno.commons.events.PaymentNotificationEvent;
import com.yuno.commons.events.PaymentResultEvent;
import com.yuno.commons.mdc.MDCKeys;
import com.yuno.commons.mdc.MDCUtil;
import com.yuno.payment.client.IdempotencyFeignClient;
import com.yuno.payment.dto.*;
import com.yuno.payment.exception.PaymentNotFoundException;
import com.yuno.payment.kafka.PaymentEventProducer;
import com.yuno.payment.mapper.PaymentMapper;
import com.yuno.payment.model.Payment;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Core payment service — orchestrates the async payment creation flow.
 *
 * createPayment() Flow:
 *   1. [SYNC]  Feign → idempotency-service: check duplicate key
 *   2.         If duplicate → return cached PaymentResponse
 *   3. [SYNC]  MyBatis INSERT → payments table (PENDING state)
 *   4. [SYNC]  MDC: set transactionId = paymentId
 *   5. [ASYNC] Kafka → publish PaymentInitiatedEvent (with MDC headers)
 *   6.         Return 202 Accepted {paymentId, status: PENDING}
 *
 * handlePaymentResult() Flow (called by Kafka consumer):
 *   7. [SYNC]  MyBatis UPDATE → payments table (SUCCESS or FAILED)
 *   8. [SYNC]  Feign → idempotency-service: store response
 *   9. [ASYNC] Kafka → publish PaymentNotificationEvent
 *
 * MyBatis replaces JPA throughout — direct SQL, no ORM overhead,
 * no dirty checking, no proxy objects. The ResultSet → POJO mapping
 * is explicit and declared in PaymentMapper.xml.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentService {

    private final PaymentMapper paymentMapper;
    private final PaymentEventProducer eventProducer;
    private final IdempotencyFeignClient idempotencyClient;
    private final ObjectMapper objectMapper;

    public PaymentResponse createPayment(CreatePaymentRequest request, String idempotencyKey) {
        log.info("createPayment | method={} | amount={} {} | idempotencyKey={}",
                request.getPaymentMethod(), request.getAmount(), request.getCurrency(), idempotencyKey);

        // Step 1-2: Idempotency check (sync Feign call to idempotency-service)
        PaymentResponse cached = checkIdempotency(idempotencyKey);
        if (cached != null) {
            log.info("Idempotency HIT — returning cached response for key={}", idempotencyKey);
            return cached;
        }

        // Step 3: Save payment in PENDING state via MyBatis
        String paymentId = UUID.randomUUID().toString();
        Payment payment = Payment.builder()
                .id(UUID.fromString(paymentId))
                .idempotencyKey(idempotencyKey)
                .paymentMethod(request.getPaymentMethod())
                .amount(request.getAmount())
                .currency(request.getCurrency())
                .description(request.getDescription())
                .status(PaymentStatus.PENDING)
                .retryCount(0)
                .failoverUsed(false)
                .userId(MDC.get(MDCKeys.USER_ID))          // ← correct: userId from X-User-Id header
                .correlationId(MDC.get(MDCKeys.CORRELATION_ID))
                .build();

        // MyBatis direct INSERT — no EntityManager, no session flush, no dirty check
        paymentMapper.insert(payment);
        log.info("Payment {} saved in PENDING state via MyBatis INSERT", paymentId);

        // Step 4: Now we have the paymentId — set it as transactionId in MDC
        MDCUtil.setTransactionId(paymentId);

        // Step 5: Publish Kafka event (async — returns 202 immediately after)
        PaymentInitiatedEvent event = PaymentInitiatedEvent.builder()
                .paymentId(paymentId)
                .idempotencyKey(idempotencyKey)
                .paymentMethod(request.getPaymentMethod())
                .amount(request.getAmount())
                .currency(request.getCurrency())
                .description(request.getDescription())
                .correlationId(MDC.get(MDCKeys.CORRELATION_ID))
                .userId(MDC.get(MDCKeys.USER_ID))
                .build();

        eventProducer.publishPaymentInitiated(event);
        log.info("PaymentInitiatedEvent published for paymentId={}", paymentId);

        // Step 6: Return 202 — client polls GET /payments/{id} for final status
        return PaymentResponse.builder()
                .paymentId(UUID.fromString(paymentId))
                .idempotencyKey(idempotencyKey)
                .paymentMethod(request.getPaymentMethod())
                .amount(request.getAmount())
                .currency(request.getCurrency())
                .description(request.getDescription())
                .status(PaymentStatus.PENDING)
                .build();
    }

    /**
     * Called by PaymentResultConsumer when provider-service publishes the result.
     * Updates payment status via MyBatis UPDATE, stores idempotency, fires notification.
     */
    public void handlePaymentResult(PaymentResultEvent event) {
        UUID paymentId = UUID.fromString(event.getPaymentId());

        PaymentStatus finalStatus = event.isSuccess() ? PaymentStatus.SUCCESS : PaymentStatus.FAILED;

        // Step 7: Targeted UPDATE — only status-related columns, MyBatis
        paymentMapper.updateStatusAfterResult(
                paymentId,
                finalStatus,
                event.getProviderUsed(),
                event.getProviderTransactionId(),
                event.getRetryCount(),
                event.isFailoverUsed(),
                event.getErrorMessage()
        );

        log.info("Payment {} updated to {} via MyBatis UPDATE | provider={} | failover={}",
                event.getPaymentId(), finalStatus,
                event.getProviderUsed(), event.isFailoverUsed());

        // Fetch updated payment for response building
        Payment updated = paymentMapper.findById(paymentId);
        if (updated == null) {
            log.error("CRITICAL: payment {} not found after status update", paymentId);
            return;
        }

        PaymentResponse response = PaymentResponse.from(updated);

        // Step 8: Store in idempotency-service
        storeIdempotencyResponse(updated.getIdempotencyKey(), response);

        // Step 9: Publish notification event
        PaymentNotificationEvent notification = PaymentNotificationEvent.builder()
                .paymentId(event.getPaymentId())
                .userId(event.getUserId())
                .paymentMethod(updated.getPaymentMethod())
                .amount(updated.getAmount())
                .currency(updated.getCurrency())
                .status(finalStatus)
                .providerTransactionId(event.getProviderTransactionId())
                .failureReason(event.getErrorMessage())
                .correlationId(event.getCorrelationId())
                .build();

        eventProducer.publishPaymentNotification(notification);
    }

    public PaymentResponse getPayment(UUID paymentId) {
        // MyBatis SELECT — direct JDBC, parses ResultSet → Payment POJO
        Payment payment = paymentMapper.findById(paymentId);
        if (payment == null) {
            throw new PaymentNotFoundException("id", paymentId.toString());
        }
        return PaymentResponse.from(payment);
    }

    public List<PaymentResponse> getPaymentsByStatus(PaymentStatus status) {
        return paymentMapper.findByStatus(status)
                .stream()
                .map(PaymentResponse::from)
                .collect(Collectors.toList());
    }

    // ── private helpers ───────────────────────────────────────────────────

    private PaymentResponse checkIdempotency(String idempotencyKey) {
        try {
            ResponseEntity<ApiResponse<IdempotencyCheckResponse>> resp = idempotencyClient.check(idempotencyKey);
            if (resp.getStatusCode().is2xxSuccessful()
                    && resp.getBody() != null
                    && resp.getBody().getData() != null
                    && resp.getBody().getData().isFound()) {
                // The stored responseBody is a serialised PaymentResponse JSON
                return objectMapper.readValue(resp.getBody().getData().getResponseBody(), PaymentResponse.class);
            }
        } catch (FeignException.NotFound e) {
            // 404 from idempotency-service = new key, proceed normally
        } catch (FeignException e) {
            // idempotency-service unavailable — log warning and proceed
            // Better to risk a rare duplicate than reject all payments
            log.warn("Idempotency service unavailable for key={}: {}", idempotencyKey, e.getMessage());
        } catch (JsonProcessingException e) {
            log.error("Failed to deserialise cached idempotency response for key={}", idempotencyKey, e);
        }
        return null;
    }

    private void storeIdempotencyResponse(String idempotencyKey, PaymentResponse response) {
        try {
            String json = objectMapper.writeValueAsString(response);
            idempotencyClient.store(IdempotencyStoreRequest.builder()
                    .idempotencyKey(idempotencyKey)
                    .responseBody(json)
                    .httpStatus(202)
                    .build());
        } catch (Exception e) {
            // Non-fatal — payment itself succeeded; log and continue
            log.error("Failed to store idempotency response for key={}: {}", idempotencyKey, e.getMessage());
        }
    }
}
