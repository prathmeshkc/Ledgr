package com.prachaudhari.ledgr.repository

import com.prachaudhari.ledgr.domain.model.Account
import com.prachaudhari.ledgr.domain.model.AccountType
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface AccountRepository : JpaRepository<Account, UUID> {

    // Returns all accounts of a given type (e.g., all SYSTEM accounts for internal holds/fees)
    fun findByType(type: AccountType): List<Account>

    // Looks up an account by name (e.g., "Hold Account") — used during seed data and settlement
    fun findByName(name: String): Account?
}