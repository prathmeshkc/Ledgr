package com.prachaudhari.ledgr.repository

import com.prachaudhari.ledgr.domain.model.OutboxEvent
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface OutboxEventRepository : JpaRepository<OutboxEvent, UUID> {

    // Fetches all unpublished events ordered by creation time — used by the outbox poller to publish to Kafka in sequence
    fun findByPublishedAtIsNullOrderByCreatedAtAsc(): List<OutboxEvent>
}