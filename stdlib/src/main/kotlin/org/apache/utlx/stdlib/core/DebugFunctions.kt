// stdlib/src/main/kotlin/org/apache/utlx/stdlib/core/DebugFunctions.kt
package org.apache.utlx.stdlib.core

import org.apache.utlx.core.udm.*
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Debug and logging functions for UTL-X
 * 
 * Provides utilities for debugging transformations, logging values,
 * and inspecting data during development.
 * 
 * @since 1.0.0
 */
object DebugFunctions {
    
    // ============================================
    // LOGGING CONFIGURATION
    // ============================================
    
    enum class LogLevel {
        TRACE, DEBUG, INFO, WARN, ERROR
    }
    
    private var currentLogLevel = LogLevel.INFO
    private var logToConsole = true
    private val logBuffer = mutableListOf<LogEntry>()
    
    data class LogEntry(
        val timestamp: LocalDateTime,
        val level: LogLevel,
        val message: String,
        val data: UDM?
    )
    
    /**
     * Sets the minimum log level
     * 
     * @param level Log level: "TRACE", "DEBUG", "INFO", "WARN", "ERROR"
     * @return true if successful
     * 
     * Example:
     * ```
     * setLogLevel("DEBUG")
     * ```
     */
    fun setLogLevel(level: UDM): UDM {
        val levelStr = (level as? UDM.Scalar)?.value?.uppercase() ?: return UDM.Scalar(false)
        
        currentLogLevel = when (levelStr) {
            "TRACE" -> LogLevel.TRACE
            "DEBUG" -> LogLevel.DEBUG
            "INFO" -> LogLevel.INFO
            "WARN" -> LogLevel.WARN
            "ERROR" -> LogLevel.ERROR
            else -> return UDM.Scalar(false)
        }
        
        return UDM.Scalar(true)
    }
    
    /**
     * Enables or disables console logging
     * 
     * @param enabled true to enable, false to disable
     * @return Previous state
     * 
     * Example:
     * ```
     * setConsoleLogging(false) // Disable console output
     * ```
     */
    fun setConsoleLogging(enabled: UDM): UDM {
        val previousState = logToConsole
        logToConsole = when (val scalar = enabled as? UDM.Scalar) {
            null -> true
            else -> {
                val v = scalar.value
                when (v) {
                    is Boolean -> v
                    is Number -> v.toDouble() != 0.0
                    is String -> v.isNotEmpty() && v != "false" && v != "0"
                    null -> false
                    else -> true
                }
            }
        }
        return UDM.Scalar(previousState)
    }
    
    // ============================================
    // BASIC LOGGING
    // ============================================
    
    /**
     * Logs a value with optional message (passthrough)
     * 
     * Returns the value unchanged, allowing use in pipelines.
     * 
     * @param value The value to log and return
     * @param message Optional message prefix
     * @return The original value (passthrough)
     * 
     * Examples:
     * ```
     * log(data) // Logs and returns data
     * log(data, "Processing:") // With message
     * 
     * // In pipeline:
     * input.items 
     *   |> map(item => item.price)
     *   |> log("Prices:")
     *   |> sum()
     * ```
     */
    fun log(value: UDM, message: UDM = UDM.Scalar(null)): UDM {
        val msg = (message as? UDM.Scalar)?.value?.toString() ?: "LOG"
        logInternal(LogLevel.INFO, msg, value)
        return value // Passthrough
    }
    
    /**
     * Logs with TRACE level
     * 
     * @param message Log message
     * @param data Optional data to log
     * @return The data value (passthrough)
     * 
     * Example:
     * ```
     * trace("Entering function", input)
     * ```
     */
    fun trace(message: UDM, data: UDM = UDM.Scalar(null)): UDM {
        val msg = (message as? UDM.Scalar)?.value?.toString() ?: ""
        logInternal(LogLevel.TRACE, msg, data)
        return data
    }
    
