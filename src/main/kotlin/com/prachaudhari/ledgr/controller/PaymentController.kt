package com.prachaudhari.ledgr.controller

import com.prachaudhari.ledgr.dto.CreatePaymentRequest
import com.prachaudhari.ledgr.dto.PaymentResponse
import com.prachaudhari.ledgr.domain.model.Payment
import com.prachaudhari.ledgr.service.PaymentService
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.util.UUID

/**
 * REST controller for the payment lifecycle.
 *
 * Exposes endpoints to create payments and transition them through the
 * state machine (authorize, settle, fail). Each mutation endpoint triggers
 * ledger entries, outbox events, and state validation under the hood.
 *
 * The Idempotency-Key header on payment creation follows the convention
 * used by Stripe, PayPal, and other payment APIs — the client generates
 * a UUID and sends it with the request. On retry, the same key returns
 * the original response without creating a duplicate payment.
 */
@RestController
@RequestMapping("/api/v1/payments")
class PaymentController(
    private val paymentService: PaymentService
) {

    /**
     * POST /api/v1/payments
     *
     * Creates a new payment in INITIATED status.
     * Requires an Idempotency-Key header to prevent duplicate processing.
     *
     * Returns 201 Created on success, or the cached response on retry.
     * Returns 409 Conflict if a previous request with this key is still in-flight.
     */
    @PostMapping
    fun createPayment(
        @RequestHeader("Idempotency-Key") idempotencyKey: String,
        @RequestBody request: CreatePaymentRequest
    ): ResponseEntity<PaymentResponse> {
        val payment = paymentService.createPayment(
            idempotencyKey = idempotencyKey,
            sourceAccountId = request.sourceAccountId,
            destinationAccountId = request.destinationAccountId,
            amount = request.amount,
            currency = request.currency
        )

        return if (payment != null) {
            ResponseEntity.status(HttpStatus.CREATED).body(payment.toResponse())
        } else {
            ResponseEntity.status(HttpStatus.CONFLICT).build()
        }
    }

    /**
     * GET /api/v1/payments/{id}
     *
     * Retrieves a payment by its ID.
     */
    @GetMapping("/{id}")
    fun getPayment(@PathVariable id: UUID): ResponseEntity<PaymentResponse> {
        val payment = paymentService.getPayment(id)
        return ResponseEntity.ok(payment.toResponse())
    }

    /**
     * POST /api/v1/payments/{id}/authorize
     *
     * Transitions a payment from INITIATED → AUTHORIZED.
     * Writes ledger entries: debit source account, credit hold account.
     */
    @PostMapping("/{id}/authorize")
    fun authorizePayment(@PathVariable id: UUID): ResponseEntity<PaymentResponse> {
        val payment = paymentService.authorizePayment(id)
        return ResponseEntity.ok(payment.toResponse())
    }

    /**
     * POST /api/v1/payments/{id}/settle
     *
     * Transitions a payment from AUTHORIZED → SETTLED.
     * Writes ledger entries: debit hold account, credit destination account.
     */
    @PostMapping("/{id}/settle")
    fun settlePayment(@PathVariable id: UUID): ResponseEntity<PaymentResponse> {
        val payment = paymentService.settlePayment(id)
        return ResponseEntity.ok(payment.toResponse())
    }

    /**
     * POST /api/v1/payments/{id}/fail
     *
     * Transitions a payment from AUTHORIZED → FAILED.
     * Writes reversal ledger entries: debit hold account, credit source account.
     */
    @PostMapping("/{id}/fail")
    fun failPayment(@PathVariable id: UUID): ResponseEntity<PaymentResponse> {
        val payment = paymentService.failPayment(id)
        return ResponseEntity.ok(payment.toResponse())
    }

    /**
     * Maps a Payment entity to a PaymentResponse DTO.
     *
     * This keeps JPA entities out of the API response — controllers never
     * expose internal entity structure to the client.
     */
    private fun Payment.toResponse() = PaymentResponse(
        id = id!!,
        idempotencyKey = idempotencyKey,
        sourceAccountId = sourceAccount.id!!,
        destinationAccountId = destinationAccount.id!!,
        amount = amount,
        currency = currency,
        status = status.name,
        createdAt = createdAt,
        updatedAt = updatedAt
    )
}