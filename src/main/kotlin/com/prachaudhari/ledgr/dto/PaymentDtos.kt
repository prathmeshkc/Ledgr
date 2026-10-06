package com.prachaudhari.ledgr.dto

import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

/**
 * Request body for creating a new payment.
 *
 * The idempotency key is sent as a header (Idempotency-Key), not in the body,
 * following the convention used by Stripe and other payment APIs.
 */
data class CreatePaymentRequest(
    val sourceAccountId: UUID,
    val destinationAccountId: UUID,
    val amount: BigDecimal,
    val currency: String = "USD"
)

/**
 * Response body for payment operations.
 *
 * This is the external representation of a payment. It uses account IDs
 * instead of full account objects to keep the response flat and avoid
 * exposing internal entity relationships.
 */
data class PaymentResponse(
    val id: UUID,
    val idempotencyKey: String,
    val sourceAccountId: UUID,
    val destinationAccountId: UUID,
    val amount: BigDecimal,
    val currency: String,
    val status: String,
    val createdAt: Instant,
    val updatedAt: Instant
)