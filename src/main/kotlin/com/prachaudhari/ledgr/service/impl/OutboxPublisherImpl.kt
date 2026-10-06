package com.prachaudhari.ledgr.service.impl

import com.prachaudhari.ledgr.repository.OutboxEventRepository
import com.prachaudhari.ledgr.service.OutboxPublisher
import org.slf4j.LoggerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class OutboxPublisherImpl(
    private val outboxEventRepository: OutboxEventRepository,
    private val kafkaTemplate: KafkaTemplate<String, String>
) : OutboxPublisher {

    private val logger = LoggerFactory.getLogger(OutboxPublisherImpl::class.java)

    /**
     * Runs every 5 seconds. Fetches all unpublished outbox events in creation
     * order, publishes each to Kafka, and marks them as published.
     *
     * Each event is published with the aggregate ID (payment ID) as the Kafka
     * message key. This ensures all events for the same payment land in the
     * same Kafka partition, preserving ordering per payment.
     */
    @Scheduled(fixedDelay = 5000)
    @Transactional
    override fun pollAndPublish() {
        val events = outboxEventRepository.findByPublishedAtIsNullOrderByCreatedAtAsc()

        for (event in events) {
            kafkaTemplate.send("payment-events", event.aggregateId.toString(), event.payload)
            event.markPublished()
            outboxEventRepository.save(event)
            logger.info("Published outbox event: type={}, aggregateId={}", event.eventType, event.aggregateId)
        }
    }
}