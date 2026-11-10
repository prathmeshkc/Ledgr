# Concurrent Idempotency

## The Problem

Idempotency keys prevent duplicate payments when a client retries a request. But what happens when two identical requests arrive **at the same time**?

### The Race Condition

```
Timeline:
─────────────────────────────────────────────────────────────

Thread A: SELECT * FROM idempotency_keys WHERE key = 'abc-123'
          → NULL (key doesn't exist)

Thread B: SELECT * FROM idempotency_keys WHERE key = 'abc-123'
          → NULL (key doesn't exist — Thread A hasn't inserted yet)

Thread A: INSERT INTO idempotency_keys (key, status) VALUES ('abc-123', 'STARTED')
          → SUCCESS

Thread A: INSERT INTO payments (...) → Payment #1 created ✓

Thread B: INSERT INTO idempotency_keys (key, status) VALUES ('abc-123', 'STARTED')
          → SUCCESS ← DUPLICATE KEY!

Thread B: INSERT INTO payments (...) → Payment #2 created ← DOUBLE CHARGE!
```

This is a classic **Time-of-Check to Time-of-Use (TOCTOU)** bug. Both threads check for the key, both see it doesn't exist, and both proceed to create a payment. The customer gets charged twice.

## The Solution: Two Layers of Defense

### Layer 1: Pessimistic Locking (`SELECT FOR UPDATE`)

```sql
SELECT * FROM idempotency_keys WHERE key = 'abc-123' FOR UPDATE
```

`FOR UPDATE` acquires a **row-level exclusive lock** on the matching row. Any other transaction trying to `SELECT FOR UPDATE` the same row **blocks** until the first transaction commits or rolls back.

```
Timeline (with SELECT FOR UPDATE):
─────────────────────────────────────────────────────────────

Thread A: SELECT ... FOR UPDATE WHERE key = 'abc-123'
          → NULL (no row to lock, but continues)

Thread B: SELECT ... FOR UPDATE WHERE key = 'abc-123'
          → NULL (no row exists yet — nothing to lock, continues)
                  ↑ THIS IS THE GAP — FOR UPDATE can't lock a row that doesn't exist
```

**Limitation:** `SELECT FOR UPDATE` locks **existing** rows. When the key doesn't exist yet, there's no row to lock, so both threads still pass through. This is why we need a second layer.

### Layer 2: INSERT ... ON CONFLICT DO NOTHING

