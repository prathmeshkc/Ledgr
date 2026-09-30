package com.prachaudhari.ledgr.repository

import com.prachaudhari.ledgr.domain.model.LedgerEntry
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import java.math.BigDecimal
import java.util.UUID

interface LedgerEntryRepository : JpaRepository<LedgerEntry, UUID> {

    fun findByAccountId(accountId: UUID): List<LedgerEntry>

    fun findByPaymentId(paymentId: UUID): List<LedgerEntry>

    @Query("""
        SELECT COALESCE(SUM(
            CASE WHEN e.entryType = 'CREDIT' THEN e.amount ELSE -e.amount END
        ), 0)
        FROM LedgerEntry e WHERE e.account.id = :accountId
    """)
    fun deriveBalance(accountId: UUID): BigDecimal
}