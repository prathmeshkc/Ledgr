package com.prachaudhari.ledgr.consumer

import org.apache.kafka.clients.consumer.ConsumerRecord
import org.slf4j.LoggerFactory
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component

/**
 * Kafka consumer that listens to the 'payment-events' topic.
 *
 * In Phase 1, this is a simple event logger that proves the end-to-end
 * pipeline works: payment state change → outbox write → outbox poller
 * publishes to Kafka → this consumer receives and logs it.
 *
 * In a production system, this is where downstream side effects would
 * be triggered: sending notification emails, updating analytics dashboards,
 * syncing with external accounting systems, triggering fraud checks, etc.
 *
 * The consumer uses the payment ID as the Kafka message key, which means
 * all events for the same payment arrive in order within the same partition.
 * This guarantees that a consumer processing events for a single payment
 * will see CREATED before AUTHORIZED before SETTLED.
 *
 * Delivery guarantee: the outbox pattern provides at-least-once delivery,
 * so this consumer may see duplicate events. In Phase 2, consumers should
 * be idempotent (e.g., check if the event was already processed before
 * taking action).
 */
@Component
class PaymentEventConsumer {

    private val logger = LoggerFactory.getLogger(PaymentEventConsumer::class.java)

    /**
     * Receives payment events from the 'payment-events' Kafka topic.
     *
     * The message key is the payment ID (UUID string), and the value is
     * the full PaymentEvent serialized as JSON.
     *
     * @param record The Kafka consumer record containing the event payload
     */
    @KafkaListener(topics = ["payment-events"], groupId = "ledgr-group")
    fun consume(record: ConsumerRecord<String, String>) {
        logger.info(
            "Received payment event: key={}, partition={}, offset={}, payload={}",
            record.key(),
            record.partition(),
            record.offset(),
            record.value()
        )
    }
}