    /**
     * Logs with DEBUG level
     * 
     * @param message Log message
     * @param data Optional data to log
     * @return The data value (passthrough)
     * 
     * Example:
     * ```
     * debug("Variable state", myVar)
     * ```
     */
    fun debug(message: UDM, data: UDM = UDM.Scalar.nullValue()): UDM {
        val msg = (message as? UDM.Scalar)?.value ?: ""
        logInternal(LogLevel.DEBUG, msg, data)
        return data
    }
    
    /**
     * Logs with INFO level
     * 
     * @param message Log message
     * @param data Optional data to log
     * @return The data value (passthrough)
     * 
     * Example:
     * ```
     * info("Processing completed", result)
     * ```
     */
    fun info(message: UDM, data: UDM = UDM.Scalar.nullValue()): UDM {
        val msg = (message as? UDM.Scalar)?.value ?: ""
        logInternal(LogLevel.INFO, msg, data)
        return data
    }
    
    /**
     * Logs with WARN level
     * 
     * @param message Log message
     * @param data Optional data to log
     * @return The data value (passthrough)
     * 
     * Example:
     * ```
     * warn("Missing optional field", fieldName)
     * ```
     */
    fun warn(message: UDM, data: UDM = UDM.Scalar.nullValue()): UDM {
        val msg = (message as? UDM.Scalar)?.value ?: ""
        logInternal(LogLevel.WARN, msg, data)
        return data
    }
    
    /**
     * Logs with ERROR level
     * 
     * @param message Log message
     * @param data Optional data to log
     * @return The data value (passthrough)
     * 
     * Example:
     * ```
     * error("Validation failed", invalidData)
     * ```
     */
    fun error(message: UDM, data: UDM = UDM.Scalar.nullValue()): UDM {
        val msg = (message as? UDM.Scalar)?.value ?: ""
        logInternal(LogLevel.ERROR, msg, data)
        return data
    }
    
    // ============================================
    // INSPECTION FUNCTIONS
    // ============================================
    
    /**
     * Logs the type of a value
     * 
     * @param value The value to inspect
     * @param message Optional message prefix
     * @return The original value (passthrough)
     * 
     * Example:
     * ```
     * logType(data, "Input type:")
     * // Logs: "Input type: Array"
     * ```
     */
    fun logType(value: UDM, message: UDM = UDM.Scalar("Type")): UDM {
        val msg = (message as? UDM.Scalar)?.value?.toString() ?: "Type"
        val type = when (value) {
            is UDM.Scalar -> when (value.value) {
                is String -> "String"
                is Number -> "Number"
                is Boolean -> "Boolean"
                null -> "Null"
                else -> "Scalar"
            }
            is UDM.Array -> "Array"
            is UDM.Object -> "Object"
            is UDM.DateTime -> "DateTime"
            is UDM.Binary -> "Binary"
            is UDM.Lambda -> "Function"
            else -> "Unknown"
        }
        
        logInternal(LogLevel.DEBUG, "$msg: $type", null)
        return value
    }
    
    /**
     * Logs the size/length of a value
     * 
     * @param value The value to measure (string, array, object)
     * @param message Optional message prefix
     * @return The original value (passthrough)
     * 
     * Example:
     * ```
     * logSize(items, "Item count:")
     * // Logs: "Item count: 25"
     * ```
     */
    fun logSize(value: UDM, message: UDM = UDM.Scalar("Size")): UDM {
        val msg = (message as? UDM.Scalar)?.value ?: "Size"
        val size = when (value) {
            is UDM.Scalar -> value.value.length
            is UDM.Array -> value.elements.size
            is UDM.Object -> value.properties.size
            else -> -1
        }
        
        if (size >= 0) {
            logInternal(LogLevel.DEBUG, "$msg: $size", null)
        }
        
        return value
    }
    
