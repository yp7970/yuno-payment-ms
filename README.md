# Yuno Payment Orchestration — Microservices (v2.0.0)

A production-grade payment orchestration system built with **Java 17**, **Spring Boot 3.2.5**, and a microservices architecture. Implements full payment routing, idempotency, retry/failover, MDC-based distributed tracing, and async event-driven processing via Kafka.

---

## Architecture Overview

```
Client
  │
  ▼
┌─────────────────────────────────────────────────────────┐
│  api-gateway  :8080                                     │
│  • MDC correlation-id injection                         │
│  • Redis rate limiting (100 req/s per user, burst 200)  │
│  • Route /api/payments → payment-service                │
└───────────────────┬─────────────────────────────────────┘
                    │ HTTP
                    ▼
┌───────────────────────────────────┐    Feign (sync)    ┌─────────────────────────────┐
│  payment-service  :8081           │◄──────────────────►│  idempotency-service  :8083  │
│  • Accept payment requests        │                    │  • Redis 24h TTL fast path   │
│  • MyBatis INSERT (PENDING)       │                    │  • PostgreSQL durable backup │
│  • Publish PaymentInitiatedEvent  │                    │  • Scheduled DB cleanup       │
│  • Consume PaymentResultEvent     │                    └─────────────────────────────┘
│  • Publish PaymentNotificationEvent│
└──────────┬────────────────────────┘
           │ Kafka: payment.initiated
           ▼
┌───────────────────────────────────┐
│  provider-service  :8082          │
│  • CARD → ProviderA (primary)     │
│         → ProviderB (failover)    │
│  • UPI  → ProviderB (primary)     │
│         → ProviderA (failover)    │
│  • Spring Retry (3 attempts each) │
│  • MyBatis INSERT audit record    │
│  • Publish PaymentResultEvent     │
└──────────┬────────────────────────┘
           │ Kafka: payment.result
           ▼
    payment-service (consumer)
           │ Kafka: payment.notification
           ▼
┌───────────────────────────────────┐
│  notification-service  :8084      │
│  • Consume PaymentNotificationEvent│
│  • Log delivery audit via MyBatis │
│  (Email/Push/Webhook stub)        │
└───────────────────────────────────┘
```

---

## Microservices

| Service              | Port | Description                                          |
|----------------------|------|------------------------------------------------------|
| `api-gateway`        | 8080 | Spring Cloud Gateway — routing, MDC, rate limiting   |
| `payment-service`    | 8081 | Core payment CRUD, Kafka producer/consumer           |
| `provider-service`   | 8082 | Provider routing, retry/failover, audit logging      |
| `idempotency-service`| 8083 | Redis + PostgreSQL idempotency guard                 |
| `notification-service`| 8084 | Payment outcome notifications, audit log             |
| `commons`            | —    | Shared DTOs, Kafka events, MDC utilities, enums      |

---

## Technology Stack

| Concern          | Technology                                      |
|------------------|-------------------------------------------------|
| Language         | Java 17                                         |
| Framework        | Spring Boot 3.2.5                               |
| API Gateway      | Spring Cloud Gateway 2023.0.1                   |
| HTTP Client      | OpenFeign (payment → idempotency sync calls)    |
| Database ORM     | MyBatis 3.0.3 (XML mappers, no JPA/Hibernate)   |
| Database         | PostgreSQL 16 (4 databases, one per service)    |
| Cache            | Redis 7 (idempotency TTL, gateway rate limit)   |
| Messaging        | Apache Kafka (Confluent 7.6.0)                  |
| Migrations       | Flyway                                          |
| Retry            | Spring Retry (`@Retryable` + `@Recover`)        |
| Testing          | JUnit 5, Mockito, MockMvc, AssertJ              |
| Containerisation | Docker + Docker Compose                         |

---

## Key Features

### Payment Flow (Async)
1. `POST /api/payments` → gateway → payment-service (202 Accepted immediately)
2. payment-service saves `PENDING`, publishes `PaymentInitiatedEvent` to Kafka
3. provider-service routes by method, calls provider, retries up to 3×
4. On primary failure → failover to secondary provider (also 3 retries)
5. provider-service publishes `PaymentResultEvent` → payment-service updates status
6. payment-service stores idempotency record + publishes `PaymentNotificationEvent`
7. notification-service logs delivery record via MyBatis
8. Client polls `GET /api/payments/{id}` for final status

