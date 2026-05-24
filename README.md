# Yuno Payment Orchestration — Microservices (v2.0.0)

A production-grade payment orchestration system built with **Java 17**, **Spring Boot 3.2.5**, and a microservices architecture. Implements full payment routing, idempotency, retry/failover, MDC-based distributed tracing, and async event-driven processing via Kafka.

-----

## Table of Contents

1. [Architecture Overview](#architecture-overview)
1. [Functional Requirements](#functional-requirements)
1. [Non-Functional Requirements](#non-functional-requirements)
1. [Microservices](#microservices)
1. [Technology Stack](#technology-stack)
1. [Key Features](#key-features)
1. [Module Structure](#module-structure)
1. [Running Locally](#running-locally)
1. [API Reference](#api-reference)
1. [Test Case Documentation](#test-case-documentation)
1. [Performance Considerations](#performance-considerations)
1. [Design Decisions](#design-decisions)
1. [Kafka Topics](#kafka-topics)
1. [PostgreSQL Databases](#postgresql-databases)
1. [Prompts Used During Development](#prompts-used-during-development)
1. [Bugs Fixed](#bugs-fixed-v200--current)

-----

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

-----

## Functional Requirements

|ID   |Requirement                                                                                                          |Implemented|
|-----|---------------------------------------------------------------------------------------------------------------------|-----------|
|FR-01|**Create Payment API** — Accept `POST /api/payments` with method, amount, currency, description                      |✅          |
|FR-02|**Fetch Payment API** — Retrieve payment by ID via `GET /api/payments/{id}`                                          |✅          |
|FR-03|**List Payments API** — Filter payments by status via `GET /api/payments?status=`                                    |✅          |
|FR-04|**Payment Routing** — CARD requests route to ProviderA; UPI requests route to ProviderB                              |✅          |
|FR-05|**Retry** — Each provider is retried up to 3 times on failure before failover                                        |✅          |
|FR-06|**Failover** — On primary provider exhaustion, automatically switch to secondary provider                            |✅          |
|FR-07|**Idempotency** — Duplicate requests with the same `Idempotency-Key` return the stored response without re-processing|✅          |
|FR-08|**Payment Status Tracking** — Payment transitions through `PENDING → SUCCESS / FAILED`; status queryable at any point|✅          |
|FR-09|**Audit Logging** — Every provider call, notification delivery, and payment status change is persisted               |✅          |
|FR-10|**Dead-Letter Queue** — Failed Kafka events are routed to DLT topics after exhausting retries                        |✅          |

-----

## Non-Functional Requirements

|ID    |Requirement                 |Target                    |Approach                                                                     |
|------|----------------------------|--------------------------|-----------------------------------------------------------------------------|
|NFR-01|**Availability**            |99.9% uptime              |Retry + failover across two providers                                        |
|NFR-02|**Idempotency durability**  |Survive Redis restart     |Two-tier: Redis (fast path) + PostgreSQL (fallback)                          |
|NFR-03|**Throughput**              |100 req/s per user        |Redis token-bucket rate limiter at gateway                                   |
|NFR-04|**Latency — Create Payment**|< 200ms p99 (202 accepted)|Async: payment saved PENDING, Kafka publish, respond immediately             |
|NFR-05|**Latency — Fetch Payment** |< 50ms p99                |Direct MyBatis DB query, no joins                                            |
|NFR-06|**Observability**           |Full trace per payment    |MDC: `trxId`, `corrId`, `userId`, `svc` on every log line                    |
|NFR-07|**Security**                |Input validation          |`@Valid` on all request bodies; `@NotBlank`, `@Positive`, `@Size` constraints|
|NFR-08|**Data isolation**          |Service-level DB isolation|One PostgreSQL database per microservice                                     |
|NFR-09|**Portability**             |Run anywhere with Docker  |Full Docker Compose stack; multi-stage Dockerfiles with non-root user        |
|NFR-10|**Schema evolution**        |Zero-downtime migrations  |Flyway versioned migrations per service                                      |

-----

## Microservices

|Service               |Port|Description                                                       |
|----------------------|----|------------------------------------------------------------------|
|`api-gateway`         |8080|Spring Cloud Gateway — routing, MDC injection, Redis rate limiting|
|`payment-service`     |8081|Core payment CRUD, Kafka producer/consumer, idempotency checks    |
|`provider-service`    |8082|Provider routing, retry/failover, audit logging                   |
|`idempotency-service` |8083|Redis + PostgreSQL idempotency guard                              |
|`notification-service`|8084|Payment outcome notifications, audit log                          |
|`commons`             |—   |Shared DTOs, Kafka events, MDC utilities, enums                   |

-----

## Technology Stack

|Concern         |Technology                                   |
|----------------|---------------------------------------------|
|Language        |Java 17                                      |
|Framework       |Spring Boot 3.2.5                            |
|API Gateway     |Spring Cloud Gateway 2023.0.1                |
|HTTP Client     |OpenFeign (payment → idempotency sync calls) |
|Database ORM    |MyBatis 3.0.3 (XML mappers, no JPA/Hibernate)|
|Database        |PostgreSQL 16 (4 databases, one per service) |
|Cache           |Redis 7 (idempotency TTL, gateway rate limit)|
|Messaging       |Apache Kafka (Confluent 7.6.0)               |
|Migrations      |Flyway                                       |
|Retry           |Spring Retry (`@Retryable` + `@Recover`)     |
|Testing         |JUnit 5, Mockito, MockMvc, AssertJ           |
|Containerisation|Docker + Docker Compose                      |

-----

## Key Features

### Payment Flow (Async)

1. `POST /api/payments` → gateway → payment-service (202 Accepted immediately)
1. payment-service saves `PENDING`, publishes `PaymentInitiatedEvent` to Kafka
1. provider-service routes by method, calls provider, retries up to 3×
1. On primary failure → failover to secondary provider (also 3 retries)
1. provider-service publishes `PaymentResultEvent` → payment-service updates status
1. payment-service stores idempotency record + publishes `PaymentNotificationEvent`
1. notification-service logs delivery record via MyBatis
1. Client polls `GET /api/payments/{id}` for final status

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

Propagation path: HTTP headers → MDCFilter → service logs → Kafka headers → consumer `MDCUtil.populateFromConsumerRecord()`

A single log query `transactionId:"abc-123"` reconstructs the complete payment journey across all 5 services.

### Provider Routing Table

|Payment Method|Primary   |Failover  |
|--------------|----------|----------|
|CARD          |PROVIDER_A|PROVIDER_B|
|UPI           |PROVIDER_B|PROVIDER_A|

### Rate Limiting

- Spring Cloud Gateway with Redis token bucket
- 100 req/s per user (X-User-Id), burst capacity 200
- Anonymous requests bucketed together

-----

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
│       ├── config/GatewayRateLimiterConfig.java
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

-----

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
1. kafka, kafka-ui
1. idempotency-service
1. payment-service, provider-service
1. notification-service
1. api-gateway

Wait ~60 seconds for all health checks to pass, then:

```bash
docker compose ps  # verify all services are healthy
```

### Kafka UI

Browse topics and messages at <http://localhost:9090>

### Infrastructure

|Service   |URL                    |Credentials       |
|----------|-----------------------|------------------|
|PostgreSQL|localhost:5432         |yuno / yuno_secret|
|Redis     |localhost:6379         |(no auth)         |
|Kafka     |localhost:9092         |(plaintext)       |
|Kafka UI  |<http://localhost:9090>|—                 |

### Opening in IntelliJ IDEA

1. File → Open → select `yuno-payment-ms/pom.xml`
1. Choose **“Open as Project”**
1. IntelliJ auto-detects all 6 modules from the parent POM `<modules>` section
1. Maven sync completes — all modules appear in the Project tree

-----

## API Reference

All requests go through the gateway at `http://localhost:8080`.

### Integration Points

|Endpoint               |Method|Request Headers                                                 |Request Body             |Response                                   |
|-----------------------|------|----------------------------------------------------------------|-------------------------|-------------------------------------------|
|`/api/payments`        |POST  |`Idempotency-Key`, `X-User-Id`, `Content-Type: application/json`|`CreatePaymentRequest`   |`202 ApiResponse<PaymentResponse>`         |
|`/api/payments/{id}`   |GET   |`X-User-Id`                                                     |—                        |`200 ApiResponse<PaymentResponse>`         |
|`/api/payments?status=`|GET   |`X-User-Id`                                                     |—                        |`200 ApiResponse<List<PaymentResponse>>`   |
|`/idempotency/check`   |GET   |—                                                               |`?key=<idempotency-key>` |`200 ApiResponse<IdempotencyCheckResponse>`|
|`/idempotency/store`   |POST  |—                                                               |`IdempotencyStoreRequest`|`200 ApiResponse<Void>`                    |

### Input Parameters — CreatePaymentRequest

|Field          |Type        |Constraints                             |Description                |
|---------------|------------|----------------------------------------|---------------------------|
|`paymentMethod`|`String`    |Required. One of: `CARD`, `UPI`         |Payment method to use      |
|`amount`       |`BigDecimal`|Required. `> 0`                         |Payment amount             |
|`currency`     |`String`    |Required. 3-letter ISO 4217 (e.g. `USD`)|Currency code              |
|`description`  |`String`    |Optional. Max 255 chars                 |Free-text order description|

### Output Parameters — PaymentResponse

|Field                  |Type        |Description                                   |
|-----------------------|------------|----------------------------------------------|
|`paymentId`            |`UUID`      |Unique payment identifier                     |
|`status`               |`String`    |One of: `PENDING`, `SUCCESS`, `FAILED`        |
|`paymentMethod`        |`String`    |Method used                                   |
|`amount`               |`BigDecimal`|Payment amount                                |
|`currency`             |`String`    |Currency code                                 |
|`providerUsed`         |`String`    |Which provider processed it (after completion)|
|`providerTransactionId`|`String`    |Provider’s own transaction reference          |
|`retryCount`           |`int`       |Number of retry attempts made                 |
|`failoverUsed`         |`boolean`   |Whether failover provider was invoked         |
|`createdAt`            |`Instant`   |Creation timestamp                            |
|`updatedAt`            |`Instant`   |Last status update timestamp                  |

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

-----

## Test Case Documentation

### Running Tests

```bash
# Run all tests (from project root)
mvn test

# Run tests for a specific module
mvn test -pl payment-service
mvn test -pl provider-service
mvn test -pl idempotency-service
mvn test -pl notification-service
```

### Test Classes by Module

|Module              |Test Classes                                                                           |
|--------------------|---------------------------------------------------------------------------------------|
|payment-service     |`PaymentControllerTest`, `PaymentServiceTest`, `PaymentResultConsumerTest`             |
|provider-service    |`ProviderOrchestrationServiceTest`, `ProviderConnectorTest`, `PaymentRoutingEngineTest`|
|idempotency-service |`IdempotencyServiceTest`, `IdempotencyControllerTest`                                  |
|notification-service|`NotificationServiceTest`, `PaymentNotificationConsumerTest`                           |

-----

### Sanity Test Cases

> Core happy-path scenarios. Run after every deployment to verify the system is alive.

|TC-S01      |Create CARD payment — happy path                                                                        |
|------------|--------------------------------------------------------------------------------------------------------|
|**Input**   |`POST /api/payments` with `paymentMethod=CARD`, `amount=100.00`, `currency=USD`, valid `Idempotency-Key`|
|**Expected**|HTTP 202; `status=PENDING`; paymentId returned                                                          |
|**Class**   |`PaymentControllerTest.testCreatePayment_Card_Returns202`                                               |

|TC-S02      |Create UPI payment — happy path                                              |
|------------|-----------------------------------------------------------------------------|
|**Input**   |`POST /api/payments` with `paymentMethod=UPI`, `amount=50.00`, `currency=INR`|
|**Expected**|HTTP 202; `status=PENDING`                                                   |
|**Class**   |`PaymentControllerTest.testCreatePayment_UPI_Returns202`                     |

|TC-S03      |Fetch payment by ID — existing payment      |
|------------|--------------------------------------------|
|**Input**   |`GET /api/payments/{validUUID}`             |
|**Expected**|HTTP 200; correct payment fields returned   |
|**Class**   |`PaymentControllerTest.testGetPayment_Found`|

|TC-S04      |CARD routes to ProviderA                            |
|------------|----------------------------------------------------|
|**Input**   |`PaymentInitiatedEvent` with `paymentMethod=CARD`   |
|**Expected**|ProviderA connector is invoked first                |
|**Class**   |`PaymentRoutingEngineTest.testCardRoutesToProviderA`|

|TC-S05      |UPI routes to ProviderB                            |
|------------|---------------------------------------------------|
|**Input**   |`PaymentInitiatedEvent` with `paymentMethod=UPI`   |
|**Expected**|ProviderB connector is invoked first               |
|**Class**   |`PaymentRoutingEngineTest.testUpiRoutesToProviderB`|

-----

### Regression Test Cases

> Covers previously identified bugs and critical business rules. Run before every release.

|TC-R01      |Idempotency — duplicate request returns cached response                         |
|------------|--------------------------------------------------------------------------------|
|**Input**   |Two identical `POST /api/payments` with same `Idempotency-Key`                  |
|**Expected**|Second request returns the same `paymentId` from store; no new DB record created|
|**Class**   |`IdempotencyServiceTest.testDuplicateRequestReturnsCachedResponse`              |

|TC-R02      |Idempotency Redis miss — PostgreSQL fallback          |
|------------|------------------------------------------------------|
|**Input**   |Key absent from Redis; present in PostgreSQL          |
|**Expected**|Response returned from DB; Redis backfilled           |
|**Class**   |`IdempotencyServiceTest.testRedisMissPostgresFallback`|

|TC-R03      |ProviderA fails all retries — failover to ProviderB           |
|------------|--------------------------------------------------------------|
|**Input**   |CARD payment; ProviderA throws exception on all 3 attempts    |
|**Expected**|ProviderB is invoked; `failoverUsed=true` in result           |
|**Class**   |`ProviderOrchestrationServiceTest.testCardFailoverToProviderB`|

|TC-R04      |ProviderB fails all retries — failover to ProviderA          |
|------------|-------------------------------------------------------------|
|**Input**   |UPI payment; ProviderB throws exception on all 3 attempts    |
|**Expected**|ProviderA is invoked; `failoverUsed=true` in result          |
|**Class**   |`ProviderOrchestrationServiceTest.testUpiFailoverToProviderA`|

|TC-R05      |Payment status transitions PENDING → SUCCESS                          |
|------------|----------------------------------------------------------------------|
|**Input**   |`PaymentResultEvent` with `status=SUCCESS` consumed by payment-service|
|**Expected**|DB record updated to `SUCCESS`; notification event published          |
|**Class**   |`PaymentResultConsumerTest.testStatusUpdatedToSuccess`                |

|TC-R06      |Payment status transitions PENDING → FAILED          |
|------------|-----------------------------------------------------|
|**Input**   |`PaymentResultEvent` with `status=FAILED`            |
|**Expected**|DB record updated to `FAILED`                        |
|**Class**   |`PaymentResultConsumerTest.testStatusUpdatedToFailed`|

|TC-R07      |MDC correlation headers propagated through Kafka                |
|------------|----------------------------------------------------------------|
|**Input**   |Payment event published with `trxId`, `corrId`, `userId` headers|
|**Expected**|Consumer extracts and populates MDC correctly                   |
|**Class**   |`PaymentInitiatedConsumerTest.testMdcPopulatedFromKafkaHeaders` |

-----

### Integration Test Cases

> Validates cross-service interactions and full payment lifecycle. Run in staging environment.

|TC-I01      |Full CARD payment lifecycle (success)                                       |
|------------|----------------------------------------------------------------------------|
|**Input**   |`POST /api/payments` (CARD); wait for async processing                      |
|**Expected**|`GET /api/payments/{id}` returns `status=SUCCESS`, `providerUsed=PROVIDER_A`|
|**Class**   |`PaymentControllerTest.testFullCardPaymentLifecycle`                        |

|TC-I02      |Full UPI payment lifecycle (success)                                        |
|------------|----------------------------------------------------------------------------|
|**Input**   |`POST /api/payments` (UPI)                                                  |
|**Expected**|`GET /api/payments/{id}` returns `status=SUCCESS`, `providerUsed=PROVIDER_B`|
|**Class**   |`PaymentControllerTest.testFullUpiPaymentLifecycle`                         |

|TC-I03      |Notification delivery on payment success                                         |
|------------|---------------------------------------------------------------------------------|
|**Input**   |Payment completes successfully                                                   |
|**Expected**|`notification_logs` table contains record with `paymentId` and `DELIVERED` status|
|**Class**   |`NotificationServiceTest.testNotificationLoggedOnSuccess`                        |

|TC-I04      |Rate limiting enforced at gateway                      |
|------------|-------------------------------------------------------|
|**Input**   |> 200 requests within 1 second from same `X-User-Id`   |
|**Expected**|HTTP 429 returned once burst capacity exceeded         |
|**Class**   |`GatewayRateLimitIntegrationTest.testRateLimitExceeded`|

-----

### Negative Test Cases

|TC-N01      |Missing required field — paymentMethod                                   |
|------------|-------------------------------------------------------------------------|
|**Input**   |`POST /api/payments` body omits `paymentMethod`                          |
|**Expected**|HTTP 400; validation error message                                       |
|**Class**   |`PaymentControllerTest.testCreatePayment_MissingPaymentMethod_Returns400`|

|TC-N02      |Invalid amount — zero                                          |
|------------|---------------------------------------------------------------|
|**Input**   |`amount=0`                                                     |
|**Expected**|HTTP 400; `amount must be greater than 0`                      |
|**Class**   |`PaymentControllerTest.testCreatePayment_ZeroAmount_Returns400`|

|TC-N03      |Invalid amount — negative                                          |
|------------|-------------------------------------------------------------------|
|**Input**   |`amount=-50.00`                                                    |
|**Expected**|HTTP 400                                                           |
|**Class**   |`PaymentControllerTest.testCreatePayment_NegativeAmount_Returns400`|

|TC-N04      |Invalid paymentMethod — unsupported value                         |
|------------|------------------------------------------------------------------|
|**Input**   |`paymentMethod=CRYPTO`                                            |
|**Expected**|HTTP 400; deserialization/validation error                        |
|**Class**   |`PaymentControllerTest.testCreatePayment_InvalidMethod_Returns400`|

|TC-N05      |Fetch non-existent payment                                |
|------------|----------------------------------------------------------|
|**Input**   |`GET /api/payments/00000000-0000-0000-0000-000000000000`  |
|**Expected**|HTTP 404; error message `Payment not found`               |
|**Class**   |`PaymentControllerTest.testGetPayment_NotFound_Returns404`|

|TC-N06      |Both providers fail all retries — payment marked FAILED                   |
|------------|--------------------------------------------------------------------------|
|**Input**   |CARD payment; both ProviderA and ProviderB throw exceptions on all retries|
|**Expected**|Payment status updated to `FAILED`; DLT event published                   |
|**Class**   |`ProviderOrchestrationServiceTest.testBothProvidersFailMarksFailed`       |

|TC-N07      |Duplicate Idempotency-Key with different body                    |
|------------|-----------------------------------------------------------------|
|**Input**   |Same `Idempotency-Key`; different `amount` in second request     |
|**Expected**|Original cached response returned (amount from first request)    |
|**Class**   |`IdempotencyServiceTest.testSameKeyDifferentBody_ReturnsOriginal`|

|TC-N08      |Missing Idempotency-Key header                                            |
|------------|--------------------------------------------------------------------------|
|**Input**   |`POST /api/payments` without `Idempotency-Key` header                     |
|**Expected**|HTTP 400; `Idempotency-Key header is required`                            |
|**Class**   |`PaymentControllerTest.testCreatePayment_MissingIdempotencyKey_Returns400`|

|TC-N09      |Invalid currency code                                               |
|------------|--------------------------------------------------------------------|
|**Input**   |`currency=XYZ123` (> 3 chars)                                       |
|**Expected**|HTTP 400; validation error                                          |
|**Class**   |`PaymentControllerTest.testCreatePayment_InvalidCurrency_Returns400`|

|TC-N10      |Kafka consumer receives malformed event                       |
|------------|--------------------------------------------------------------|
|**Input**   |Malformed JSON on `payment.initiated` topic                   |
|**Expected**|Deserialization error handled; message routed to DLT; no crash|
|**Class**   |`PaymentInitiatedConsumerTest.testMalformedEvent_RoutedToDLT` |

-----

## Performance Considerations

### Latency Targets

|Operation                      |Target p50|Target p99|Mechanism                                                              |
|-------------------------------|----------|----------|-----------------------------------------------------------------------|
|Create Payment (202 accepted)  |< 30ms    |< 200ms   |Async: DB write + Kafka publish only; no provider call in critical path|
|Fetch Payment status           |< 10ms    |< 50ms    |Single indexed `SELECT` by UUID primary key                            |
|Idempotency check (Redis hit)  |< 2ms     |< 10ms    |Redis GET with 24h TTL                                                 |
|Idempotency check (DB fallback)|< 20ms    |< 80ms    |Indexed `SELECT` on `idempotency_key` column                           |
|Provider call (ProviderA/B)    |< 300ms   |< 1500ms  |Simulated; real PSP SLAs vary; timeout configurable                    |

### Key Performance Design Choices

**Async by design.** The create payment API returns 202 immediately after a DB write and Kafka publish. The provider call (slowest step, up to 1.5s with retries) happens completely outside the HTTP response path. This means API latency is decoupled from provider latency.

**MyBatis over JPA.** No dirty tracking overhead, no session management, no lazy-load surprises. Each query is explicit SQL — easy to add indexes and explain-plan without ORM abstraction getting in the way.

**Redis rate limiting at gateway.** Token-bucket implementation means rate limiting adds < 1ms per request (single Redis EVAL command) and protects all downstream services uniformly.

**Database-per-service.** Connection pool contention between services is eliminated. Each service has its own pool tuned for its own query patterns.

**MDC via Kafka headers (not payload).** Correlation data travels in Kafka message headers rather than the event payload — zero business logic change needed to add tracing, and consumers can discard it without touching business fields.

### Suggested Metrics to Capture in Production

```
# Payment throughput
payment_created_total (counter, labels: method, currency)
payment_result_total (counter, labels: status, provider, failover_used)

# Latency histograms
payment_create_latency_seconds (histogram)
payment_fetch_latency_seconds (histogram)
idempotency_check_latency_seconds (histogram, labels: source=[redis|db])

# Provider health
provider_call_total (counter, labels: provider, attempt_number, outcome)
provider_failover_total (counter, labels: from_provider, to_provider)

# Infrastructure
kafka_consumer_lag (gauge, labels: topic, consumer_group)
redis_hit_ratio (gauge)
db_connection_pool_active (gauge, labels: service)
```

These can be exposed via `spring-boot-starter-actuator` + `micrometer-registry-prometheus` and scraped by Prometheus.

-----

## Design Decisions

|Decision                               |Rationale                                                                                                                         |
|---------------------------------------|----------------------------------------------------------------------------------------------------------------------------------|
|**MyBatis over JPA/Hibernate**         |Full SQL control, no dirty tracking overhead, no N+1 surprises, DBA-readable queries                                              |
|**Feign (sync) for idempotency checks**|The answer “is this a duplicate?” must be known before processing; async patterns (Kafka request-reply) add unnecessary complexity|
|**Kafka for payment events**           |Decouples services, enables retry/DLQ, provides natural audit log                                                                 |
|**Two-tier idempotency (Redis + DB)**  |Redis provides sub-ms speed; DB survives Redis restart — both required for a payments system                                      |
|**MDC in commons module**              |Single source of truth for key names; identical pattern across all 5 services; log aggregation with a single field query          |
|**Database-per-service**               |True microservice data isolation; each service owns its schema, migration lifecycle, and connection pool                          |
|**@Retryable + @Recover**              |Clean declarative retry without manual loop logic; Spring AOP handles backoff; @Recover gives type-safe exhaustion handling       |
|**202 Accepted (not 201 Created)**     |Payment outcome is unknown at creation time (async processing); 202 correctly signals “accepted for processing, result pending”   |

-----

## Kafka Topics

|Topic                     |Producer        |Consumer            |Purpose                        |
|--------------------------|----------------|--------------------|-------------------------------|
|`payment.initiated`       |payment-service |provider-service    |Trigger provider routing       |
|`payment.result`          |provider-service|payment-service     |Update payment status          |
|`payment.notification`    |payment-service |notification-service|Trigger user notification      |
|`payment.initiated.DLT`   |DLQ recoverer   |(manual review)     |Failed payment.initiated events|
|`payment.result.DLT`      |DLQ recoverer   |(manual review)     |Failed payment.result events   |
|`payment.notification.DLT`|DLQ recoverer   |(manual review)     |Failed notification events     |

Dead-letter topics use exponential backoff (500ms → 1s → 2s, max 3 attempts) before routing to DLT.

-----

## PostgreSQL Databases

|Database              |Owner Service       |Key Tables           |
|----------------------|--------------------|---------------------|
|`yuno_payment_db`     |payment-service     |`payments`           |
|`yuno_provider_db`    |provider-service    |`provider_calls`     |
|`yuno_idempotency_db` |idempotency-service |`idempotency_records`|
|`yuno_notification_db`|notification-service|`notification_logs`  |

Schemas managed by Flyway migrations (`V1__create_*.sql` in each service’s `db/migration/`).

-----

## Prompts Used During Development

This project was developed using AI-assisted coding (vibe coding) with Claude. Below are the key prompts used during development, demonstrating how high-quality prompts were crafted to generate production-aligned code.

-----

### 1. Initial Architecture Design

**Prompt:**

```
Design a production-grade payment orchestration system using Java 17 and Spring Boot 3.2.5 
with a microservices architecture. Requirements:
- CARD payments route to ProviderA, UPI to ProviderB
- Retry (3 attempts) + failover to secondary provider on exhaustion
- Two-tier idempotency: Redis (24h TTL) as fast path, PostgreSQL as durable fallback
- Async processing via Kafka: payment.initiated → provider-service → payment.result
- MDC distributed tracing with trxId, corrId, userId, svc across all services
- Use MyBatis (not JPA) for explicit SQL control
- Database-per-service pattern with Flyway migrations
- Spring Cloud Gateway with Redis rate limiting (100 req/s per user)
- Docker Compose for full local stack

Produce: architecture diagram, module list, Kafka topic design, and database schema per service.
```

-----

### 2. Payment Service Core

**Prompt:**

```
Implement the payment-service for the architecture above. Requirements:
- POST /api/payments returns 202 Accepted immediately (async design)
- Save payment as PENDING to PostgreSQL via MyBatis before publishing Kafka event
- Sync idempotency check via Feign before processing (return cached response if duplicate)
- Consume PaymentResultEvent from Kafka and update payment status
- Publish PaymentNotificationEvent after status update
- GlobalExceptionHandler with @ControllerAdvice: 404 for missing payment, 400 for validation
- SLF4J/Logback with MDC on every log statement (trxId, corrId, userId, svc)
- Full file, no stubs, production-ready with comments explaining each decision.
```

-----

### 3. Provider Routing + Retry/Failover

**Prompt:**

```
Implement the provider-service routing engine. Requirements:
- Consume PaymentInitiatedEvent from Kafka
- Route: CARD → ProviderA primary, ProviderB failover; UPI → ProviderB primary, ProviderA failover
- @Retryable on each provider connector: 3 attempts, exponential backoff 500ms/1s/2s
- @Recover method publishes to DLT on exhaustion; also triggers failover to secondary
- After failover exhaustion on secondary: mark payment FAILED, publish PaymentResultEvent
- Log every provider call attempt to provider_calls table via MyBatis (provider, attempt_number, outcome, duration_ms)
- propagate MDC from Kafka headers via MDCUtil.populateFromConsumerRecord()
- Full production code, no TODO stubs.
```

-----

### 4. Idempotency Service

**Prompt:**

```
Implement the idempotency-service as a standalone Spring Boot microservice. Requirements:
- GET /idempotency/check?key= → check Redis first; on miss check PostgreSQL; backfill Redis on DB hit
- POST /idempotency/store → store in both Redis (24h TTL) and PostgreSQL
- Redis: Spring Data Redis with RedisTemplate<String, String>; value is JSON-serialized response body
- PostgreSQL: MyBatis mapper with idempotency_records table (key, response_body, http_status, created_at)
- @Scheduled cleanup of expired PostgreSQL records (daily at midnight)
- MDCFilter to propagate correlation headers from Feign caller
- Full validation with @Valid, @NotBlank on request DTOs.
```

-----

### 5. Test Suite

**Prompt:**

```
Write comprehensive JUnit 5 + Mockito tests for PaymentService and PaymentController. Cover:
Positive cases:
- Create CARD payment returns 202 with PENDING status
- Create UPI payment returns 202
- Duplicate idempotency key returns cached response
- Payment result consumer updates status to SUCCESS
- Payment result consumer updates status to FAILED

Negative cases:
- Missing paymentMethod returns 400
- Zero/negative amount returns 400
- Non-existent payment ID returns 404
- Missing Idempotency-Key header returns 400
- Both providers fail all retries: payment marked FAILED

Use MockMvc for controller tests, @ExtendWith(MockitoExtension.class) for service tests.
AssertJ for assertions. Full test class, no abbreviations.
```

-----

### 6. MDC Distributed Tracing Strategy

**Prompt:**

```
Design and implement MDC (Mapped Diagnostic Context) distributed tracing across 5 Spring Boot 
microservices connected via HTTP (Feign) and Kafka. Requirements:
- Keys: trxId (paymentId), corrId (X-Correlation-Id), userId (X-User-Id), svc (service name)
- HTTP path: MDCFilter (OncePerRequestFilter) reads headers → populates MDC → clears after response
- Kafka producer path: MDCUtil reads MDC → writes to Kafka message headers
- Kafka consumer path: MDCUtil reads Kafka headers → populates MDC
- commons module owns MDCKeys constants and MDCUtil utility class
- All 5 services import commons and use identical key names
- Show: MDCKeys.java, MDCUtil.java, MDCFilter.java (generic), and Kafka producer/consumer usage example.
```

-----

## Bugs Fixed (v2.0.0 → current)

|Bug                                                                                                                                                                                                                                                             |Fix                                                                                                                             |
|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|--------------------------------------------------------------------------------------------------------------------------------|
|`userId` field populated from `MDCUtil.getTransactionId()` — wrong MDC key                                                                                                                                                                                      |Changed to `MDC.get(MDCKeys.USER_ID)`                                                                                           |
|`IdempotencyFeignClient.check()` return type `IdempotencyCheckResponse` — missing `ApiResponse<>` wrapper caused Jackson to map `{success,data,timestamp}` to `{found,responseBody,httpStatus}`, leaving `found=false` on every call (idempotency always missed)|Fixed return type to `ApiResponse<IdempotencyCheckResponse>`; updated `PaymentService.checkIdempotency()` to unwrap `.getData()`|
|Java label `assertThrows:` artifact in `PaymentResultConsumerTest` + dead `private void assertThrows()` stub                                                                                                                                                    |Cleaned up to proper try/catch assertion                                                                                        |
|`idempotency-service` missing `spring-boot-starter-validation` despite `@Valid` on controller                                                                                                                                                                   |Added to pom.xml                                                                                                                |
|Missing `MDCFilter` in `idempotency-service` — Feign calls from payment-service carried correlation headers but idempotency logs had no MDC context                                                                                                             |Added `MDCFilter` component                                                                                                     |
|Missing `KeyResolver` bean in `api-gateway` — `RequestRateLimiter` filter throws `NoSuchBeanDefinitionException` at startup without it                                                                                                                          |Added `GatewayRateLimiterConfig` with user-keyed resolver                                                                       |
|Missing Dockerfiles for `api-gateway`, `provider-service`, `idempotency-service`, `notification-service`                                                                                                                                                        |Created all 4 Dockerfiles with multi-stage build + non-root user                                                                |
|`notification-service` pom missing `mybatis-spring-boot-starter-test`                                                                                                                                                                                           |Added to test scope                                                                                                             |