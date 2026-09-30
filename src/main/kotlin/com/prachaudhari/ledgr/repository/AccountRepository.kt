package com.prachaudhari.ledgr.repository

import com.prachaudhari.ledgr.domain.model.Account
import com.prachaudhari.ledgr.domain.model.AccountType
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface AccountRepository : JpaRepository<Account, UUID> {
    fun findByType(type: AccountType): List<Account>
    fun findByName(name: String): Account?
}