### Idempotency (Two-Tier)
- **Redis (fast path)**: sub-millisecond lookup, 24h TTL
- **PostgreSQL (fallback)**: durable record survives Redis restart
- On Redis miss: DB fallback + automatic Redis backfill
- Stored response is replayed on duplicate requests (no double-charge)

### MDC Distributed Tracing
Every log line across all services carries:
```
[trxId=<paymentId>] [corrId=<X-Correlation-Id>] [userId=<X-User-Id>] [svc=<serviceName>]
```
Propagation path: HTTP headers → MDCFilter → service logs → Kafka headers → consumer MDCUtil.populateFromConsumerRecord()

A single log query `transactionId:"abc-123"` reconstructs the complete payment journey across all 5 services.

### Provider Routing Table
| Payment Method | Primary    | Failover   |
|---------------|------------|------------|
| CARD          | PROVIDER_A | PROVIDER_B |
| UPI           | PROVIDER_B | PROVIDER_A |

### Rate Limiting
- Spring Cloud Gateway with Redis token bucket
- 100 req/s per user (X-User-Id), burst capacity 200
- Anonymous requests bucketed together

---

## Module Structure

```
yuno-payment-ms/
├── pom.xml                          ← Parent POM (open this in IntelliJ)
├── commons/                         ← Shared library (plain JAR, no Spring Boot plugin)
│   └── src/main/java/com/yuno/commons/
│       ├── dto/ApiResponse.java
│       ├── enums/{PaymentMethod,PaymentStatus,ProviderType}.java
│       ├── events/{PaymentInitiatedEvent,PaymentResultEvent,PaymentNotificationEvent}.java
│       └── mdc/{MDCKeys,MDCUtil}.java
├── api-gateway/
│   ├── Dockerfile
│   └── src/main/java/com/yuno/gateway/
│       ├── ApiGatewayApplication.java
│       ├── config/GatewayRateLimiterConfig.java  ← KeyResolver for rate limiting
│       └── filter/MDCGlobalFilter.java
├── payment-service/
│   ├── Dockerfile
│   └── src/main/java/com/yuno/payment/
│       ├── client/IdempotencyFeignClient.java
│       ├── config/{KafkaConfig,MDCFilter,UUIDTypeHandler}.java
│       ├── controller/PaymentController.java
│       ├── dto/{CreatePaymentRequest,PaymentResponse,...}.java
│       ├── exception/{GlobalExceptionHandler,PaymentNotFoundException}.java
│       ├── kafka/{PaymentEventProducer,PaymentResultConsumer}.java
│       ├── mapper/PaymentMapper.java  +  resources/mapper/PaymentMapper.xml
│       ├── model/Payment.java
│       └── service/PaymentService.java
├── provider-service/
│   ├── Dockerfile
│   └── src/main/java/com/yuno/provider/
│       ├── config/{KafkaConfig,MDCFilter,UUIDTypeHandler}.java
│       ├── exception/ProviderException.java
│       ├── kafka/{PaymentInitiatedConsumer,PaymentResultProducer}.java
│       ├── mapper/ProviderCallMapper.java
│       ├── model/ProviderCall.java
│       ├── provider/{PaymentProviderConnector,ProviderAConnector,ProviderBConnector,ProviderCallResponse}.java
│       ├── routing/PaymentRoutingEngine.java
│       └── service/ProviderOrchestrationService.java
├── idempotency-service/
│   ├── Dockerfile
│   └── src/main/java/com/yuno/idempotency/
│       ├── config/{MDCFilter,RedisConfig}.java
│       ├── controller/IdempotencyController.java
│       ├── dto/{IdempotencyCheckResponse,IdempotencyStoreRequest}.java
│       ├── mapper/IdempotencyMapper.java
│       ├── model/IdempotencyRecord.java
│       └── service/IdempotencyService.java
├── notification-service/
│   ├── Dockerfile
│   └── src/main/java/com/yuno/notification/
│       ├── config/KafkaConfig.java
│       ├── kafka/PaymentNotificationConsumer.java
│       ├── mapper/NotificationMapper.java
│       ├── model/NotificationLog.java
│       └── service/NotificationService.java
├── docker-compose.yml
└── scripts/init-databases.sql
```

