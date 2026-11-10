package com.prachaudhari.ledgr.service

import com.prachaudhari.ledgr.domain.model.Account
import com.prachaudhari.ledgr.domain.model.AccountType
import com.prachaudhari.ledgr.repository.AccountRepository
import com.prachaudhari.ledgr.repository.PaymentRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import java.math.BigDecimal
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

/**
 * Integration test for concurrent idempotency.
 *
 * Runs against the Docker Compose Postgres and Kafka (must be running).
 * Tests that simultaneous requests with the same idempotency key
 * create exactly one payment — not duplicates.
 *
 * Run with: ./gradlew test --tests "*.ConcurrentIdempotencyTest"
 * Requires: docker compose up -d
 */
@SpringBootTest
class ConcurrentIdempotencyTest {

    @Autowired
    lateinit var paymentService: PaymentService

    @Autowired
    lateinit var paymentRepository: PaymentRepository

    @Autowired
    lateinit var accountRepository: AccountRepository

    private lateinit var sourceAccount: Account
    private lateinit var destinationAccount: Account

    @BeforeEach
    fun setUp() {
        sourceAccount = accountRepository.findByName("Alice Wallet")
            ?: accountRepository.save(Account(name = "Alice Wallet", type = AccountType.USER))
        destinationAccount = accountRepository.findByName("Bob Wallet")
            ?: accountRepository.save(Account(name = "Bob Wallet", type = AccountType.USER))
    }

    @Test
    fun `concurrent requests with same idempotency key should create only one payment`() {
        val threadCount = 5
        val idempotencyKey = "concurrent-test-${UUID.randomUUID()}"
        val gate = CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(threadCount)
        val successCount = AtomicInteger(0)
        val conflictCount = AtomicInteger(0)
        val errorCount = AtomicInteger(0)

        val futures = (1..threadCount).map {
            executor.submit {
                gate.await()
                try {
                    val result = paymentService.createPayment(
                        idempotencyKey = idempotencyKey,
                        sourceAccountId = sourceAccount.id!!,
                        destinationAccountId = destinationAccount.id!!,
                        amount = BigDecimal("10.00"),
                        currency = "USD"
                    )
                    if (result != null) {
                        successCount.incrementAndGet()
                    } else {
                        conflictCount.incrementAndGet()
                    }
                } catch (e: Exception) {
                    errorCount.incrementAndGet()
                }
            }
        }

        // Release all threads at once — simulates simultaneous requests
        gate.countDown()

        futures.forEach { it.get() }
        executor.shutdown()

        val payment = paymentRepository.findByIdempotencyKey(idempotencyKey)

        assertEquals(1, if (payment != null) 1 else 0, "Exactly one payment should be created")
        assertEquals(0, errorCount.get(), "No unhandled errors should occur")
        assertTrue(successCount.get() >= 1, "At least one thread should succeed")
        println("Results: ${successCount.get()} succeeded, ${conflictCount.get()} conflicts, ${errorCount.get()} errors")
    }

    @Test
    fun `sequential retries with same idempotency key should return same payment`() {
        val idempotencyKey = "retry-test-${UUID.randomUUID()}"

        val firstPayment = paymentService.createPayment(
            idempotencyKey = idempotencyKey,
            sourceAccountId = sourceAccount.id!!,
            destinationAccountId = destinationAccount.id!!,
            amount = BigDecimal("25.00"),
            currency = "USD"
        )

        val retryPayment = paymentService.createPayment(
            idempotencyKey = idempotencyKey,
            sourceAccountId = sourceAccount.id!!,
            destinationAccountId = destinationAccount.id!!,
            amount = BigDecimal("25.00"),
            currency = "USD"
        )

        assertEquals(firstPayment!!.id, retryPayment!!.id, "Retry should return the same payment")
        assertEquals(firstPayment.idempotencyKey, retryPayment.idempotencyKey)
    }
}
