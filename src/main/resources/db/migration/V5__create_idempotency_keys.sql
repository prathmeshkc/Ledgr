CREATE TABLE idempotency_keys (
    key         VARCHAR(255) PRIMARY KEY,
    status      VARCHAR(20)  NOT NULL,
    response    JSONB,
    created_at  TIMESTAMP    NOT NULL DEFAULT now(),
    expires_at  TIMESTAMP    NOT NULL
);