---

## Running Locally

### Prerequisites
- Docker 24+ and Docker Compose v2
- Java 17 (for local Maven builds only)
- Maven 3.9+ (for local builds)

### Start Everything with Docker Compose

```bash
# From the project root (yuno-payment-ms/)
docker compose up --build -d
```

Services start in dependency order:
1. postgres, redis, zookeeper
2. kafka, kafka-ui
3. idempotency-service
4. payment-service, provider-service
5. notification-service
6. api-gateway

Wait ~60 seconds for all health checks to pass, then:

```bash
docker compose ps  # verify all services are healthy
```

### Kafka UI
Browse topics and messages at http://localhost:9090

### Infrastructure
| Service    | URL                      | Credentials           |
|------------|--------------------------|-----------------------|
| PostgreSQL | localhost:5432           | yuno / yuno_secret    |
| Redis      | localhost:6379           | (no auth)             |
| Kafka      | localhost:9092           | (plaintext)           |
| Kafka UI   | http://localhost:9090    | —                     |

---

## API Reference

All requests go through the gateway at `http://localhost:8080`.

### Create Payment

```bash
curl -X POST http://localhost:8080/api/payments \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: unique-key-001" \
  -H "X-User-Id: merchant-42" \
  -d '{
    "paymentMethod": "CARD",
    "amount": 99.99,
    "currency": "USD",
    "description": "Order #1234"
  }'
```

**Response 202 Accepted:**
```json
{
  "success": true,
  "data": {
    "paymentId": "550e8400-e29b-41d4-a716-446655440000",
    "status": "PENDING",
    "paymentMethod": "CARD",
    "amount": 99.99,
    "currency": "USD"
  },
  "message": "Payment queued. Poll GET /payments/{id} for status."
}
```

### Poll Payment Status

```bash
curl http://localhost:8080/api/payments/550e8400-e29b-41d4-a716-446655440000 \
  -H "X-User-Id: merchant-42"
```

**Response 200 OK (after processing):**
```json
{
  "success": true,
  "data": {
    "paymentId": "550e8400-e29b-41d4-a716-446655440000",
    "status": "SUCCESS",
    "providerUsed": "PROVIDER_A",
    "providerTransactionId": "PA-A1B2C3D4E5F6G7H8",
    "retryCount": 1,
    "failoverUsed": false
  }
}
```

### List Payments by Status

```bash
curl "http://localhost:8080/api/payments?status=SUCCESS"
curl "http://localhost:8080/api/payments?status=FAILED"
curl "http://localhost:8080/api/payments?status=PENDING"
```

### Payment Methods
- `CARD` — routes to ProviderA (failover: ProviderB)
- `UPI` — routes to ProviderB (failover: ProviderA)

### Currencies
ISO 4217 3-letter uppercase codes (e.g. `USD`, `EUR`, `INR`, `GBP`)

---

## Testing

```bash
# Run all tests (from project root)
mvn test

# Run tests for a specific module
mvn test -pl payment-service
mvn test -pl provider-service
mvn test -pl idempotency-service
mvn test -pl notification-service
```

### Test Coverage

| Module                | Test Classes                                                                 |
|-----------------------|------------------------------------------------------------------------------|
| payment-service       | PaymentControllerTest, PaymentServiceTest, PaymentResultConsumerTest         |
| provider-service      | ProviderOrchestrationServiceTest, ProviderConnectorTest, PaymentRoutingEngineTest |
| idempotency-service   | IdempotencyServiceTest, IdempotencyControllerTest                            |
| notification-service  | NotificationServiceTest, PaymentNotificationConsumerTest                     |

---

## Opening in IntelliJ IDEA

1. File → Open → select `yuno-payment-ms/pom.xml`
2. Choose **"Open as Project"**
3. IntelliJ auto-detects all 6 modules from the parent POM `<modules>` section
4. Maven sync completes — all modules appear in the Project tree

