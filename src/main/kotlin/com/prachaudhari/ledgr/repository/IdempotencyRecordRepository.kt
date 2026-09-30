package com.prachaudhari.ledgr.repository

import com.prachaudhari.ledgr.domain.model.IdempotencyRecord
import org.springframework.data.jpa.repository.JpaRepository

// Keyed by the client-provided idempotency key (String PK). Uses standard findById() to check for existing keys.
interface IdempotencyRecordRepository : JpaRepository<IdempotencyRecord, String>