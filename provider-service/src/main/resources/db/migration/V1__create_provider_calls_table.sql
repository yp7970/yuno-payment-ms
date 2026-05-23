-- V1__create_provider_calls_table.sql
-- provider-service owns this table — database-per-service pattern.
-- One row per provider invocation outcome (final result, not per retry attempt).
-- Supports analytics: retry rates, provider success rates, latency SLAs.

CREATE TABLE IF NOT EXISTS provider_calls (
    id                       UUID          PRIMARY KEY,
    payment_id               UUID          NOT NULL,
    provider_type            VARCHAR(16)   NOT NULL,
    payment_method           VARCHAR(10)   NOT NULL,
    amount                   NUMERIC(19,4) NOT NULL,
    currency                 CHAR(3)       NOT NULL,
    success                  BOOLEAN       NOT NULL,
    provider_transaction_id  VARCHAR(256),
    error_message            VARCHAR(512),
    retry_count              INT           NOT NULL DEFAULT 0,
    failover_used            BOOLEAN       NOT NULL DEFAULT FALSE,
    processing_time_ms       BIGINT,
    correlation_id           VARCHAR(128),
    created_at               TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_provider_calls_payment_id    ON provider_calls(payment_id);
CREATE INDEX idx_provider_calls_provider_type ON provider_calls(provider_type);
CREATE INDEX idx_provider_calls_success       ON provider_calls(success);
CREATE INDEX idx_provider_calls_created_at    ON provider_calls(created_at DESC);

COMMENT ON TABLE  provider_calls                     IS 'Audit trail of all provider call outcomes — owned by provider-service';
COMMENT ON COLUMN provider_calls.retry_count         IS 'Total attempts made including retries across primary and failover';
COMMENT ON COLUMN provider_calls.failover_used       IS 'True when primary provider exhausted retries';
COMMENT ON COLUMN provider_calls.processing_time_ms  IS 'Actual provider call duration — use for SLA monitoring';
COMMENT ON COLUMN provider_calls.correlation_id      IS 'Ties this call to the originating HTTP request via X-Correlation-Id';
