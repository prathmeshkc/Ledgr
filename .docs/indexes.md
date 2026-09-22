# Database Indexes

## payments

| Index | Column(s) | Type | Why |
|-------|-----------|------|-----|
| `idx_payments_idempotency_key` | `idempotency_key` | Unique | Enforces one payment per idempotency key. Enables fast lookup on retries — the idempotency layer queries this on every incoming request. |

## ledger_entries

| Index | Column(s) | Type | Why |
|-------|-----------|------|-----|
| `idx_ledger_entries_account_id` | `account_id` | B-tree | Balance derivation queries `SUM` over all entries for an account. Without this index, every balance check is a full table scan. |
| `idx_ledger_entries_payment_id` | `payment_id` | B-tree | Enables fast lookup of all ledger entries for a given payment (e.g., "show me the debits and credits for this payment"). |

## outbox_events

| Index | Column(s) | Type | Why |
|-------|-----------|------|-----|
| `idx_outbox_unpublished` | `created_at` | Partial (`WHERE published_at IS NULL`) | The outbox poller only queries unpublished events. A partial index keeps the index small — as events get published, they drop out of the index automatically. Orders by `created_at` so events are published in sequence. |