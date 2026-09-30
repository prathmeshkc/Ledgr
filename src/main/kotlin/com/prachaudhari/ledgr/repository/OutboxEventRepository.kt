package com.prachaudhari.ledgr.repository

import com.prachaudhari.ledgr.domain.model.OutboxEvent
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface OutboxEventRepository : JpaRepository<OutboxEvent, UUID> {
    fun findByPublishedAtIsNullOrderByCreatedAtAsc(): List<OutboxEvent>
}