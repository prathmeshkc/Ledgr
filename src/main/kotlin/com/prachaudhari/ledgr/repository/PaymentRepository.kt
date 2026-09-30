package com.prachaudhari.ledgr.repository

import com.prachaudhari.ledgr.domain.model.Payment
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface PaymentRepository : JpaRepository<Payment, UUID> {

    // Finds a payment by its client-provided idempotency key — used to detect retries and return cached responses
    fun findByIdempotencyKey(idempotencyKey: String): Payment?
}