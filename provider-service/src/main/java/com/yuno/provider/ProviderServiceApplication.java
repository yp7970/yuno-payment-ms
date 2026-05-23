package com.yuno.provider;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.retry.annotation.EnableRetry;

/**
 * Provider Service — consumes PaymentInitiatedEvent, routes to the correct
 * payment provider (A/B), handles retry + failover, and publishes the result.
 *
 * Uses MyBatis to record each provider call attempt for audit and analytics.
 * Spring Retry drives the retry/failover logic on provider connectors.
 */
@SpringBootApplication
@EnableRetry
@MapperScan("com.yuno.provider.mapper")
public class ProviderServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(ProviderServiceApplication.class, args);
    }
}