Instead of catching exceptions (which corrupts Hibernate's session), we use Postgres's native upsert:

```sql
INSERT INTO idempotency_keys (key, status, created_at, expires_at)
VALUES ('abc-123', 'STARTED', now(), now() + interval '24 hours')
ON CONFLICT (key) DO NOTHING
```

If the key already exists, this returns 0 rows affected — no exception thrown, no session corruption.

```
Timeline (complete solution):
─────────────────────────────────────────────────────────────

Thread A: SELECT ... FOR UPDATE WHERE key = 'abc-123' → NULL
Thread B: SELECT ... FOR UPDATE WHERE key = 'abc-123' → NULL
Thread A: INSERT ... ON CONFLICT DO NOTHING → 1 row inserted ✓
Thread A: INSERT payment → Payment #1 created ✓
Thread A: UPDATE idempotency_keys SET status = 'COMPLETE' → COMMIT

Thread B: INSERT ... ON CONFLICT DO NOTHING → 0 rows inserted
Thread B: Returns existing Payment #1 (or waits briefly if not yet committed) ✓
```

### Why Both Layers?

| Scenario | Layer 1 (FOR UPDATE) handles it? | Layer 2 (Unique Constraint) handles it? |
|----------|----------------------------------|----------------------------------------|
| Key already exists, retry arrives | ✅ Locks the row, returns cached response | Not reached |
| Key doesn't exist, two simultaneous requests | ❌ No row to lock | ✅ Second insert returns 0 rows |
| Key exists with STARTED status (in-flight) | ✅ Locks the row, returns 409 Conflict | Not reached |

Layer 1 handles the **common case** (retries of completed requests) efficiently with locking.
Layer 2 handles the **edge case** (true simultaneous first requests) as a safety net.

## How It Works in Code

### Repository

```kotlin
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("SELECT r FROM IdempotencyRecord r WHERE r.key = :key")
fun findByKeyForUpdate(key: String): Optional<IdempotencyRecord>
```

`LockModeType.PESSIMISTIC_WRITE` translates to `SELECT ... FOR UPDATE` in SQL.

### Service (IdempotencyService)

```kotlin
@Transactional(propagation = Propagation.REQUIRES_NEW)
fun claimKey(key: String): ClaimResult {
    // Layer 1: Pessimistic lock on existing records
    val existing = idempotencyRecordRepository.findByKeyForUpdate(key).orElse(null)
    if (existing != null) {
        return if (existing.status == "COMPLETE") ClaimResult.AlreadyComplete
        else ClaimResult.InFlight
    }

    // Layer 2: Native INSERT with ON CONFLICT for new key races
    val rowsInserted = entityManager.createNativeQuery("""
        INSERT INTO idempotency_keys (key, status, created_at, expires_at)
        VALUES (:key, 'STARTED', now(), now() + interval '24 hours')
        ON CONFLICT (key) DO NOTHING
    """).setParameter("key", key).executeUpdate()

    return if (rowsInserted == 1) ClaimResult.Claimed(key)
    else ClaimResult.RaceLost
}
```

The idempotency claim runs in `REQUIRES_NEW` — an isolated transaction — so it doesn't interfere with the caller's transaction.

### Orchestrator (PaymentServiceImpl)

```kotlin
override fun createPayment(...): Payment? {
    return when (idempotencyService.claimKey(idempotencyKey)) {
        is ClaimResult.AlreadyComplete -> paymentRepository.findByIdempotencyKey(idempotencyKey)
        is ClaimResult.InFlight -> null  // 409 Conflict
        is ClaimResult.RaceLost -> paymentRepository.findByIdempotencyKey(idempotencyKey)
        is ClaimResult.Claimed -> createNewPayment(...)
    }
}
```

The orchestrator is NOT `@Transactional` — each step manages its own transaction boundaries.

## Key Concepts

### Pessimistic vs. Optimistic Locking

| | Pessimistic | Optimistic |
|---|---|---|
| **Strategy** | Lock before reading | Detect conflicts on write |
| **Mechanism** | `SELECT FOR UPDATE` (database lock) | Version column + retry |
| **Blocks others?** | Yes — other transactions wait | No — but may need to retry |
| **Best for** | High contention, critical operations | Low contention, read-heavy workloads |
| **Used here** | ✅ Payment systems can't afford conflicts | |

### Why `saveAndFlush()` Instead of `save()`?

- `save()` may defer the actual INSERT until transaction commit (Hibernate batching)
- If the INSERT is deferred, the `DataIntegrityViolationException` is thrown at commit time — outside our try/catch
- `saveAndFlush()` forces the INSERT immediately, so we catch the violation and handle it gracefully

### Why Not Just Use ON CONFLICT Alone?

We could skip `SELECT FOR UPDATE` and rely entirely on `ON CONFLICT`, but:

1. **Performance**: For completed keys (the common retry case), `SELECT FOR UPDATE` returns the cached response without attempting an INSERT at all
2. **In-flight detection**: `SELECT FOR UPDATE` can detect STARTED status and return 409, which the `ON CONFLICT` approach can't distinguish
3. **Locking semantics**: `SELECT FOR UPDATE` blocks concurrent readers of the same key, giving deterministic ordering

### Why ON CONFLICT Instead of Catching Exceptions?

In JPA/Hibernate, a `DataIntegrityViolationException` from `saveAndFlush()` marks the Hibernate session as **corrupted** — any further operations in the same transaction will fail with `UnexpectedRollbackException`. Postgres's `ON CONFLICT DO NOTHING` avoids this entirely by returning 0 rows instead of throwing an error.

## Interview Talking Points

- **"How do you handle concurrent idempotency?"** — Two layers: pessimistic locking for existing keys, unique constraint for new key races
- **"Why not just use optimistic locking?"** — Payment systems need deterministic outcomes, not retry-and-hope. A lost optimistic lock means you attempted to create a payment and had to roll it back
- **"What's the trade-off of SELECT FOR UPDATE?"** — It serializes concurrent requests for the same key, adding latency. But for payments, correctness > throughput
- **"Can this deadlock?"** — No, because we only lock one row (the idempotency key) and always in the same order. Deadlocks happen when two transactions lock multiple rows in different orders