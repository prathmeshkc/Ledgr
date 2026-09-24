package com.prachaudhari.ledgr.domain.event

import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

data class PaymentEvent(
    val paymentId: UUID,
    val eventType: String,
    val sourceAccountId: UUID,
    val destinationAccountId: UUID,
    val amount: BigDecimal,
    val currency: String,
    val status: String,
    val timestamp: Instant = Instant.now()
)