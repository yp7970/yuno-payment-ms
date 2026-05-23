package com.yuno.payment;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * Payment Service — accepts payment requests, publishes events to Kafka,
 * consumes payment results, and owns the payments table.
 *
 * Uses MyBatis (XML mappers) for all database access — no JPA/Hibernate.
 * Feign client for synchronous idempotency checks.
 */
@SpringBootApplication
@EnableFeignClients
@MapperScan("com.yuno.payment.mapper")
public class PaymentServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(PaymentServiceApplication.class, args);
    }
}
