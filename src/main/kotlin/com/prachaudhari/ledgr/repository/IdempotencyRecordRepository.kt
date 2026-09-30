package com.prachaudhari.ledgr.repository

import com.prachaudhari.ledgr.domain.model.IdempotencyRecord
import org.springframework.data.jpa.repository.JpaRepository

interface IdempotencyRecordRepository : JpaRepository<IdempotencyRecord, String>