package com.prachaudhari.ledgr.service

import com.prachaudhari.ledgr.domain.model.Account
import com.prachaudhari.ledgr.domain.model.LedgerEntry
import com.prachaudhari.ledgr.domain.model.Payment
import java.math.BigDecimal
import java.util.UUID

/**
 * Core double-entry bookkeeping service.
 *
 * Every money movement in the system passes through this service. It enforces
 * the fundamental accounting invariant: for every debit, there must be an equal
 * and opposite credit. This guarantees that the ledger always sums to zero
 * across all accounts.
 *
 * Ledger entries are immutable and append-only — once written, they are never
 * updated or deleted. Corrections are made by writing new compensating entries.
 * This provides a complete, tamper-evident audit trail.
 *
 * Balances are never stored as a field on any account. Instead, they are always
 * derived by summing all ledger entries for that account. This eliminates an
 * entire class of bugs where a stored balance drifts out of sync with the
 * actual transaction history.
 */
interface LedgerService {

    /**
     * Writes a paired debit/credit entry — the core of double-entry bookkeeping.
     *
     * For every call, exactly two [LedgerEntry] rows are created:
     * 1. A DEBIT on [debitAccount] — money leaves this account
     * 2. A CREDIT on [creditAccount] — money enters this account
     *
     * Both entries use the same [amount], ensuring the pair sums to zero.
     *
     * This method does NOT manage its own transaction. The caller (typically
     * [PaymentService]) wraps the state transition, ledger write, and outbox
     * write in a single @Transactional block. This guarantees atomicity:
     * either all three happen, or none do.
     *
     * Example — Authorization of a $50 payment:
     * - debitAccount = Alice (USER) → her available balance decreases
     * - creditAccount = Hold (SYSTEM) → funds are held pending settlement
     *
     * Example — Settlement of that same payment:
     * - debitAccount = Hold (SYSTEM) → hold is released
     * - creditAccount = Bob (USER) → he receives the funds
     *
     * @param payment The payment that triggered this money movement
     * @param debitAccount The account being debited (money leaves)
     * @param creditAccount The account being credited (money enters)
     * @param amount The amount to move (always positive)
     */
    fun writeEntries(payment: Payment, debitAccount: Account, creditAccount: Account, amount: BigDecimal)

    /**
     * Derives the current balance for an account by summing all its ledger entries.
     *
     * Balance = SUM(credits) - SUM(debits)
     *
     * This is computed on every call — there is no cached or stored balance.
     * For accounts with no entries, returns BigDecimal.ZERO.
     *
     * A positive balance means the account has received more credits than debits.
     * A negative balance means more money has left than entered (e.g., a user
     * who has sent payments but not received any).
     *
     * @param accountId The UUID of the account to compute the balance for
     * @return The derived balance as a [BigDecimal]
     */
    fun getBalance(accountId: UUID): BigDecimal

    /**
     * Returns all ledger entries for a given account, ordered by creation time.
     *
     * Used to display transaction history for an account. Each entry shows
     * whether money entered (CREDIT) or left (DEBIT) the account, the amount,
     * and which payment triggered it.
     *
     * @param accountId The UUID of the account
     * @return List of all [LedgerEntry] rows for this account
     */
    fun getEntriesByAccount(accountId: UUID): List<LedgerEntry>

    /**
     * Returns all ledger entries for a given payment.
     *
     * A fully settled payment will have 4 entries:
     * - Authorization: 1 debit (source) + 1 credit (hold)
     * - Settlement: 1 debit (hold) + 1 credit (destination)
     *
     * A failed payment (after authorization) will have 4 entries:
     * - Authorization: 1 debit (source) + 1 credit (hold)
     * - Reversal: 1 debit (hold) + 1 credit (source) — money returned
     *
     * @param paymentId The UUID of the payment
     * @return List of all [LedgerEntry] rows for this payment
     */
    fun getEntriesByPayment(paymentId: UUID): List<LedgerEntry>
}