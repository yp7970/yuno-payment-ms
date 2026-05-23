package com.yuno.idempotency;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.yuno.idempotency.mapper")
public class IdempotencyServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(IdempotencyServiceApplication.class, args);
    }
}