All modules share the parent's dependency management (Spring Boot BOM, Spring Cloud BOM, MyBatis versions).

---

## Design Decisions

| Decision | Rationale |
|----------|-----------|
| **MyBatis over JPA/Hibernate** | Full SQL control, no dirty tracking overhead, no N+1 surprises, DBA-readable queries |
| **Feign (sync) for idempotency checks** | The answer "is this a duplicate?" must be known before processing; async patterns (Kafka request-reply) add unnecessary complexity |
| **Kafka for payment events** | Decouples services, enables retry/DLQ, provides natural audit log |
| **Two-tier idempotency (Redis + DB)** | Redis provides sub-ms speed; DB survives Redis restart — both required for a payments system |
| **MDC in commons module** | Single source of truth for key names; identical pattern across all 5 services; log aggregation with a single field query |
| **Database-per-service** | True microservice data isolation; each service owns its schema, migration lifecycle, and connection pool |
| **@Retryable + @Recover** | Clean declarative retry without manual loop logic; Spring AOP handles backoff; @Recover gives type-safe exhaustion handling |

---

## Kafka Topics

| Topic                    | Producer            | Consumer             | Purpose                          |
|--------------------------|---------------------|----------------------|----------------------------------|
| `payment.initiated`      | payment-service     | provider-service     | Trigger provider routing         |
| `payment.result`         | provider-service    | payment-service      | Update payment status            |
| `payment.notification`   | payment-service     | notification-service | Trigger user notification        |
| `payment.initiated.DLT`  | DLQ recoverer       | (manual review)      | Failed payment.initiated events  |
| `payment.result.DLT`     | DLQ recoverer       | (manual review)      | Failed payment.result events     |
| `payment.notification.DLT` | DLQ recoverer     | (manual review)      | Failed notification events       |

Dead-letter topics use exponential backoff (500ms → 1s → 2s, max 3 attempts) before routing to DLT.

---

## PostgreSQL Databases

| Database                 | Owner Service         | Key Tables                            |
|--------------------------|-----------------------|---------------------------------------|
| `yuno_payment_db`        | payment-service       | `payments`                            |
| `yuno_provider_db`       | provider-service      | `provider_calls`                      |
| `yuno_idempotency_db`    | idempotency-service   | `idempotency_records`                 |
| `yuno_notification_db`   | notification-service  | `notification_logs`                   |

Schemas managed by Flyway migrations (`V1__create_*.sql` in each service's `db/migration/`).

---

## Bugs Fixed (v2.0.0 → current)

| Bug | Fix |
|-----|-----|
| `userId` field populated from `MDCUtil.getTransactionId()` — wrong MDC key | Changed to `MDC.get(MDCKeys.USER_ID)` |
| `IdempotencyFeignClient.check()` declared return type `IdempotencyCheckResponse` — missing `ApiResponse<>` wrapper caused Jackson to map `{success,data,timestamp}` to `{found,responseBody,httpStatus}`, leaving `found=false` on every call (idempotency always missed) | Fixed return type to `ApiResponse<IdempotencyCheckResponse>`; updated `PaymentService.checkIdempotency()` to unwrap `.getData()` |
| Java label `assertThrows:` artifact in `PaymentResultConsumerTest` + dead `private void assertThrows()` stub | Cleaned up to proper try/catch assertion |
| `idempotency-service` missing `spring-boot-starter-validation` despite `@Valid` on controller | Added to pom.xml |
| Missing `MDCFilter` in `idempotency-service` — Feign calls from payment-service carried correlation headers but idempotency logs had no MDC context | Added `MDCFilter` component |
| Missing `KeyResolver` bean in `api-gateway` — `RequestRateLimiter` filter throws `NoSuchBeanDefinitionException` at startup without it | Added `GatewayRateLimiterConfig` with user-keyed resolver |
| Missing Dockerfiles for `api-gateway`, `provider-service`, `idempotency-service`, `notification-service` | Created all 4 Dockerfiles with multi-stage build + non-root user |
| `notification-service` pom missing `mybatis-spring-boot-starter-test` | Added to test scope |
