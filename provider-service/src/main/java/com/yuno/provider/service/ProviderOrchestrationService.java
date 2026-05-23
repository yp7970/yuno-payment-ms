package com.yuno.provider.service;

import com.yuno.commons.enums.ProviderType;
import com.yuno.commons.events.PaymentInitiatedEvent;
import com.yuno.commons.events.PaymentResultEvent;
import com.yuno.commons.mdc.MDCKeys;
import com.yuno.provider.exception.ProviderException;
import com.yuno.provider.mapper.ProviderCallMapper;
import com.yuno.provider.model.ProviderCall;
import com.yuno.provider.provider.PaymentProviderConnector;
import com.yuno.provider.provider.ProviderCallResponse;
import com.yuno.provider.routing.PaymentRoutingEngine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Core orchestration logic for provider-service.
 *
 * orchestrate() Flow:
 *   1. Determine primary provider via routing engine (CARD→A, UPI→B)
 *   2. Call primary — @Retryable handles up to 3 attempts internally
 *   3. If primary exhausts retries → ProviderException propagates here
 *   4. Attempt failover provider (also has its own @Retryable)
 *   5. If both fail → build FAILED result
 *   6. Persist audit record via MyBatis INSERT (ProviderCallMapper)
 *   7. Return PaymentResultEvent for Kafka publication
 *
 * MyBatis INSERT here:
 *   - One row per payment orchestration (final outcome)
 *   - Direct SQL in ProviderCallMapper.xml — no ORM overhead
 *   - Executes AFTER provider call — never holds a connection open during I/O
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ProviderOrchestrationService {

    private final PaymentRoutingEngine routingEngine;
    private final ProviderCallMapper providerCallMapper;

    public PaymentResultEvent orchestrate(PaymentInitiatedEvent event) {
        String paymentId = event.getPaymentId();

        log.info("Orchestrating payment={} method={} amount={} {}",
                paymentId, event.getPaymentMethod(), event.getAmount(), event.getCurrency());

        PaymentProviderConnector primary  = routingEngine.getPrimary(event.getPaymentMethod());
        PaymentProviderConnector failover = routingEngine.getFailover(event.getPaymentMethod());

        ProviderCallResponse response;
        boolean failoverUsed = false;
        int retryCount = 1;

        // ── Step 1: Primary provider (has internal @Retryable) ───────────
        try {
            response = primary.process(event);
            log.info("Primary provider {} succeeded for payment={}", primary.getProviderType(), paymentId);

        } catch (ProviderException primaryEx) {
            log.warn("Primary provider {} exhausted retries for payment={}. Failover → {}",
                    primary.getProviderType(), paymentId, failover.getProviderType());

            retryCount = 3; // primary used 3 attempts
            failoverUsed = true;

            // ── Step 2: Failover provider (also has @Retryable) ──────────
            try {
                response = failover.process(event);
                log.info("Failover provider {} succeeded for payment={}", failover.getProviderType(), paymentId);
                retryCount += 1; // at least 1 failover attempt

            } catch (ProviderException failoverEx) {
                log.error("Both providers exhausted for payment={}. PRIMARY={} FAILOVER={}",
                        paymentId, primaryEx.getMessage(), failoverEx.getMessage());

                retryCount += 3; // failover also used 3 attempts
                response = ProviderCallResponse.failure(
                        failover.getProviderType(),
                        String.format("Primary(%s): %s | Failover(%s): %s",
                                primary.getProviderType(), primaryEx.getMessage(),
                                failover.getProviderType(), failoverEx.getMessage()),
                        0L
                );
            }
        }

        // ── Step 3: Persist audit record via MyBatis (no JPA) ────────────
        ProviderType finalProvider = response.getProvider() != null
                ? response.getProvider()
                : (failoverUsed ? failover.getProviderType() : primary.getProviderType());

        ProviderCall auditRecord = ProviderCall.builder()
                .id(UUID.randomUUID())
                .paymentId(UUID.fromString(paymentId))
                .providerType(finalProvider)
                .paymentMethod(event.getPaymentMethod())
                .amount(event.getAmount())
                .currency(event.getCurrency())
                .success(response.isSuccess())
                .providerTransactionId(response.getTransactionId())
                .errorMessage(response.getErrorMessage())
                .retryCount(retryCount)
                .failoverUsed(failoverUsed)
                .processingTimeMs(response.getProcessingTimeMs())
                .correlationId(MDC.get(MDCKeys.CORRELATION_ID))
                .build();

        // MyBatis direct INSERT — runs after provider call, no connection held during I/O
        providerCallMapper.insert(auditRecord);
        log.info("Provider audit record inserted via MyBatis | payment={} | success={} | provider={}",
                paymentId, response.isSuccess(), finalProvider);

        // ── Step 4: Build result event for Kafka ─────────────────────────
        return PaymentResultEvent.builder()
                .paymentId(paymentId)
                .success(response.isSuccess())
                .providerUsed(finalProvider)
                .providerTransactionId(response.getTransactionId())
                .errorMessage(response.getErrorMessage())
                .retryCount(retryCount)
                .failoverUsed(failoverUsed)
                .correlationId(event.getCorrelationId())
                .userId(event.getUserId())
                .build();
    }
}
