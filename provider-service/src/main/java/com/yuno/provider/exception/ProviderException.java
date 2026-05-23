package com.yuno.provider.exception;

import com.yuno.commons.enums.ProviderType;

/**
 * Thrown by a provider connector on transient failure.
 * Spring Retry intercepts this to trigger @Retryable backoff.
 * After exhausting retries, @Recover re-throws it so the
 * orchestration service can initiate failover.
 */
public class ProviderException extends RuntimeException {

    private final ProviderType provider;

    public ProviderException(ProviderType provider, String message) {
        super(String.format("[%s] %s", provider, message));
        this.provider = provider;
    }

    public ProviderType getProvider() {
        return provider;
    }
}
