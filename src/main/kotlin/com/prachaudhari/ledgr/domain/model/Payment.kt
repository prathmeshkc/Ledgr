package com.prachaudhari.ledgr.domain.model

import jakarta.persistence.*
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "payments")
class Payment(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    val id: UUID? = null,

    @Column(name = "idempotency_key", nullable = false, unique = true)
    val idempotencyKey: String,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_account_id", nullable = false)
    val sourceAccount: Account,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "destination_account_id", nullable = false)
    val destinationAccount: Account,

    @Column(nullable = false, precision = 19, scale = 4)
    val amount: BigDecimal,

    @Column(nullable = false, length = 3)
    val currency: String = "USD",

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    var status: PaymentStatus = PaymentStatus.INITIATED,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now()
) {
    fun transitionTo(target: PaymentStatus) {
        status = status.transitionTo(target)
        updatedAt = Instant.now()
    }
}
