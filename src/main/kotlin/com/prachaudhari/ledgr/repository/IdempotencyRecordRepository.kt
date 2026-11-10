package com.prachaudhari.ledgr.repository

import com.prachaudhari.ledgr.domain.model.IdempotencyRecord
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import java.util.Optional

/**
 * Repository for idempotency key records.
 *
 * Keyed by the client-provided idempotency key (String PK).
 * Provides a pessimistic locking query to prevent concurrent
 * requests with the same key from creating duplicate payments.
 */
interface IdempotencyRecordRepository : JpaRepository<IdempotencyRecord, String> {

    /**
     * Finds an idempotency record by key with a pessimistic write lock (SELECT FOR UPDATE).
     *
     * This blocks any concurrent transaction trying to read the same key until
     * the current transaction commits or rolls back. If the key doesn't exist yet,
     * no row is locked — the caller must handle the insert race via unique constraint.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM IdempotencyRecord r WHERE r.key = :key")
    fun findByKeyForUpdate(key: String): Optional<IdempotencyRecord>
}