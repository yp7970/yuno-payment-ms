-- init-databases.sql
-- Runs once on first PostgreSQL container startup.
-- Creates one database per microservice (database-per-service pattern).
-- The POSTGRES_DB env var already creates yuno_payment_db,
-- so we only need to create the remaining three.

CREATE DATABASE yuno_provider_db;
CREATE DATABASE yuno_idempotency_db;
CREATE DATABASE yuno_notification_db;

-- Grant all privileges to the yuno user on all databases
GRANT ALL PRIVILEGES ON DATABASE yuno_payment_db     TO yuno;
GRANT ALL PRIVILEGES ON DATABASE yuno_provider_db    TO yuno;
GRANT ALL PRIVILEGES ON DATABASE yuno_idempotency_db TO yuno;
GRANT ALL PRIVILEGES ON DATABASE yuno_notification_db TO yuno;
