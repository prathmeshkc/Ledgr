package com.prachaudhari.ledgr.service.impl

import tools.jackson.databind.ObjectMapper
import com.prachaudhari.ledgr.domain.event.PaymentEvent
import com.prachaudhari.ledgr.domain.model.*
import com.prachaudhari.ledgr.repository.AccountRepository
import com.prachaudhari.ledgr.repository.PaymentRepository
import com.prachaudhari.ledgr.repository.OutboxEventRepository
import com.prachaudhari.ledgr.service.LedgerService
import com.prachaudhari.ledgr.service.PaymentService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionTemplate
import java.math.BigDecimal
import java.util.UUID

@Service
class PaymentServiceImpl(
    private val paymentRepository: PaymentRepository,
    private val accountRepository: AccountRepository,
    private val outboxEventRepository: OutboxEventRepository,
    private val ledgerService: LedgerService,
    private val idempotencyService: IdempotencyService,
    private val objectMapper: ObjectMapper,
    private val transactionTemplate: TransactionTemplate
) : PaymentService {

    override fun createPayment(
        idempotencyKey: String,
        sourceAccountId: UUID,
        destinationAccountId: UUID,
        amount: BigDecimal,
        currency: String
    ): Payment? {
        // Claim the idempotency key in an isolated transaction.
        // This runs in REQUIRES_NEW so a constraint violation from a concurrent
        // insert race rolls back only the inner transaction, not this one.
        return when (idempotencyService.claimKey(idempotencyKey)) {
            is IdempotencyService.ClaimResult.AlreadyComplete -> {
                paymentRepository.findByIdempotencyKey(idempotencyKey)
            }
            is IdempotencyService.ClaimResult.InFlight -> null
            is IdempotencyService.ClaimResult.RaceLost -> {
                // The winning thread claimed the key but may not have committed
                // the payment yet. Wait briefly then look it up.
                Thread.sleep(100)
                paymentRepository.findByIdempotencyKey(idempotencyKey)
            }
            is IdempotencyService.ClaimResult.Claimed -> {
                createNewPayment(idempotencyKey, sourceAccountId, destinationAccountId, amount, currency)
            }
        }
    }

    private fun createNewPayment(
        idempotencyKey: String,
        sourceAccountId: UUID,
        destinationAccountId: UUID,
        amount: BigDecimal,
        currency: String
    ): Payment {
        // Payment + outbox write must be atomic, so we use TransactionTemplate
        // instead of @Transactional (which doesn't work on self-invoked private methods).
        val payment = transactionTemplate.execute {
            val sourceAccount = accountRepository.findById(sourceAccountId)
                .orElseThrow { IllegalArgumentException("Source account not found: $sourceAccountId") }
            val destinationAccount = accountRepository.findById(destinationAccountId)
                .orElseThrow { IllegalArgumentException("Destination account not found: $destinationAccountId") }

            val p = Payment(
                idempotencyKey = idempotencyKey,
                sourceAccount = sourceAccount,
                destinationAccount = destinationAccount,
                amount = amount,
                currency = currency
            )
            paymentRepository.save(p)
            writeOutboxEvent(p, "PAYMENT_CREATED")
            p
        }!!

        idempotencyService.completeKey(
            idempotencyKey,
            objectMapper.writeValueAsString(
                mapOf("paymentId" to payment.id, "status" to payment.status.name)
            )
        )

        return payment
    }

    @Transactional
    override fun authorizePayment(paymentId: UUID): Payment {
        val payment = paymentRepository.findById(paymentId)
            .orElseThrow { IllegalArgumentException("Payment not found: $paymentId") }

        // Check if source account has sufficient balance
        val sourceBalance = ledgerService.getBalance(payment.sourceAccount.id!!)
        if (sourceBalance < payment.amount) {
            throw IllegalArgumentException(
                "Insufficient balance: account ${payment.sourceAccount.id} has $sourceBalance but payment requires ${payment.amount}"
            )
        }

        // State machine validates: only INITIATED → AUTHORIZED is allowed
        payment.transitionTo(PaymentStatus.AUTHORIZED)
        paymentRepository.save(payment)

        // Write double-entry ledger: debit source, credit hold
        val holdAccount = accountRepository.findByName("Hold Account")
            ?: throw IllegalStateException("Hold Account not found — was seed data created?")

        ledgerService.writeEntries(payment, payment.sourceAccount, holdAccount, payment.amount)

        // Write outbox event
        writeOutboxEvent(payment, "PAYMENT_AUTHORIZED")

        return payment
    }

    @Transactional
    override fun settlePayment(paymentId: UUID): Payment {
        val payment = paymentRepository.findById(paymentId)
            .orElseThrow { IllegalArgumentException("Payment not found: $paymentId") }

        // State machine validates: only AUTHORIZED → SETTLED is allowed
        payment.transitionTo(PaymentStatus.SETTLED)
        paymentRepository.save(payment)

        // Write double-entry ledger: debit hold, credit destination
        val holdAccount = accountRepository.findByName("Hold Account")
            ?: throw IllegalStateException("Hold Account not found — was seed data created?")

        ledgerService.writeEntries(payment, holdAccount, payment.destinationAccount, payment.amount)

        // Write outbox event
        writeOutboxEvent(payment, "PAYMENT_SETTLED")

        return payment
    }

    @Transactional
    override fun failPayment(paymentId: UUID): Payment {
        val payment = paymentRepository.findById(paymentId)
            .orElseThrow { IllegalArgumentException("Payment not found: $paymentId") }

        // State machine validates: only AUTHORIZED → FAILED is allowed
        payment.transitionTo(PaymentStatus.FAILED)
        paymentRepository.save(payment)

        // Write reversal ledger entries: debit hold, credit source (undo authorization)
        val holdAccount = accountRepository.findByName("Hold Account")
            ?: throw IllegalStateException("Hold Account not found — was seed data created?")

        ledgerService.writeEntries(payment, holdAccount, payment.sourceAccount, payment.amount)

        // Write outbox event
        writeOutboxEvent(payment, "PAYMENT_FAILED")

        return payment
    }

    override fun getPayment(paymentId: UUID): Payment {
        return paymentRepository.findById(paymentId)
            .orElseThrow { IllegalArgumentException("Payment not found: $paymentId") }
    }

    /**
     * Writes an event to the outbox table within the current transaction.
     *
     * This is the "write" side of the transactional outbox pattern. The event
     * is NOT published to Kafka here — it's written to the database in the
     * same transaction as the payment and ledger changes. The [OutboxPublisher]
     * polls for unpublished events and handles the actual Kafka publishing.
     *
     * This two-step approach (write to DB, then publish from DB) avoids the
     * dual-write problem: if we published to Kafka directly here, a crash
     * between the DB commit and the Kafka publish would leave the system in
     * an inconsistent state.
     *
     * @param payment The payment this event is about
     * @param eventType The type of event (e.g., PAYMENT_CREATED, PAYMENT_AUTHORIZED)
     */
    private fun writeOutboxEvent(payment: Payment, eventType: String) {
        val event = PaymentEvent(
            paymentId = payment.id!!,
            eventType = eventType,
            sourceAccountId = payment.sourceAccount.id!!,
            destinationAccountId = payment.destinationAccount.id!!,
            amount = payment.amount,
            currency = payment.currency,
            status = payment.status.name
        )

        val outboxEvent = OutboxEvent(
            aggregateType = "Payment",
            aggregateId = payment.id!!,
            eventType = eventType,
            payload = objectMapper.writeValueAsString(event)
        )
        outboxEventRepository.save(outboxEvent)
    }
}