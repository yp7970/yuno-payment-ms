package com.yuno.provider.provider;

import com.yuno.commons.enums.ProviderType;
import com.yuno.commons.events.PaymentInitiatedEvent;
import com.yuno.provider.exception.ProviderException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Provider A — primary connector for CARD payments.
 *
 * @Retryable: 3 max attempts, exponential backoff 500ms → 1s → 2s.
 * @Recover: re-throws after exhaustion so orchestration engine initiates failover.
 *
 * In production: replace simulateCall() with a real HTTP client (WebClient/RestTemplate)
 * calling the PSP API with proper auth headers, request signing, and response parsing.
 */
@Component
@Slf4j
public class ProviderAConnector implements PaymentProviderConnector {

    @Value("${provider.a.failure-rate:0.2}")
    private double failureRate;

    @Value("${provider.a.latency-ms:200}")
    private long latencyMs;

    @Override
    @Retryable(
            retryFor = ProviderException.class,
            maxAttempts = 3,
            backoff = @Backoff(delay = 500, multiplier = 2.0, maxDelay = 5000)
    )
    public ProviderCallResponse process(PaymentInitiatedEvent event) {
        log.info("[PROVIDER_A] Processing payment={} method={} amount={} {}",
                event.getPaymentId(), event.getPaymentMethod(),
                event.getAmount(), event.getCurrency());

        long start = System.currentTimeMillis();
        simulateLatency();

        if (ThreadLocalRandom.current().nextDouble() < failureRate) {
            long ms = System.currentTimeMillis() - start;
            log.warn("[PROVIDER_A] Transient failure for payment={} ({}ms)", event.getPaymentId(), ms);
            throw new ProviderException(ProviderType.PROVIDER_A, "Connection timeout");
        }

        long ms = System.currentTimeMillis() - start;
        String txnId = "PA-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16).toUpperCase();
        log.info("[PROVIDER_A] SUCCESS payment={} txn={} {}ms", event.getPaymentId(), txnId, ms);
        return ProviderCallResponse.success(ProviderType.PROVIDER_A, txnId, ms);
    }

    @Recover
    public ProviderCallResponse recover(ProviderException ex, PaymentInitiatedEvent event) {
        log.error("[PROVIDER_A] Exhausted retries for payment={}. Initiating failover.", event.getPaymentId());
        throw ex;
    }

    @Override
    public ProviderType getProviderType() { return ProviderType.PROVIDER_A; }

    private void simulateLatency() {
        try { if (latencyMs > 0) Thread.sleep(latencyMs); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
}
