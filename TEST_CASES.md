# Test Cases — Yuno Payment Orchestration System

## 1. Payment Routing

| ID | Scenario | Input | Expected Output |
|----|----------|-------|-----------------|
| TC-001 | Route CARD payment to ProviderA | `paymentMethod: CARD` | Routed to ProviderA, status `SUCCESS` |
| TC-002 | Route UPI payment to ProviderB | `paymentMethod: UPI` | Routed to ProviderB, status `SUCCESS` |
| TC-003 | Invalid payment method | `paymentMethod: UNKNOWN` | `400 Bad Request` |

## 2. Failover & Reversal

| ID | Scenario | Input | Expected Output |
|----|----------|-------|-----------------|
| TC-004 | ProviderA fails, failover triggers | ProviderA returns 500 | Payment retried, reversal initiated |
| TC-005 | Both providers fail | All providers down | `503 Service Unavailable`, reversal completed |
| TC-006 | Partial payment reversal | ProviderA partially processes | Reversal for partial amount |

## 3. Idempotency

| ID | Scenario | Input | Expected Output |
|----|----------|-------|-----------------|
| TC-007 | Duplicate request within 24h (Redis hit) | Same `idempotencyKey` | Returns cached response, no new payment |
| TC-008 | Duplicate request (Redis miss, DB hit) | Same key, Redis evicted | Returns DB response, no new payment |
| TC-009 | Same key after 24h expiry | Expired `idempotencyKey` | New payment processed |
| TC-010 | Unique idempotency key | New `idempotencyKey` | Payment processed normally |

## 4. Retry Logic

| ID | Scenario | Input | Expected Output |
|----|----------|-------|-----------------|
| TC-011 | Transient failure, retry succeeds | Provider fails once | Payment succeeds on retry |
| TC-012 | Max retries exceeded | Provider fails 3 times | `503`, exhausted retries |

## 5. API Validation

| ID | Scenario | Input | Expected Output |
|----|----------|-------|-----------------|
| TC-013 | Missing required fields | No `amount` in request | `400 Bad Request` |
| TC-014 | Negative amount | `amount: -100` | `400 Bad Request` |
| TC-015 | Valid payment request | All fields valid | `200 OK`, payment created |

## 6. MDC Distributed Tracing

| ID | Scenario | Expected Output |
|----|----------|-----------------|
| TC-016 | Payment request logs | `transactionId`, `userId`, `threadId` present in all logs |
| TC-017 | Cross-service trace | Same `transactionId` propagated across services |
