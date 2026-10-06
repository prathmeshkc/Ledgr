package com.prachaudhari.ledgr.controller

import com.prachaudhari.ledgr.dto.AccountResponse
import com.prachaudhari.ledgr.dto.BalanceResponse
import com.prachaudhari.ledgr.dto.CreateAccountRequest
import com.prachaudhari.ledgr.dto.LedgerEntryResponse
import com.prachaudhari.ledgr.domain.model.Account
import com.prachaudhari.ledgr.domain.model.AccountType
import com.prachaudhari.ledgr.domain.model.LedgerEntry
import com.prachaudhari.ledgr.repository.AccountRepository
import com.prachaudhari.ledgr.service.LedgerService
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.util.UUID

/**
 * REST controller for account management and balance queries.
 *
 * Accounts are the entities that hold money in the ledger system. Every
 * payment moves money between two accounts, and the balance of each account
 * is derived from its ledger entries (never stored directly).
 *
 * This controller also exposes the ledger entries for an account, which
 * serves as the transaction history / audit trail.
 */
@RestController
@RequestMapping("/api/v1/accounts")
class AccountController(
    private val accountRepository: AccountRepository,
    private val ledgerService: LedgerService
) {

    /**
     * POST /api/v1/accounts
     *
     * Creates a new account. Type must be either "USER" or "SYSTEM".
     * USER accounts are for customers, SYSTEM accounts are for internal
     * use (hold accounts, fee accounts, etc.).
     */
    @PostMapping
    fun createAccount(@RequestBody request: CreateAccountRequest): ResponseEntity<AccountResponse> {
        val accountType = try {
            AccountType.valueOf(request.type.uppercase())
        } catch (e: IllegalArgumentException) {
            return ResponseEntity.badRequest().build()
        }

        val account = Account(
            name = request.name,
            type = accountType
        )
        val saved = accountRepository.save(account)
        return ResponseEntity.status(HttpStatus.CREATED).body(saved.toResponse())
    }

    /**
     * GET /api/v1/accounts/{id}
     *
     * Retrieves an account by its ID.
     */
    @GetMapping("/{id}")
    fun getAccount(@PathVariable id: UUID): ResponseEntity<AccountResponse> {
        val account = accountRepository.findById(id).orElse(null)
            ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(account.toResponse())
    }

    /**
     * GET /api/v1/accounts/{id}/balance
     *
     * Returns the derived balance for an account.
     *
     * The balance is computed as SUM(credits) - SUM(debits) across all
     * ledger entries for this account. It is never stored — always computed
     * fresh from the immutable ledger.
     */
    @GetMapping("/{id}/balance")
    fun getBalance(@PathVariable id: UUID): ResponseEntity<BalanceResponse> {
        if (!accountRepository.existsById(id)) {
            return ResponseEntity.notFound().build()
        }
        val balance = ledgerService.getBalance(id)
        return ResponseEntity.ok(BalanceResponse(accountId = id, balance = balance))
    }

    /**
     * GET /api/v1/accounts/{id}/ledger
     *
     * Returns all ledger entries for an account — the full transaction history.
     *
     * Each entry shows whether money entered (CREDIT) or left (DEBIT) the
     * account, the amount, and which payment triggered it. This is the
     * audit trail that makes the system transparent and verifiable.
     */
    @GetMapping("/{id}/ledger")
    fun getLedgerEntries(@PathVariable id: UUID): ResponseEntity<List<LedgerEntryResponse>> {
        if (!accountRepository.existsById(id)) {
            return ResponseEntity.notFound().build()
        }
        val entries = ledgerService.getEntriesByAccount(id)
        return ResponseEntity.ok(entries.map { it.toResponse() })
    }

    /**
     * Maps an Account entity to an AccountResponse DTO.
     */
    private fun Account.toResponse() = AccountResponse(
        id = id!!,
        name = name,
        type = type.name,
        createdAt = createdAt
    )

    /**
     * Maps a LedgerEntry entity to a LedgerEntryResponse DTO.
     */
    private fun LedgerEntry.toResponse() = LedgerEntryResponse(
        id = id!!,
        paymentId = payment.id!!,
        accountId = account.id!!,
        entryType = entryType.name,
        amount = amount,
        createdAt = createdAt
    )
}