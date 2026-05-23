-- V1__create_idempotency_table.sql
-- Durable fallback store for idempotency responses.
-- Redis is the fast path; this table survives Redis restarts and evictions.

CREATE TABLE IF NOT EXISTS idempotency_records (
    id                BIGSERIAL    PRIMARY KEY,
    idempotency_key   VARCHAR(128) NOT NULL UNIQUE,
    response_body     TEXT         NOT NULL,
    http_status       INT          NOT NULL,
    expires_at        TIMESTAMP    NOT NULL,
    created_at        TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_idem_key        ON idempotency_records(idempotency_key);
CREATE INDEX idx_idem_expires_at ON idempotency_records(expires_at);

COMMENT ON TABLE  idempotency_records              IS 'Durable idempotency store — DB fallback when Redis is unavailable';
COMMENT ON COLUMN idempotency_records.response_body IS 'Serialised JSON PaymentResponse — replayed on duplicate requests';
COMMENT ON COLUMN idempotency_records.expires_at    IS '24h TTL — matches Redis policy; cleaned up by scheduled task';
