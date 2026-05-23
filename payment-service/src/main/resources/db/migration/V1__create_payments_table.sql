-- V1__create_payments_table.sql
-- payment-service owns this table. provider-service and notification-service
-- each own their own separate tables in their own databases (database-per-service pattern).

CREATE TABLE IF NOT EXISTS payments (
    id                       UUID          PRIMARY KEY,
    idempotency_key          VARCHAR(128)  NOT NULL UNIQUE,
    payment_method           VARCHAR(10)   NOT NULL,
    amount                   NUMERIC(19,4) NOT NULL,
    currency                 CHAR(3)       NOT NULL,
    description              VARCHAR(256),
    status                   VARCHAR(16)   NOT NULL DEFAULT 'PENDING',
    provider_used            VARCHAR(16),
    provider_transaction_id  VARCHAR(256),
    retry_count              INT           NOT NULL DEFAULT 0,
    failover_used            BOOLEAN       NOT NULL DEFAULT FALSE,
    failure_reason           VARCHAR(512),
    user_id                  VARCHAR(128),
    correlation_id           VARCHAR(128),
    created_at               TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at               TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_payments_idempotency_key ON payments(idempotency_key);
CREATE INDEX idx_payments_status          ON payments(status);
CREATE INDEX idx_payments_created_at      ON payments(created_at DESC);
CREATE INDEX idx_payments_user_id         ON payments(user_id);

COMMENT ON TABLE  payments                      IS 'Payment records owned by payment-service';
COMMENT ON COLUMN payments.idempotency_key      IS 'Caller-supplied key — unique per payment attempt, checked against idempotency-service';
COMMENT ON COLUMN payments.status               IS 'PENDING→PROCESSING→RETRYING→SUCCESS|FAILED';
COMMENT ON COLUMN payments.retry_count          IS 'Total provider call attempts including failover';
COMMENT ON COLUMN payments.failover_used        IS 'True when primary provider exhausted retries and failover was invoked';
COMMENT ON COLUMN payments.correlation_id       IS 'X-Correlation-Id from api-gateway — ties HTTP request to all Kafka hops';
