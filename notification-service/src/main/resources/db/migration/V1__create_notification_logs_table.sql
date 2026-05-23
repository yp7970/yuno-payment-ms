-- V1__create_notification_logs_table.sql
CREATE TABLE IF NOT EXISTS notification_logs (
    id                       BIGSERIAL    PRIMARY KEY,
    payment_id               VARCHAR(36)  NOT NULL,
    user_id                  VARCHAR(128),
    payment_status           VARCHAR(16)  NOT NULL,
    payment_method           VARCHAR(10)  NOT NULL,
    amount                   NUMERIC(19,4),
    currency                 CHAR(3),
    provider_transaction_id  VARCHAR(256),
    notification_type        VARCHAR(32)  NOT NULL,
    delivery_status          VARCHAR(16)  NOT NULL DEFAULT 'DELIVERED',
    correlation_id           VARCHAR(128),
    created_at               TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_notif_payment_id  ON notification_logs(payment_id);
CREATE INDEX idx_notif_user_id     ON notification_logs(user_id);
CREATE INDEX idx_notif_created_at  ON notification_logs(created_at DESC);

COMMENT ON TABLE notification_logs IS 'Audit log of all payment notifications dispatched — owned by notification-service';
