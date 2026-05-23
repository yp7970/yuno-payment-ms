package com.yuno.gateway.config;

import com.yuno.commons.mdc.MDCKeys;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.publisher.Mono;

/**
 * Configuration for the Spring Cloud Gateway Redis rate limiter.
 *
 * The RequestRateLimiter filter requires a KeyResolver bean to determine
 * how to partition the rate-limit buckets. Without this bean, the gateway
 * will throw a NoSuchBeanDefinitionException on startup.
 *
 * Strategy: Rate-limit per X-User-Id header (merchant/user level).
 *   - Each user gets their own token bucket (100 req/s, burst 200)
 *   - Anonymous requests (no X-User-Id) are bucketed together under "anonymous"
 *   - This is appropriate for a payment API where each merchant has their own quota
 *
 * In production, consider rate-limiting per API key or JWT subject instead.
 */
@Configuration
public class GatewayRateLimiterConfig {

    /**
     * Resolves the rate-limit key from the X-User-Id request header.
     * Falls back to "anonymous" when the header is absent.
     */
    @Bean
    public KeyResolver userKeyResolver() {
        return exchange -> {
            String userId = exchange.getRequest()
                    .getHeaders()
                    .getFirst(MDCKeys.HEADER_USER_ID);
            return Mono.just(userId != null && !userId.isBlank() ? userId : "anonymous");
        };
    }
}
