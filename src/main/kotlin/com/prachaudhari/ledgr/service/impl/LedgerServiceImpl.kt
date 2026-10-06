package com.prachaudhari.ledgr.service.impl

import com.prachaudhari.ledgr.domain.model.*
import com.prachaudhari.ledgr.repository.LedgerEntryRepository
import com.prachaudhari.ledgr.service.LedgerService
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.util.UUID

@Service
class LedgerServiceImpl(
    private val ledgerEntryRepository: LedgerEntryRepository
) : LedgerService {

    override fun writeEntries(payment: Payment, debitAccount: Account, creditAccount: Account, amount: BigDecimal) {
        val debitEntry = LedgerEntry(
            payment = payment,
            account = debitAccount,
            entryType = EntryType.DEBIT,
            amount = amount
        )
        val creditEntry = LedgerEntry(
            payment = payment,
            account = creditAccount,
            entryType = EntryType.CREDIT,
            amount = amount
        )
        ledgerEntryRepository.save(debitEntry)
        ledgerEntryRepository.save(creditEntry)
    }

    override fun getBalance(accountId: UUID): BigDecimal {
        return ledgerEntryRepository.deriveBalance(accountId)
    }

    override fun getEntriesByAccount(accountId: UUID): List<LedgerEntry> {
        return ledgerEntryRepository.findByAccountId(accountId)
    }

    override fun getEntriesByPayment(paymentId: UUID): List<LedgerEntry> {
        return ledgerEntryRepository.findByPaymentId(paymentId)
    }
}