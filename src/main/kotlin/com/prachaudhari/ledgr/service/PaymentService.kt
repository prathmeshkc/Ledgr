package com.prachaudhari.ledgr.service

import com.prachaudhari.ledgr.domain.model.Payment
import java.math.BigDecimal
import java.util.UUID

/**
 * Orchestrates the payment lifecycle from creation through settlement or failure.
 *
 * This is the central service that coordinates all the pieces:
 * - The payment state machine (validates transitions)
 * - The ledger service (writes double-entry records)
 * - The outbox (writes events for Kafka publishing)
 * - The idempotency layer (prevents duplicate processing)
 *
 * Every state transition is wrapped in a single @Transactional block. This means
 * the payment status update, the ledger entries, and the outbox event are all
 * committed atomically — if any step fails, the entire transaction rolls back.
 *
 * This is the key consistency guarantee: you will never have a payment marked
 * as SETTLED without the corresponding ledger entries, and you will never have
 * ledger entries without a corresponding outbox event.
 */
interface PaymentService {

    /**
     * Creates a new payment with idempotency protection.
     *
     * Flow:
     * 1. Check if the idempotency key has been seen before
     *    - If COMPLETE: return the cached response (no new payment created)
     *    - If STARTED: a previous request is still in-flight (Phase 2 will add locking here)
     *    - If not found: proceed with creation
     * 2. Create an idempotency record with status STARTED
     * 3. Create the payment in INITIATED status
     * 4. Write an outbox event (PAYMENT_CREATED)
     * 5. Mark the idempotency record as COMPLETE with the cached response
     *
     * All of this happens in a single database transaction. If the app crashes
     * between steps 2 and 5, the idempotency record stays in STARTED status,
     * which Phase 2 will handle (currently it returns null, indicating in-flight).
     *
     * @param idempotencyKey Client-provided key to prevent duplicate payments
     * @param sourceAccountId The account to debit (sender)
     * @param destinationAccountId The account to credit (receiver)
     * @param amount The payment amount (must be positive)
     * @param currency ISO 4217 currency code (defaults to USD)
     * @return The created [Payment], or the existing one if the idempotency key was already used.
     *         Returns null if a previous request with this key is still in-flight.
     */
    fun createPayment(
        idempotencyKey: String,
        sourceAccountId: UUID,
        destinationAccountId: UUID,
        amount: BigDecimal,
        currency: String = "USD"
    ): Payment?

    /**
     * Authorizes a payment — transitions from INITIATED to AUTHORIZED.
     *
     * This is when money is "held" but not yet transferred. In real payment
     * systems, this is the step where the card network confirms the cardholder
     * has sufficient funds.
     *
     * Ledger entries written:
     * - DEBIT on source account (money leaves the sender's available balance)
     * - CREDIT on hold account (money is held pending settlement)
     *
     * The hold account is a SYSTEM account that temporarily holds funds between
     * authorization and settlement. This mirrors how real payment processors
     * work — authorized funds are neither with the sender nor the receiver.
     *
     * @param paymentId The UUID of the payment to authorize
     * @return The updated [Payment] in AUTHORIZED status
     * @throws IllegalArgumentException if the payment is not found
     * @throws IllegalStateException if the payment is not in INITIATED status
     */
    fun authorizePayment(paymentId: UUID): Payment

    /**
     * Settles a payment — transitions from AUTHORIZED to SETTLED.
     *
     * This is the final step where money actually reaches the receiver.
     * Settlement releases the hold and credits the destination account.
     *
     * Ledger entries written:
     * - DEBIT on hold account (releases the held funds)
     * - CREDIT on destination account (receiver gets the money)
     *
     * After settlement, the hold account's net balance for this payment is zero
     * (one credit from authorization, one debit from settlement), which is
     * correct — the hold account is just a pass-through.
     *
     * @param paymentId The UUID of the payment to settle
     * @return The updated [Payment] in SETTLED status
     * @throws IllegalArgumentException if the payment is not found
     * @throws IllegalStateException if the payment is not in AUTHORIZED status
     */
    fun settlePayment(paymentId: UUID): Payment

    /**
     * Fails a payment — transitions from AUTHORIZED to FAILED.
     *
     * When a payment fails after authorization, the held funds must be returned
     * to the sender. This is done by writing reversal ledger entries that undo
     * the authorization.
     *
     * Ledger entries written:
     * - DEBIT on hold account (releases the held funds)
     * - CREDIT on source account (money returned to sender)
     *
     * These entries are the mirror image of the authorization entries,
     * effectively canceling them out. The source account's balance is restored
     * to what it was before the payment was authorized.
     *
     * @param paymentId The UUID of the payment to fail
     * @return The updated [Payment] in FAILED status
     * @throws IllegalArgumentException if the payment is not found
     * @throws IllegalStateException if the payment is not in AUTHORIZED status
     */
    fun failPayment(paymentId: UUID): Payment

    /**
     * Retrieves a payment by its ID.
     *
     * @param paymentId The UUID of the payment
     * @return The [Payment] if found
     * @throws IllegalArgumentException if the payment is not found
     */
    fun getPayment(paymentId: UUID): Payment
}