package org.apache.utlx.engine.config

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.kotlinModule
import com.fasterxml.jackson.module.kotlin.readValue
import java.nio.file.Path

data class TransformConfig(
    val strategy: String = "TEMPLATE",
    val validationPolicy: String = "SKIP",
    val inputs: List<InputSlot> = emptyList(),
    val output: OutputSlot = OutputSlot(),
    val maxConcurrent: Int = 0,   // EF21: 0 = unlimited, >0 = max in-flight executions before 503
    val maxInputSize: String? = null,   // Per-transformation max input size (e.g., "100KB", "25MB"). Null = use engine default.
    val outputBinding: String? = null,  // Dapr output binding name (legacy, prefer messaging.output)
    val input: MessagingEndpoint? = null,   // EF10: messaging input (queue/topic/eventhub)
    @com.fasterxml.jackson.annotation.JsonProperty("output_messaging")
    val outputMessaging: MessagingEndpoint? = null  // EF10: messaging output (queue/topic/eventhub)
) {
    companion object {
        private val yamlMapper = ObjectMapper(YAMLFactory()).apply {
            registerModule(kotlinModule())
            configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        }

        fun load(path: Path): TransformConfig {
            return yamlMapper.readValue(path.toFile())
        }

        fun yamlMapper(): ObjectMapper = yamlMapper
    }
}

data class InputSlot(
    val name: String = "",
    val schema: String? = null
)

data class OutputSlot(
    val schema: String? = null
)

/**
 * Parse a human-readable size string to bytes.
 * Supports: "100KB", "5MB", "1GB", "1024", "5mb", "100 KB"
 */
fun parseSizeToBytes(size: String?): Long? {
    if (size.isNullOrBlank()) return null
    val trimmed = size.trim().uppercase()
    val match = Regex("^(\\d+)\\s*(KB|MB|GB|B)?$").matchEntire(trimmed) ?: return null
    val value = match.groupValues[1].toLong()
    return when (match.groupValues[2]) {
        "KB" -> value * 1024
        "MB" -> value * 1024 * 1024
        "GB" -> value * 1024 * 1024 * 1024
        "B", "" -> value
        else -> value
    }
}

/**
 * EF10: Messaging endpoint declaration.
 * Exactly one of queue/topic/eventhub should be set. The field name IS the discriminator.
 */
data class MessagingEndpoint(
    val queue: String? = null,              // Service Bus queue → bindings.azure.servicebusqueues
    val topic: String? = null,              // Service Bus topic → pubsub.azure.servicebus.topics
    val eventhub: String? = null,           // Event Hub → bindings.azure.eventhubs (or pubsub if consumerGroup set)
    val subscription: String? = null,       // Required for topic input (Service Bus subscription name)
    val consumerGroup: String? = null       // Optional for eventhub input (triggers pub/sub mode)
) {
    /** The resource name (queue name, topic name, or eventhub name). */
    val resourceName: String?
        get() = queue ?: topic ?: eventhub

    /** The Dapr component type for this endpoint. */
    val daprComponentType: String?
        get() = when {
            queue != null -> "bindings.azure.servicebusqueues"
            topic != null -> "pubsub.azure.servicebus.topics"
            eventhub != null && consumerGroup != null -> "pubsub.azure.eventhubs"
            eventhub != null -> "bindings.azure.eventhubs"
            else -> null
        }

    /** Whether this is a pub/sub endpoint (vs binding). */
    val isPubSub: Boolean
        get() = topic != null || (eventhub != null && consumerGroup != null)

    /** Short description for logging. */
    override fun toString(): String = when {
        queue != null -> "queue:$queue"
        topic != null -> "topic:$topic" + (subscription?.let { " sub:$it" } ?: "")
        eventhub != null -> "eventhub:$eventhub" + (consumerGroup?.let { " cg:$it" } ?: "")
        else -> "none"
    }
}
