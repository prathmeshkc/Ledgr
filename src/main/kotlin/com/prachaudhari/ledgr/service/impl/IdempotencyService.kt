package com.prachaudhari.ledgr.service.impl

import com.prachaudhari.ledgr.domain.model.IdempotencyRecord
import com.prachaudhari.ledgr.repository.IdempotencyRecordRepository
import jakarta.persistence.EntityManager
import jakarta.persistence.LockModeType
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

/**
 * Handles idempotency key claiming in an isolated transaction.
 *
 * Uses REQUIRES_NEW propagation so that each claim attempt runs in its own
 * transaction. This prevents constraint violations from corrupting the
 * caller's Hibernate session.
 */
@Service
class IdempotencyService(
    private val idempotencyRecordRepository: IdempotencyRecordRepository,
    private val entityManager: EntityManager
) {

    sealed class ClaimResult {
        data class Claimed(val key: String) : ClaimResult()
        data object AlreadyComplete : ClaimResult()
        data object InFlight : ClaimResult()
        data object RaceLost : ClaimResult()
    }

    /**
     * Attempts to claim an idempotency key using native SQL INSERT with ON CONFLICT.
     *
     * This avoids the Hibernate session corruption problem: instead of catching
     * a DataIntegrityViolationException (which poisons the session), we use
     * Postgres's INSERT ... ON CONFLICT DO NOTHING which simply returns 0 rows
     * affected if the key already exists.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun claimKey(key: String): ClaimResult {
        // First check if the key already exists with a pessimistic lock
        val existing = idempotencyRecordRepository.findByKeyForUpdate(key).orElse(null)
        if (existing != null) {
            return if (existing.status == "COMPLETE") ClaimResult.AlreadyComplete
            else ClaimResult.InFlight
        }

        // Use native INSERT ... ON CONFLICT to avoid exception-based control flow
        val rowsInserted = entityManager.createNativeQuery(
            """
            INSERT INTO idempotency_keys (key, status, created_at, expires_at)
            VALUES (:key, 'STARTED', now(), now() + interval '24 hours')
            ON CONFLICT (key) DO NOTHING
            """
        )
            .setParameter("key", key)
            .executeUpdate()

        return if (rowsInserted == 1) ClaimResult.Claimed(key)
        else ClaimResult.RaceLost
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun completeKey(key: String, responseBody: String) {
        val record = idempotencyRecordRepository.findById(key)
            .orElseThrow { IllegalStateException("Idempotency key not found: $key") }
        record.complete(responseBody)
        idempotencyRecordRepository.save(record)
    }
}