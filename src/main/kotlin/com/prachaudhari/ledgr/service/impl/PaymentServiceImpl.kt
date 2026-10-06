package com.prachaudhari.ledgr.service.impl

import tools.jackson.databind.ObjectMapper
import com.prachaudhari.ledgr.domain.event.PaymentEvent
import com.prachaudhari.ledgr.domain.model.*
import com.prachaudhari.ledgr.repository.AccountRepository
import com.prachaudhari.ledgr.repository.IdempotencyRecordRepository
import com.prachaudhari.ledgr.repository.OutboxEventRepository
import com.prachaudhari.ledgr.repository.PaymentRepository
import com.prachaudhari.ledgr.service.LedgerService
import com.prachaudhari.ledgr.service.PaymentService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.util.UUID

@Service
class PaymentServiceImpl(
    private val paymentRepository: PaymentRepository,
    private val accountRepository: AccountRepository,
    private val idempotencyRecordRepository: IdempotencyRecordRepository,
    private val outboxEventRepository: OutboxEventRepository,
    private val ledgerService: LedgerService,
    private val objectMapper: ObjectMapper
) : PaymentService {

    @Transactional
    override fun createPayment(
        idempotencyKey: String,
        sourceAccountId: UUID,
        destinationAccountId: UUID,
        amount: BigDecimal,
        currency: String
    ): Payment? {
        // Check idempotency: has this key been used before?
        val existingRecord = idempotencyRecordRepository.findById(idempotencyKey).orElse(null)
        if (existingRecord != null) {
            if (existingRecord.status == "COMPLETE") {
                // Return the original payment — this is a retry
                return paymentRepository.findByIdempotencyKey(idempotencyKey)
            }
            // Status is STARTED — previous request still in-flight
            return null
        }

        // Create idempotency record to claim this key
        val idempotencyRecord = IdempotencyRecord(key = idempotencyKey)
        idempotencyRecordRepository.save(idempotencyRecord)

        // Look up the source and destination accounts
        val sourceAccount = accountRepository.findById(sourceAccountId)
            .orElseThrow { IllegalArgumentException("Source account not found: $sourceAccountId") }
        val destinationAccount = accountRepository.findById(destinationAccountId)
            .orElseThrow { IllegalArgumentException("Destination account not found: $destinationAccountId") }

        // Create the payment in INITIATED status
        val payment = Payment(
            idempotencyKey = idempotencyKey,
            sourceAccount = sourceAccount,
            destinationAccount = destinationAccount,
            amount = amount,
            currency = currency
        )
        paymentRepository.save(payment)

        // Write outbox event — will be published to Kafka by the outbox poller
        writeOutboxEvent(payment, "PAYMENT_CREATED")

        // Mark idempotency record as complete with the response
        idempotencyRecord.complete(objectMapper.writeValueAsString(
            mapOf("paymentId" to payment.id, "status" to payment.status.name)
        ))
        idempotencyRecordRepository.save(idempotencyRecord)

        return payment
    }

    @Transactional
    override fun authorizePayment(paymentId: UUID): Payment {
        val payment = paymentRepository.findById(paymentId)
            .orElseThrow { IllegalArgumentException("Payment not found: $paymentId") }

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