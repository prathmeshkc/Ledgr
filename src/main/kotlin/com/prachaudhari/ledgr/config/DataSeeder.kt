package com.prachaudhari.ledgr.config

import com.prachaudhari.ledgr.domain.model.Account
import com.prachaudhari.ledgr.domain.model.AccountType
import com.prachaudhari.ledgr.repository.AccountRepository
import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.stereotype.Component

/**
 * Seeds the database with initial accounts on application startup.
 *
 * This creates the minimum set of accounts needed for the payment flow to work:
 * - Two USER accounts (Alice and Bob) for testing payments between users
 * - One SYSTEM account (Hold Account) used internally during authorization
 *   to hold funds between the sender and receiver
 *
 * The seeder is idempotent — it checks if each account already exists by name
 * before creating it, so restarting the app won't create duplicates.
 *
 * Implements [ApplicationRunner] which Spring calls after the application
 * context is fully initialized (including Flyway migrations), so the
 * accounts table is guaranteed to exist when this runs.
 */
@Component
class DataSeeder(
    private val accountRepository: AccountRepository
) : ApplicationRunner {

    private val logger = LoggerFactory.getLogger(DataSeeder::class.java)

    override fun run(args: ApplicationArguments) {
        seedAccount("Alice Wallet", AccountType.USER)
        seedAccount("Bob Wallet", AccountType.USER)
        seedAccount("Hold Account", AccountType.SYSTEM)
    }

    /**
     * Creates an account if one with the given name doesn't already exist.
     *
     * @param name The account name (used for lookup and display)
     * @param type USER for customer accounts, SYSTEM for internal accounts
     */
    private fun seedAccount(name: String, type: AccountType) {
        if (accountRepository.findByName(name) != null) {
            logger.info("Account already exists: {}", name)
            return
        }

        val account = accountRepository.save(Account(name = name, type = type))
        logger.info("Seeded account: name={}, type={}, id={}", name, type, account.id)
    }
}