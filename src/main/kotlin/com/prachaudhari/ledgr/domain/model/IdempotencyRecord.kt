package com.prachaudhari.ledgr.domain.model

import jakarta.persistence.*
import java.time.Instant

@Entity
@Table(name = "idempotency_keys")
class IdempotencyRecord(
    @Id
    @Column(name = "key", nullable = false)
    val key: String,

    @Column(nullable = false, length = 20)
    var status: String = "STARTED",

    @Column(columnDefinition = "jsonb")
    var response: String? = null,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now(),

    @Column(name = "expires_at", nullable = false)
    val expiresAt: Instant = Instant.now().plusSeconds(86400)
) {
    fun complete(responseBody: String) {
        status = "COMPLETE"
        response = responseBody
    }
}