package org.apache.utlx.engine.config

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MessagingEndpointTest {

    // ── resourceName ──

    @Test
    fun `queue endpoint returns queue name as resourceName`() {
        val ep = MessagingEndpoint(queue = "orders-in")
        assertEquals("orders-in", ep.resourceName)
    }

    @Test
    fun `topic endpoint returns topic name as resourceName`() {
        val ep = MessagingEndpoint(topic = "incoming-orders", subscription = "utlxe")
        assertEquals("incoming-orders", ep.resourceName)
    }

    @Test
    fun `eventhub endpoint returns eventhub name as resourceName`() {
        val ep = MessagingEndpoint(eventhub = "telemetry-stream")
        assertEquals("telemetry-stream", ep.resourceName)
    }

    @Test
    fun `empty endpoint returns null resourceName`() {
        val ep = MessagingEndpoint()
        assertNull(ep.resourceName)
    }

    // ── daprComponentType ──

    @Test
    fun `queue maps to bindings azure servicebusqueues`() {
        val ep = MessagingEndpoint(queue = "orders-in")
        assertEquals("bindings.azure.servicebusqueues", ep.daprComponentType)
    }

    @Test
    fun `topic maps to pubsub azure servicebus topics`() {
        val ep = MessagingEndpoint(topic = "incoming-orders")
        assertEquals("pubsub.azure.servicebus.topics", ep.daprComponentType)
    }

    @Test
    fun `eventhub without consumerGroup maps to bindings azure eventhubs`() {
        val ep = MessagingEndpoint(eventhub = "telemetry")
        assertEquals("bindings.azure.eventhubs", ep.daprComponentType)
    }

    @Test
    fun `eventhub with consumerGroup maps to pubsub azure eventhubs`() {
        val ep = MessagingEndpoint(eventhub = "telemetry", consumerGroup = "utlxe")
        assertEquals("pubsub.azure.eventhubs", ep.daprComponentType)
    }

    @Test
    fun `empty endpoint returns null daprComponentType`() {
        val ep = MessagingEndpoint()
        assertNull(ep.daprComponentType)
    }

    // ── isPubSub ──

    @Test
    fun `queue is not pubsub`() {
        assertFalse(MessagingEndpoint(queue = "q").isPubSub)
    }

    @Test
    fun `topic is pubsub`() {
        assertTrue(MessagingEndpoint(topic = "t").isPubSub)
    }

    @Test
    fun `eventhub without consumerGroup is not pubsub`() {
        assertFalse(MessagingEndpoint(eventhub = "eh").isPubSub)
    }

    @Test
    fun `eventhub with consumerGroup is pubsub`() {
        assertTrue(MessagingEndpoint(eventhub = "eh", consumerGroup = "cg").isPubSub)
    }

    // ── toString ──

    @Test
    fun `toString for queue`() {
        assertEquals("queue:orders-in", MessagingEndpoint(queue = "orders-in").toString())
    }

    @Test
    fun `toString for topic with subscription`() {
        assertEquals("topic:orders sub:utlxe",
            MessagingEndpoint(topic = "orders", subscription = "utlxe").toString())
    }

    @Test
    fun `toString for eventhub with consumerGroup`() {
        assertEquals("eventhub:telemetry cg:utlxe",
            MessagingEndpoint(eventhub = "telemetry", consumerGroup = "utlxe").toString())
    }

    @Test
    fun `toString for empty`() {
        assertEquals("none", MessagingEndpoint().toString())
    }
}
