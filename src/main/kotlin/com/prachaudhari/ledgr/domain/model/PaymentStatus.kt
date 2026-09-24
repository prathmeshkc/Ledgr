package com.prachaudhari.ledgr.domain.model

enum class PaymentStatus {
    INITIATED,
    AUTHORIZED,
    SETTLED,
    FAILED;

    fun canTransitionTo(target: PaymentStatus): Boolean = when (this) {
        INITIATED -> target == AUTHORIZED
        AUTHORIZED -> target == SETTLED || target == FAILED
        SETTLED -> false
        FAILED -> false
    }

    fun transitionTo(target: PaymentStatus): PaymentStatus {
        require(canTransitionTo(target)) {
            "Invalid state transition: $this -> $target"
        }
        return target
    }
}