    /**
     * Logs a pretty-printed representation of a value
     * 
     * @param value The value to print
     * @param message Optional message prefix
     * @param indent Indentation level (default: 2)
     * @return The original value (passthrough)
     * 
     * Example:
     * ```
     * logPretty(complexObject, "Result:", 4)
     * ```
     */
    fun logPretty(
        value: UDM, 
        message: UDM = UDM.Scalar.nullValue(), 
        indent: UDM = UDM.Scalar(2.0)
    ): UDM {
        val msg = (message as? UDM.Scalar)?.value
        val indentSize = (indent as? UDM.Scalar)?.value?.toInt() ?: 2
        
        val pretty = prettyPrint(value, 0, indentSize)
        
        if (msg != null) {
            logInternal(LogLevel.INFO, "$msg\n$pretty", null)
        } else {
            logInternal(LogLevel.INFO, pretty, null)
        }
        
        return value
    }
    
    /**
     * Logs execution time of a block
     * 
     * Note: This is a placeholder for actual timing implementation
     * in the UTL-X runtime.
     * 
     * @param label Label for the timed operation
     * @return Timer token for endTimer()
     * 
     * Example:
     * ```
     * let timer = startTimer("Data processing")
     * // ... do work ...
     * endTimer(timer)
     * ```
     */
    fun startTimer(label: UDM = UDM.Scalar("Timer")): UDM {
        val lbl = (label as? UDM.Scalar)?.value ?: "Timer"
        val startTime = System.nanoTime()
        
        return UDM.Object(mapOf(
            "label" to UDM.Scalar(lbl),
            "startTime" to UDM.Scalar(startTime.toDouble())
        ))
    }
    
    /**
     * Logs elapsed time since startTimer()
     * 
     * @param timer Timer token from startTimer()
     * @return Elapsed time in milliseconds
     * 
     * Example:
     * ```
     * let timer = startTimer("Processing")
     * // ... work ...
     * endTimer(timer) // Logs: "Processing: 125.3ms"
     * ```
     */
    fun endTimer(timer: UDM): UDM {
        val timerObj = timer as? UDM.Object ?: return UDM.Scalar(0.0)
        val label = (timerObj.properties["label"] as? UDM.Scalar)?.value ?: "Timer"
        val startTime = (timerObj.properties["startTime"] as? UDM.Scalar)?.value?.toLong() ?: 0L
        
        val endTime = System.nanoTime()
        val elapsedMs = (endTime - startTime) / 1_000_000.0
        
        logInternal(LogLevel.INFO, "$label: ${String.format("%.2f", elapsedMs)}ms", null)
        
        return UDM.Scalar(elapsedMs)
    }
    
    // ============================================
    // LOG MANAGEMENT
    // ============================================
    
    /**
     * Retrieves all log entries
     * 
     * @return Array of log entries
     * 
     * Example:
     * ```
     * getLogs() // Returns all logged entries
     * ```
     */
    fun getLogs(): UDM {
        val logs = logBuffer.map { entry ->
            UDM.Object(mapOf(
                "timestamp" to UDM.Scalar(entry.timestamp.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)),
                "level" to UDM.Scalar(entry.level.name),
                "message" to UDM.Scalar(entry.message),
                "data" to (entry.data ?: UDM.Scalar.nullValue())
            ))
        }
        
