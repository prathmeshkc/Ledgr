package com.prachaudhari.ledgr.repository

import com.prachaudhari.ledgr.domain.model.LedgerEntry
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import java.math.BigDecimal
import java.util.UUID

interface LedgerEntryRepository : JpaRepository<LedgerEntry, UUID> {

    // Returns all ledger entries for a given account — used to show transaction history
    fun findByAccountId(accountId: UUID): List<LedgerEntry>

    // Returns all ledger entries for a given payment — used to inspect the debit/credit pairs for a payment
    fun findByPaymentId(paymentId: UUID): List<LedgerEntry>

    // Derives the current balance for an account by summing all credits and subtracting all debits.
    // Returns 0 for accounts with no entries. Balance is NEVER stored — always computed from the ledger.
    @Query("""
        SELECT COALESCE(SUM(
            CASE WHEN e.entryType = 'CREDIT' THEN e.amount ELSE -e.amount END
        ), 0)
        FROM LedgerEntry e WHERE e.account.id = :accountId
    """)
    fun deriveBalance(accountId: UUID): BigDecimal
}