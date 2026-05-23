package com.yuno.provider.provider;

import com.yuno.commons.enums.ProviderType;
import com.yuno.commons.events.PaymentInitiatedEvent;
import com.yuno.provider.exception.ProviderException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.retry.annotation.*;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

@Component
@Slf4j
public class ProviderBConnector implements PaymentProviderConnector {

    @Value("${provider.b.failure-rate:0.15}")
    private double failureRate;

    @Value("${provider.b.latency-ms:150}")
    private long latencyMs;

    @Override
    @Retryable(retryFor = ProviderException.class, maxAttempts = 3,
               backoff = @Backoff(delay = 500, multiplier = 2.0, maxDelay = 5000))
    public ProviderCallResponse process(PaymentInitiatedEvent event) {
        log.info("[PROVIDER_B] Processing payment={} method={}", event.getPaymentId(), event.getPaymentMethod());
        long start = System.currentTimeMillis();
        simulateLatency();

        if (ThreadLocalRandom.current().nextDouble() < failureRate) {
            long ms = System.currentTimeMillis() - start;
            log.warn("[PROVIDER_B] Transient failure payment={} ({}ms)", event.getPaymentId(), ms);
            throw new ProviderException(ProviderType.PROVIDER_B, "Upstream unavailable");
        }

        long ms = System.currentTimeMillis() - start;
        String txnId = "PB-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16).toUpperCase();
        log.info("[PROVIDER_B] SUCCESS payment={} txn={} {}ms", event.getPaymentId(), txnId, ms);
        return ProviderCallResponse.success(ProviderType.PROVIDER_B, txnId, ms);
    }

    @Recover
    public ProviderCallResponse recover(ProviderException ex, PaymentInitiatedEvent event) {
        log.error("[PROVIDER_B] Exhausted retries for payment={}. Initiating failover.", event.getPaymentId());
        throw ex;
    }

    @Override
    public ProviderType getProviderType() { return ProviderType.PROVIDER_B; }

    private void simulateLatency() {
        try { if (latencyMs > 0) Thread.sleep(latencyMs); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
}
