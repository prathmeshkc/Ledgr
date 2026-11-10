# Phase 3 Roadmap

Improvements inspired by [PayFlow](https://github.com/atharva009/payflow) and production payment system patterns.

## 1. Payment Status History

Track every state transition as a separate record with timestamp.

- New `payment_status_history` table: `id`, `payment_id`, `from_status`, `to_status`, `created_at`
- Write a history row inside `transitionTo()` or in the service layer
- Return `statusHistory` on GET /payments/{id}
- **Why:** Audit trail for compliance — answers "when did this payment move to AUTHORIZED?" without parsing logs

## 2. `balance_after` on Ledger Entries

Store the running account balance at the time each entry was written.

- Add `balance_after NUMERIC(19,4)` column to `ledger_entries`
- Compute it during `writeEntries()` as current balance + entry effect
- **Why:** Reconstruct historical balances without re-summing from genesis. Useful for statements, debugging, and reconciliation

## 3. Request Fingerprinting on Idempotency

Detect when a client reuses an idempotency key with different request parameters.

- SHA-256 hash of the request body, stored alongside the idempotency record
- On retry, compare hashes — if mismatch, return 409 with `IDEMPOTENCY_CONFLICT`
- Prevents accidental key reuse (e.g., client generates the same UUID for two different payments)
- **Why:** Stripe, PayPal, and Adyen all do this. Without it, a reused key silently returns a stale cached response

## 4. `SKIP LOCKED` for Outbox Poller

Allow multiple poller instances to run concurrently without blocking each other.

- Change outbox query from `SELECT ... FOR UPDATE` to `SELECT ... FOR UPDATE SKIP LOCKED`
- Each poller instance grabs a different set of unpublished events
- **Why:** Required for horizontal scaling. Without it, only one poller instance does useful work at a time

## 5. MDC Trace ID Filter

Add a correlation ID to every request for log tracing.

- Servlet filter that reads `X-Trace-Id` header (or generates a UUID) and puts it in SLF4J MDC
- Include trace ID in all log output via logback pattern
- Return it in response headers so the client can reference it in support tickets
- **Why:** Without it, correlating logs across a request lifecycle requires timestamp guessing

## 6. Circuit Breaker on Kafka Publisher (Resilience4j)

Wrap Kafka publishing with circuit breaker and retry patterns.

- Add `resilience4j-spring-boot3` dependency
- `@CircuitBreaker` on `OutboxPublisherImpl.pollAndPublish()`
- `@Retry` with exponential backoff on individual message publishing
- **Why:** If Kafka is temporarily down, the poller currently retries every 5 seconds with no backoff or failure tracking. A circuit breaker stops hammering a dead broker

## 7. Reconciliation Endpoint

Verify system integrity: all ledger entries sum to zero, every payment has paired entries.

- GET /api/v1/admin/reconciliation
- Checks: total debits == total credits, no orphaned entries, no payments stuck in non-terminal states beyond a timeout
- Returns a report with pass/fail per check
- **Why:** How fintech companies detect bugs, fraud, and drift. Standard interview question: "how do you know the system is correct?"

## 8. Optimistic Locking on Payments (`@Version`)

Add a version column to the payments table for optimistic concurrency control.

- `@Version` annotation on a `version` field in the `Payment` entity
- Hibernate automatically increments it on each update and throws `OptimisticLockException` on conflict
- Complements the pessimistic locking on accounts — different strategies for different access patterns
- **Why:** Demonstrates understanding of when to use optimistic vs. pessimistic locking

## 9. Redis-Backed Idempotency (Fast Path)

Use Redis as a cache layer in front of the database for idempotency checks.

- Check Redis first (O(1) lookup) → if hit, return cached response without touching Postgres
- On cache miss, fall back to database (current behavior)
- On successful payment creation, write to both Redis and database
- Redis TTL matches the 24-hour key expiry
- **Why:** Reduces database load on retries. Production systems handle millions of idempotent retries — hitting Postgres for each is wasteful

## Priority Order

| Priority | Item | Effort | Interview Value |
|----------|------|--------|-----------------|
| 1 | Status History | Small | High — audit/compliance |
| 2 | balance_after | Small | High — auditability |
| 3 | Request Fingerprinting | Small | High — security |
| 4 | SKIP LOCKED | Tiny | Medium — scaling |
| 5 | MDC Trace ID | Small | Medium — observability |
| 6 | Reconciliation | Medium | High — system integrity |
| 7 | Circuit Breaker | Medium | Medium — resilience |
| 8 | Optimistic Locking | Small | Medium — concurrency |
| 9 | Redis Idempotency | Large | High — caching/performance |
