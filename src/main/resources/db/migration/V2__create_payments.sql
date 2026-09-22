CREATE TABLE payments (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    idempotency_key         VARCHAR(255) NOT NULL,
    source_account_id       UUID         NOT NULL REFERENCES accounts(id),
    destination_account_id  UUID         NOT NULL REFERENCES accounts(id),
    amount                  NUMERIC(19,4) NOT NULL,
    currency                VARCHAR(3)   NOT NULL DEFAULT 'USD',
    status                  VARCHAR(50)  NOT NULL,
    created_at              TIMESTAMP    NOT NULL DEFAULT now(),
    updated_at              TIMESTAMP    NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX idx_payments_idempotency_key ON payments(idempotency_key);