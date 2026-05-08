package org.apache.utlx.engine.admin

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.AppenderBase
import org.slf4j.LoggerFactory
import java.time.Instant
import java.util.concurrent.ConcurrentLinkedDeque
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * In-memory log ring buffer for Admin API log access.
 *
 * Custom Logback appender that stores recent log entries in a bounded deque.
 * Zero I/O — append is O(1). Accessible via GET /admin/logs.
 *
 * Usage: call LogBuffer.install() at startup to attach to the root logger.
 */
object LogBuffer {

    private val buffer = ConcurrentLinkedDeque<LogEntry>()
    private val bufferSize = AtomicInteger(0)
    private const val DEFAULT_MAX_ENTRIES = 5000

    @Volatile
    var maxEntries: Int = DEFAULT_MAX_ENTRIES

    @Volatile
    private var revertScheduled = false

    private val scheduler = Executors.newSingleThreadScheduledExecutor { r ->
        Thread(r, "log-level-revert").apply { isDaemon = true }
    }

    /**
     * Install the ring buffer appender on the Logback root logger.
     */
    fun install() {
        val rootLogger = LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME) as Logger
        val appender = RingBufferAppender()
        appender.context = rootLogger.loggerContext
        appender.start()
        rootLogger.addAppender(appender)
    }

    /**
     * Get recent log entries, newest first.
     */
    fun entries(
        limit: Int = 100,
        level: String? = null,
        contains: String? = null,
        since: Instant? = null
    ): List<LogEntry> {
        var stream = buffer.asSequence()
        if (level != null) {
            val lvl = level.uppercase()
            stream = stream.filter { it.level == lvl }
        }
        if (contains != null) {
            stream = stream.filter { it.message.contains(contains, ignoreCase = true) }
        }
        if (since != null) {
            stream = stream.filter { it.timestamp >= since }
        }
        return stream.take(limit).toList()
    }

    /**
     * Get current root log level.
     */
    fun getLevel(): String {
        val rootLogger = LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME) as Logger
        return rootLogger.level?.toString() ?: "INFO"
    }

    /**
     * Set root log level. Optionally auto-revert after N minutes.
     */
    fun setLevel(newLevel: String, revertAfterMinutes: Int? = null) {
        val rootLogger = LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME) as Logger
        val previousLevel = rootLogger.level?.toString() ?: "INFO"
        rootLogger.level = Level.toLevel(newLevel, Level.INFO)

        val logger = LoggerFactory.getLogger(LogBuffer::class.java)
        logger.info("Log level changed: {} → {} {}", previousLevel, newLevel,
            revertAfterMinutes?.let { "(auto-revert to $previousLevel in ${it}m)" } ?: "")

        if (revertAfterMinutes != null && revertAfterMinutes > 0) {
            scheduler.schedule({
                rootLogger.level = Level.toLevel(previousLevel, Level.INFO)
                logger.info("Log level auto-reverted to {}", previousLevel)
                revertScheduled = false
            }, revertAfterMinutes.toLong(), TimeUnit.MINUTES)
            revertScheduled = true
        }
    }

    /**
     * Clear the log buffer.
     */
    fun clear() {
        buffer.clear()
        bufferSize.set(0)
    }

    /**
     * Current buffer size. O(1) via atomic counter.
     */
    fun size(): Int = bufferSize.get()

    /**
     * Logback appender that writes to the in-memory ring buffer.
     */
    private class RingBufferAppender : AppenderBase<ILoggingEvent>() {
        override fun append(event: ILoggingEvent) {
            buffer.addFirst(LogEntry(
                timestamp = Instant.ofEpochMilli(event.timeStamp),
                level = event.level.toString(),
                logger = event.loggerName.substringAfterLast('.'),
                message = event.formattedMessage,
                thread = event.threadName
            ))
            // Cap the buffer — O(1) check via atomic counter
            val currentSize = bufferSize.incrementAndGet()
            if (currentSize > maxEntries) {
                buffer.pollLast()
                bufferSize.decrementAndGet()
            }
        }
    }
}

data class LogEntry(
    val timestamp: Instant,
    val level: String,
    val logger: String,
    val message: String,
    val thread: String
)
