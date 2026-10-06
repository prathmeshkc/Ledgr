package com.prachaudhari.ledgr.service

/**
 * Publishes outbox events to Kafka.
 *
 * This is the "read and publish" side of the transactional outbox pattern.
 * It periodically polls the outbox_events table for rows where published_at
 * is NULL, publishes each event to the Kafka 'payment-events' topic, and
 * marks the row as published.
 *
 * Why a poller instead of publishing directly from PaymentService?
 * Because of the dual-write problem. If PaymentService wrote to both the
 * database and Kafka in the same flow, a crash between the two writes
 * would leave the system in an inconsistent state:
 * - DB committed but Kafka publish failed → ledger entry exists but no event
 * - Kafka published but DB commit failed → event exists but no ledger entry
 *
 * The outbox pattern solves this by writing the event to the database (which
 * participates in the same transaction as the ledger write), and then having
 * a separate process read and publish it. Since the outbox row and the ledger
 * entry are in the same transaction, they either both exist or neither does.
 *
 * Delivery guarantee: at-least-once. If the poller crashes after publishing
 * to Kafka but before marking the row as published, the event will be
 * republished on the next poll. Consumers must be idempotent (Phase 2).
 */
interface OutboxPublisher {

    /**
     * Polls for unpublished outbox events and publishes them to Kafka.
     *
     * This method is called on a fixed schedule (e.g., every 5 seconds).
     * For each unpublished event:
     * 1. Publish the event payload to the 'payment-events' Kafka topic
     * 2. Mark the outbox row as published (set published_at = now)
     *
     * Events are processed in creation order (oldest first) to preserve
     * the sequence of state transitions for each payment.
     */
    fun pollAndPublish()
}