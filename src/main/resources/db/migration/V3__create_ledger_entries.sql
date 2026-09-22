CREATE TABLE ledger_entries (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    payment_id  UUID          NOT NULL REFERENCES payments(id),
    account_id  UUID          NOT NULL REFERENCES accounts(id),
    entry_type  VARCHAR(10)   NOT NULL,
    amount      NUMERIC(19,4) NOT NULL,
    created_at  TIMESTAMP     NOT NULL DEFAULT now()
);

CREATE INDEX idx_ledger_entries_account_id ON ledger_entries(account_id);
CREATE INDEX idx_ledger_entries_payment_id ON ledger_entries(payment_id);