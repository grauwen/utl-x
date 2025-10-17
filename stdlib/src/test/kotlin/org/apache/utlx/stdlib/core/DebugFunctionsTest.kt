package org.apache.utlx.stdlib.core

import org.apache.utlx.core.udm.UDM
import org.apache.utlx.stdlib.FunctionArgumentException
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DebugFunctionsTest {

    @BeforeEach
    fun setUp() {
        // Reset debug state before each test
        DebugFunctions.clearLogs(listOf())
        DebugFunctions.setLogLevel(listOf(UDM.Scalar("INFO")))
        DebugFunctions.setConsoleLogging(listOf(UDM.Scalar(false))) // Disable console during tests
    }

    // ==================== LOGGING CONFIGURATION TESTS ====================

    @Test
    fun testSetLogLevel() {
        val result1 = DebugFunctions.setLogLevel(listOf(UDM.Scalar("DEBUG")))
        assertEquals(true, (result1 as UDM.Scalar).value)
        
        val result2 = DebugFunctions.setLogLevel(listOf(UDM.Scalar("TRACE")))
        assertEquals(true, (result2 as UDM.Scalar).value)
        
        val result3 = DebugFunctions.setLogLevel(listOf(UDM.Scalar("ERROR")))
        assertEquals(true, (result3 as UDM.Scalar).value)
        
        // Invalid level
        val result4 = DebugFunctions.setLogLevel(listOf(UDM.Scalar("INVALID")))
        assertEquals(false, (result4 as UDM.Scalar).value)
    }

    @Test
    fun testSetLogLevelCaseInsensitive() {
        val result1 = DebugFunctions.setLogLevel(listOf(UDM.Scalar("debug")))
        assertEquals(true, (result1 as UDM.Scalar).value)
        
        val result2 = DebugFunctions.setLogLevel(listOf(UDM.Scalar("Info")))
        assertEquals(true, (result2 as UDM.Scalar).value)
        
        val result3 = DebugFunctions.setLogLevel(listOf(UDM.Scalar("WARN")))
        assertEquals(true, (result3 as UDM.Scalar).value)
    }

    @Test
    fun testSetConsoleLogging() {
        // Test enabling
        val result1 = DebugFunctions.setConsoleLogging(listOf(UDM.Scalar(true)))
        assertEquals(false, (result1 as UDM.Scalar).value) // Previous state was false
        
        // Test disabling
        val result2 = DebugFunctions.setConsoleLogging(listOf(UDM.Scalar(false)))
        assertEquals(true, (result2 as UDM.Scalar).value) // Previous state was true
        
        // Test with number (non-zero = true)
        val result3 = DebugFunctions.setConsoleLogging(listOf(UDM.Scalar(1)))
        assertEquals(false, (result3 as UDM.Scalar).value)
        
        // Test with number (zero = false)
        val result4 = DebugFunctions.setConsoleLogging(listOf(UDM.Scalar(0)))
        assertEquals(true, (result4 as UDM.Scalar).value)
        
        // Test with string
        val result5 = DebugFunctions.setConsoleLogging(listOf(UDM.Scalar("true")))
        assertEquals(false, (result5 as UDM.Scalar).value)
        
        val result6 = DebugFunctions.setConsoleLogging(listOf(UDM.Scalar("false")))
        assertEquals(true, (result6 as UDM.Scalar).value)
    }

    // ==================== BASIC LOGGING TESTS ====================

    @Test
    fun testLog() {
        val data = UDM.Scalar("test data")
        val result = DebugFunctions.log(listOf(data))
        
        // Should return the original data (passthrough)
        assertEquals(data, result)
        
        // Should have created a log entry
        val logCount = DebugFunctions.logCount(listOf())
        assertEquals(1.0, (logCount as UDM.Scalar).value)
    }

    @Test
    fun testLogWithMessage() {
        val data = UDM.Scalar("test data")
        val message = UDM.Scalar("Processing:")
        val result = DebugFunctions.log(listOf(data, message))
        
        assertEquals(data, result)
        
        val logs = DebugFunctions.getLogs(listOf()) as UDM.Array
        assertEquals(1, logs.elements.size)
        
        val logEntry = logs.elements[0] as UDM.Object
        assertEquals("Processing:", (logEntry.properties["message"] as UDM.Scalar).value)
    }

    @Test
    fun testTrace() {
        DebugFunctions.setLogLevel(listOf(UDM.Scalar("TRACE")))
        
        val data = UDM.Scalar("trace data")
        val result = DebugFunctions.trace(listOf(UDM.Scalar("Trace message"), data))
        
        assertEquals(data, result)
        
        val logs = DebugFunctions.getLogs(listOf()) as UDM.Array
        assertEquals(1, logs.elements.size)
        
        val logEntry = logs.elements[0] as UDM.Object
        assertEquals("TRACE", (logEntry.properties["level"] as UDM.Scalar).value)
        assertEquals("Trace message", (logEntry.properties["message"] as UDM.Scalar).value)
    }

    @Test
    fun testDebug() {
        DebugFunctions.setLogLevel(listOf(UDM.Scalar("DEBUG")))
        
        val data = UDM.Scalar("debug data")
        val result = DebugFunctions.debug(listOf(UDM.Scalar("Debug message"), data))
        
        assertEquals(data, result)
        
        val logs = DebugFunctions.getLogs(listOf()) as UDM.Array
        assertEquals(1, logs.elements.size)
        
        val logEntry = logs.elements[0] as UDM.Object
        assertEquals("DEBUG", (logEntry.properties["level"] as UDM.Scalar).value)
    }

    @Test
    fun testInfo() {
        val data = UDM.Scalar("info data")
        val result = DebugFunctions.info(listOf(UDM.Scalar("Info message"), data))
        
        assertEquals(data, result)
        
        val logs = DebugFunctions.getLogs(listOf()) as UDM.Array
        assertEquals(1, logs.elements.size)
        
        val logEntry = logs.elements[0] as UDM.Object
        assertEquals("INFO", (logEntry.properties["level"] as UDM.Scalar).value)
    }

    @Test
    fun testWarn() {
        val data = UDM.Scalar("warn data")
        val result = DebugFunctions.warn(listOf(UDM.Scalar("Warning message"), data))
        
        assertEquals(data, result)
        
        val logs = DebugFunctions.getLogs(listOf()) as UDM.Array
        assertEquals(1, logs.elements.size)
        
        val logEntry = logs.elements[0] as UDM.Object
        assertEquals("WARN", (logEntry.properties["level"] as UDM.Scalar).value)
    }

    @Test
    fun testError() {
        val data = UDM.Scalar("error data")
        val result = DebugFunctions.error(listOf(UDM.Scalar("Error message"), data))
        
        assertEquals(data, result)
        
        val logs = DebugFunctions.getLogs(listOf()) as UDM.Array
        assertEquals(1, logs.elements.size)
        
        val logEntry = logs.elements[0] as UDM.Object
        assertEquals("ERROR", (logEntry.properties["level"] as UDM.Scalar).value)
    }

    @Test
    fun testLogLevelFiltering() {
        DebugFunctions.setLogLevel(listOf(UDM.Scalar("WARN")))
        
        // These should not be logged
        DebugFunctions.trace(listOf(UDM.Scalar("Trace message")))
        DebugFunctions.debug(listOf(UDM.Scalar("Debug message")))
        DebugFunctions.info(listOf(UDM.Scalar("Info message")))
        
        // These should be logged
        DebugFunctions.warn(listOf(UDM.Scalar("Warning message")))
        DebugFunctions.error(listOf(UDM.Scalar("Error message")))
        
        val logCount = DebugFunctions.logCount(listOf())
        assertEquals(2.0, (logCount as UDM.Scalar).value) // Only WARN and ERROR
    }

    // ==================== INSPECTION FUNCTION TESTS ====================

    @Test
    fun testLogType() {
        val data = UDM.Array(listOf(UDM.Scalar(1), UDM.Scalar(2)))
        val result = DebugFunctions.logType(listOf(data, UDM.Scalar("Array type:")))
        
        assertEquals(data, result)
        
        val logs = DebugFunctions.getLogs(listOf()) as UDM.Array
        assertEquals(1, logs.elements.size)
        
        val logEntry = logs.elements[0] as UDM.Object
        val message = (logEntry.properties["message"] as UDM.Scalar).value as String
        assertTrue(message.contains("Array type: Array"))
    }

    @Test
    fun testLogTypeVariousTypes() {
        DebugFunctions.logType(listOf(UDM.Scalar("hello")))
        DebugFunctions.logType(listOf(UDM.Scalar(42)))
        DebugFunctions.logType(listOf(UDM.Scalar(true)))
        DebugFunctions.logType(listOf(UDM.Scalar(null)))
        DebugFunctions.logType(listOf(UDM.Object(emptyMap(), emptyMap())))
        
        val logs = DebugFunctions.getLogs(listOf()) as UDM.Array
        assertEquals(5, logs.elements.size)
        
        val messages = logs.elements.map { entry ->
            ((entry as UDM.Object).properties["message"] as UDM.Scalar).value as String
        }
        
        assertTrue(messages.any { it.contains("String") })
        assertTrue(messages.any { it.contains("Number") })
        assertTrue(messages.any { it.contains("Boolean") })
        assertTrue(messages.any { it.contains("Null") })
        assertTrue(messages.any { it.contains("Object") })
    }

    @Test
    fun testLogSize() {
        val array = UDM.Array(listOf(UDM.Scalar(1), UDM.Scalar(2), UDM.Scalar(3)))
        val result = DebugFunctions.logSize(listOf(array, UDM.Scalar("Array size:")))
        
        assertEquals(array, result)
        
        val logs = DebugFunctions.getLogs(listOf()) as UDM.Array
        val logEntry = logs.elements[0] as UDM.Object
        val message = (logEntry.properties["message"] as UDM.Scalar).value as String
        assertTrue(message.contains("Array size: 3"))
    }

    @Test
    fun testLogSizeString() {
        val str = UDM.Scalar("hello")
        DebugFunctions.logSize(listOf(str))
        
        val logs = DebugFunctions.getLogs(listOf()) as UDM.Array
        val logEntry = logs.elements[0] as UDM.Object
        val message = (logEntry.properties["message"] as UDM.Scalar).value as String
        assertTrue(message.contains("Size: 5"))
    }

    @Test
    fun testLogSizeObject() {
        val obj = UDM.Object(mapOf(
            "a" to UDM.Scalar(1),
            "b" to UDM.Scalar(2)
        ), emptyMap())
        DebugFunctions.logSize(listOf(obj))
        
        val logs = DebugFunctions.getLogs(listOf()) as UDM.Array
        val logEntry = logs.elements[0] as UDM.Object
        val message = (logEntry.properties["message"] as UDM.Scalar).value as String
        assertTrue(message.contains("Size: 2"))
    }

    @Test
    fun testLogPretty() {
        val obj = UDM.Object(mapOf(
            "name" to UDM.Scalar("John"),
            "age" to UDM.Scalar(30)
        ), emptyMap())
        
        val result = DebugFunctions.logPretty(listOf(obj, UDM.Scalar("User object:")))
        
        assertEquals(obj, result)
        
        val logs = DebugFunctions.getLogs(listOf()) as UDM.Array
        val logEntry = logs.elements[0] as UDM.Object
        val message = (logEntry.properties["message"] as UDM.Scalar).value as String
        
        assertTrue(message.contains("User object:"))
        assertTrue(message.contains("name"))
        assertTrue(message.contains("John"))
        assertTrue(message.contains("age"))
        assertTrue(message.contains("30"))
    }

    // ==================== TIMER TESTS ====================

    @Test
    fun testStartTimer() {
        val timer = DebugFunctions.startTimer(listOf(UDM.Scalar("Test operation")))
        
        assertTrue(timer is UDM.Object)
        val timerObj = timer as UDM.Object
        assertTrue(timerObj.properties.containsKey("label"))
        assertTrue(timerObj.properties.containsKey("startTime"))
        
        assertEquals("Test operation", (timerObj.properties["label"] as UDM.Scalar).value)
        assertTrue((timerObj.properties["startTime"] as UDM.Scalar).value is Double)
    }

    @Test
    fun testEndTimer() {
        val timer = DebugFunctions.startTimer(listOf(UDM.Scalar("Test operation")))
        
        Thread.sleep(10) // Small delay to ensure measurable time
        
        val elapsedTime = DebugFunctions.endTimer(listOf(timer))
        
        assertTrue(elapsedTime is UDM.Scalar)
        val elapsed = (elapsedTime as UDM.Scalar).value as Double
        assertTrue(elapsed >= 0.0) // Should have some positive elapsed time
        
        val logs = DebugFunctions.getLogs(listOf()) as UDM.Array
        assertEquals(1, logs.elements.size)
        
        val logEntry = logs.elements[0] as UDM.Object
        val message = (logEntry.properties["message"] as UDM.Scalar).value as String
        assertTrue(message.contains("Test operation:"))
        assertTrue(message.contains("ms"))
    }

    @Test
    fun testTimerWithoutLabel() {
        val timer = DebugFunctions.startTimer(listOf())
        val elapsed = DebugFunctions.endTimer(listOf(timer))
        
        assertTrue(elapsed is UDM.Scalar)
        val logs = DebugFunctions.getLogs(listOf()) as UDM.Array
        val logEntry = logs.elements[0] as UDM.Object
        val message = (logEntry.properties["message"] as UDM.Scalar).value as String
        assertTrue(message.contains("Timer:"))
    }

    // ==================== LOG MANAGEMENT TESTS ====================

    @Test
    fun testGetLogs() {
        DebugFunctions.info(listOf(UDM.Scalar("First message")))
        DebugFunctions.warn(listOf(UDM.Scalar("Second message")))
        
        val logs = DebugFunctions.getLogs(listOf())
        
        assertTrue(logs is UDM.Array)
        val logArray = logs as UDM.Array
        assertEquals(2, logArray.elements.size)
        
        val firstLog = logArray.elements[0] as UDM.Object
        assertTrue(firstLog.properties.containsKey("timestamp"))
        assertTrue(firstLog.properties.containsKey("level"))
        assertTrue(firstLog.properties.containsKey("message"))
        assertTrue(firstLog.properties.containsKey("data"))
        
        assertEquals("INFO", (firstLog.properties["level"] as UDM.Scalar).value)
        assertEquals("First message", (firstLog.properties["message"] as UDM.Scalar).value)
    }

    @Test
    fun testClearLogs() {
        DebugFunctions.info(listOf(UDM.Scalar("Message 1")))
        DebugFunctions.info(listOf(UDM.Scalar("Message 2")))
        DebugFunctions.info(listOf(UDM.Scalar("Message 3")))
        
        val cleared = DebugFunctions.clearLogs(listOf())
        assertEquals(3.0, (cleared as UDM.Scalar).value)
        
        val logCount = DebugFunctions.logCount(listOf())
        assertEquals(0.0, (logCount as UDM.Scalar).value)
        
        val logs = DebugFunctions.getLogs(listOf()) as UDM.Array
        assertEquals(0, logs.elements.size)
    }

    @Test
    fun testLogCount() {
        val initialCount = DebugFunctions.logCount(listOf())
        assertEquals(0.0, (initialCount as UDM.Scalar).value)
        
        DebugFunctions.info(listOf(UDM.Scalar("Message 1")))
        val count1 = DebugFunctions.logCount(listOf())
        assertEquals(1.0, (count1 as UDM.Scalar).value)
        
        DebugFunctions.warn(listOf(UDM.Scalar("Message 2")))
        val count2 = DebugFunctions.logCount(listOf())
        assertEquals(2.0, (count2 as UDM.Scalar).value)
    }

    // ==================== ASSERTION TESTS ====================

    @Test
    fun testAssertTrue() {
        val result = DebugFunctions.assert(listOf(UDM.Scalar(true), UDM.Scalar("Should pass")))
        
        assertEquals(true, (result as UDM.Scalar).value)
        
        // Should not create error log for passing assertion
        val logs = DebugFunctions.getLogs(listOf()) as UDM.Array
        assertEquals(0, logs.elements.size) // No logs for passing assertion
    }

    @Test
    fun testAssertFalse() {
        val result = DebugFunctions.assert(listOf(UDM.Scalar(false), UDM.Scalar("Should fail")))
        
        assertEquals(false, (result as UDM.Scalar).value)
        
        // Should create error log for failing assertion
        val logs = DebugFunctions.getLogs(listOf()) as UDM.Array
        assertEquals(1, logs.elements.size)
        
        val logEntry = logs.elements[0] as UDM.Object
        assertEquals("ERROR", (logEntry.properties["level"] as UDM.Scalar).value)
        val message = (logEntry.properties["message"] as UDM.Scalar).value as String
        assertTrue(message.contains("ASSERTION FAILED"))
        assertTrue(message.contains("Should fail"))
    }

    @Test
    fun testAssertEqual() {
        val result1 = DebugFunctions.assertEqual(listOf(UDM.Scalar(42), UDM.Scalar(42)))
        assertEquals(true, (result1 as UDM.Scalar).value)
        
        val result2 = DebugFunctions.assertEqual(listOf(UDM.Scalar(42), UDM.Scalar(43), UDM.Scalar("Numbers should match")))
        assertEquals(false, (result2 as UDM.Scalar).value)
        
        val logs = DebugFunctions.getLogs(listOf()) as UDM.Array
        assertEquals(1, logs.elements.size) // Only the failing assertion creates a log
        
        val logEntry = logs.elements[0] as UDM.Object
        val message = (logEntry.properties["message"] as UDM.Scalar).value as String
        assertTrue(message.contains("ASSERTION FAILED"))
        assertTrue(message.contains("Numbers should match"))
        assertTrue(message.contains("Expected"))
        assertTrue(message.contains("Actual"))
    }

    // ==================== ERROR HANDLING TESTS ====================

    @Test
    fun testSetLogLevelInvalidArguments() {
        assertThrows<FunctionArgumentException> {
            DebugFunctions.setLogLevel(listOf())
        }
    }

    @Test
    fun testSetConsoleLoggingInvalidArguments() {
        assertThrows<FunctionArgumentException> {
            DebugFunctions.setConsoleLogging(listOf())
        }
    }

    @Test
    fun testLogInvalidArguments() {
        assertThrows<FunctionArgumentException> {
            DebugFunctions.log(listOf())
        }
    }

    @Test
    fun testTraceInvalidArguments() {
        assertThrows<FunctionArgumentException> {
            DebugFunctions.trace(listOf())
        }
    }

    @Test
    fun testEndTimerInvalidArguments() {
        assertThrows<FunctionArgumentException> {
            DebugFunctions.endTimer(listOf())
        }
    }

    @Test
    fun testAssertInvalidArguments() {
        assertThrows<FunctionArgumentException> {
            DebugFunctions.assert(listOf())
        }
    }

    @Test
    fun testAssertEqualInvalidArguments() {
        assertThrows<FunctionArgumentException> {
            DebugFunctions.assertEqual(listOf(UDM.Scalar(42)))
        }
    }

    // ==================== EDGE CASES ====================

    @Test
    fun testLogWithComplexData() {
        val complexData = UDM.Object(mapOf(
            "array" to UDM.Array(listOf(UDM.Scalar(1), UDM.Scalar(2))),
            "nested" to UDM.Object(mapOf("inner" to UDM.Scalar("value")), emptyMap())
        ), emptyMap())
        
        val result = DebugFunctions.log(listOf(complexData))
        assertEquals(complexData, result)
        
        val logs = DebugFunctions.getLogs(listOf()) as UDM.Array
        assertEquals(1, logs.elements.size)
    }

    @Test
    fun testEndTimerWithInvalidTimer() {
        val invalidTimer = UDM.Scalar("not a timer")
        val result = DebugFunctions.endTimer(listOf(invalidTimer))
        
        assertEquals(0.0, (result as UDM.Scalar).value)
    }

    @Test
    fun testLogPrettyWithEmptyStructures() {
        val emptyArray = UDM.Array(emptyList())
        DebugFunctions.logPretty(listOf(emptyArray))
        
        val emptyObject = UDM.Object(emptyMap(), emptyMap())
        DebugFunctions.logPretty(listOf(emptyObject))
        
        val logs = DebugFunctions.getLogs(listOf()) as UDM.Array
        assertEquals(2, logs.elements.size)
        
        val firstMessage = ((logs.elements[0] as UDM.Object).properties["message"] as UDM.Scalar).value as String
        val secondMessage = ((logs.elements[1] as UDM.Object).properties["message"] as UDM.Scalar).value as String
        
        assertTrue(firstMessage.contains("[]"))
        assertTrue(secondMessage.contains("{}"))
    }

    @Test
    fun testAssertWithNonBooleanCondition() {
        // Non-boolean condition should be treated as false
        val result = DebugFunctions.assert(listOf(UDM.Scalar("not boolean")))
        assertEquals(false, (result as UDM.Scalar).value)
        
        val logs = DebugFunctions.getLogs(listOf()) as UDM.Array
        assertEquals(1, logs.elements.size) // Should create error log
    }
}