        return UDM.Array(logs)
    }
    
    /**
     * Clears all log entries
     * 
     * @return Number of entries cleared
     * 
     * Example:
     * ```
     * clearLogs() // Empties log buffer
     * ```
     */
    fun clearLogs(): UDM {
        val count = logBuffer.size
        logBuffer.clear()
        return UDM.Scalar(count.toDouble())
    }
    
    /**
     * Returns count of log entries
     * 
     * @return Number of log entries
     * 
     * Example:
     * ```
     * logCount() // Returns: 42
     * ```
     */
    fun logCount(): UDM {
        return UDM.Scalar(logBuffer.size.toDouble())
    }
    
    // ============================================
    // ASSERTION FUNCTIONS
    // ============================================
    
    /**
     * Asserts a condition is true
     * 
     * @param condition Boolean condition to check
     * @param message Error message if condition is false
     * @return The condition value (passthrough)
     * 
     * Example:
     * ```
     * assert(price > 0, "Price must be positive")
     * ```
     */
    fun assert(condition: UDM, message: UDM = UDM.Scalar("Assertion failed")): UDM {
        val isTrue = (condition as? UDM.Scalar)?.value ?: false
        val msg = (message as? UDM.Scalar)?.value ?: "Assertion failed"
        
        if (!isTrue) {
            logInternal(LogLevel.ERROR, "ASSERTION FAILED: $msg", null)
            // In production, this might throw an exception
        }
        
        return condition
    }
    
    /**
     * Asserts two values are equal
     * 
     * @param actual Actual value
     * @param expected Expected value
     * @param message Optional error message
     * @return true if equal, false otherwise
     * 
     * Example:
     * ```
     * assertEqual(result, 42, "Result should be 42")
     * ```
     */
    fun assertEqual(
        actual: UDM, 
        expected: UDM, 
        message: UDM = UDM.Scalar.nullValue()
    ): UDM {
        val equal = actual == expected
        
        if (!equal) {
            val msg = (message as? UDM.Scalar)?.value ?: "Values not equal"
            logInternal(
                LogLevel.ERROR, 
                "ASSERTION FAILED: $msg\nExpected: $expected\nActual: $actual",
                null
            )
        }
        
        return UDM.Scalar(equal)
    }
    
    // ============================================
    // INTERNAL HELPERS
    // ============================================
    
    /**
     * Internal logging function
     */
    private fun logInternal(level: LogLevel, message: String, data: UDM?) {
        // Check if we should log based on level
        if (level.ordinal < currentLogLevel.ordinal) {
            return
        }
        
        val entry = LogEntry(
            timestamp = LocalDateTime.now(),
            level = level,
            message = message,
            data = data
        )
        
        // Add to buffer
        logBuffer.add(entry)
        
        // Keep buffer size manageable (last 1000 entries)
        if (logBuffer.size > 1000) {
            logBuffer.removeAt(0)
        }
        
        // Console output if enabled
        if (logToConsole) {
            val timestamp = entry.timestamp.format(DateTimeFormatter.ofPattern("HH:mm:ss.SSS"))
            val levelStr = "[${level.name.padEnd(5)}]"
            
            val output = buildString {
                append("$timestamp $levelStr $message")
                if (data != null && !(data is UDM.Scalar && data.value == null)) {
                    append("\n  Data: $data")
                }
            }
            
            println(output)
        }
    }
    
    /**
     * Pretty-prints a UDM value
     */
    private fun prettyPrint(value: UDM, depth: Int, indentSize: Int): String {
        val indent = " ".repeat(depth * indentSize)
        val nextIndent = " ".repeat((depth + 1) * indentSize)
        
        return when (value) {
            is UDM.Scalar -> when (value.value) {
                is String -> "\"${value.value}\""
                null -> "null"
                else -> value.value.toString()
            }
            is UDM.Array -> {
                if (value.elements.isEmpty()) {
                    "[]"
                } else {
                    val items = value.elements.joinToString(",\n") { item ->
                        "$nextIndent${prettyPrint(item, depth + 1, indentSize)}"
                    }
                    "[\n$items\n$indent]"
                }
            }
            is UDM.Object -> {
                if (value.properties.isEmpty()) {
                    "{}"
                } else {
                    val props = value.properties.entries.joinToString(",\n") { (key, v) ->
                        "$nextIndent\"$key\": ${prettyPrint(v, depth + 1, indentSize)}"
                    }
                    "{\n$props\n$indent}"
                }
            }
            else -> value.toString()
        }
    }
}
