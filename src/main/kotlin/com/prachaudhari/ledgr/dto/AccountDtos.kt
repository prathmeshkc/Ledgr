package com.prachaudhari.ledgr.dto

import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

/**
 * Request body for creating a new account.
 */
data class CreateAccountRequest(
    val name: String,
    val type: String
)

/**
 * Response body for account operations.
 */
data class AccountResponse(
    val id: UUID,
    val name: String,
    val type: String,
    val createdAt: Instant
)

/**
 * Response body for balance queries.
 *
 * Balance is always derived from ledger entries (SUM of credits minus debits),
 * never stored. This response makes that explicit.
 */
data class BalanceResponse(
    val accountId: UUID,
    val balance: BigDecimal
)

/**
 * Response body for a single ledger entry.
 *
 * Represents one side of a double-entry pair (either a debit or a credit).
 */
data class LedgerEntryResponse(
    val id: UUID,
    val paymentId: UUID,
    val accountId: UUID,
    val entryType: String,
    val amount: BigDecimal,
    val createdAt: Instant
)