package com.prachaudhari.ledgr.repository

import com.prachaudhari.ledgr.domain.model.Payment
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface PaymentRepository : JpaRepository<Payment, UUID> {
    fun findByIdempotencyKey(idempotencyKey: String): Payment?
}