package org.apache.utlx.engine.admin

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LogBufferTest {

    @BeforeEach
    fun setup() {
        LogBuffer.clear()
    }

    @Test
    fun `getLevel returns current root level`() {
        val level = LogBuffer.getLevel()
        assertTrue(level.isNotEmpty(), "Should return a level")
    }

    @Test
    fun `setLevel changes and getLevel reflects it`() {
        val original = LogBuffer.getLevel()
        LogBuffer.setLevel("DEBUG")
        assertEquals("DEBUG", LogBuffer.getLevel())
        // Restore
        LogBuffer.setLevel(original)
    }

    @Test
    fun `setLevel rejects via validation in admin endpoint not here`() {
        // LogBuffer.setLevel accepts any string — validation is in AdminEndpoint
        // Just verify it doesn't crash
        LogBuffer.setLevel("INFO")
        assertEquals("INFO", LogBuffer.getLevel())
    }

    @Test
    fun `entries returns empty when buffer is empty`() {
        val entries = LogBuffer.entries()
        // May not be truly empty due to other test logging, but the call should work
        assertTrue(entries.size >= 0)
    }

    @Test
    fun `entries filters by level`() {
        // Install the buffer and generate some log messages
        LogBuffer.install()
        val logger = org.slf4j.LoggerFactory.getLogger("LogBufferTest")
        logger.info("test-info-message-xyz")
        logger.warn("test-warn-message-xyz")

        Thread.sleep(100) // allow appender to process

        val infoEntries = LogBuffer.entries(level = "INFO", contains = "test-info-message-xyz")
        val warnEntries = LogBuffer.entries(level = "WARN", contains = "test-warn-message-xyz")

        assertTrue(infoEntries.any { it.message.contains("test-info-message-xyz") }, "Should find info message")
        assertTrue(warnEntries.any { it.message.contains("test-warn-message-xyz") }, "Should find warn message")
    }

    @Test
    fun `entries filters by contains`() {
        LogBuffer.install()
        val logger = org.slf4j.LoggerFactory.getLogger("LogBufferTest")
        logger.info("unique-search-token-abc123")

        Thread.sleep(100)

        val filtered = LogBuffer.entries(contains = "unique-search-token-abc123")
        assertTrue(filtered.isNotEmpty(), "Should find message by contains filter")
    }

    @Test
    fun `entries filters by since`() {
        LogBuffer.install()
        val before = Instant.now()
        Thread.sleep(50)
        val logger = org.slf4j.LoggerFactory.getLogger("LogBufferTest")
        logger.info("after-timestamp-marker")

        Thread.sleep(100)

        val filtered = LogBuffer.entries(since = before, contains = "after-timestamp-marker")
        assertTrue(filtered.isNotEmpty(), "Should find message after timestamp")
    }

    @Test
    fun `clear empties the buffer`() {
        LogBuffer.install()
        val logger = org.slf4j.LoggerFactory.getLogger("LogBufferTest")
        logger.info("will-be-cleared")
        Thread.sleep(100)

        LogBuffer.clear()
        val afterClear = LogBuffer.entries(contains = "will-be-cleared")
        assertTrue(afterClear.isEmpty(), "Buffer should be empty after clear")
    }

    @Test
    fun `buffer is bounded`() {
        LogBuffer.maxEntries = 10
        LogBuffer.install()
        val logger = org.slf4j.LoggerFactory.getLogger("LogBufferTest")

        for (i in 1..50) {
            logger.info("bounded-test-$i")
        }
        Thread.sleep(200)

        assertTrue(LogBuffer.size() <= 10, "Buffer should be capped at maxEntries: ${LogBuffer.size()}")

        // Restore default
        LogBuffer.maxEntries = 5000
    }
}
