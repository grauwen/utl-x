// stdlib/src/main/kotlin/org/apache/utlx/stdlib/core/DebugFunctions.kt
package org.apache.utlx.stdlib.core

import org.apache.utlx.core.udm.*
import org.apache.utlx.stdlib.FunctionArgumentException
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import org.apache.utlx.stdlib.annotations.UTLXFunction

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
    
    @UTLXFunction(
        description = "Sets the minimum log level",
        minArgs = 1,
        maxArgs = 1,
        category = "Core",
        parameters = [
            "level: Level value"
        ],
        returns = "Result of the operation",
        example = "setLogLevel(...) => result",
        notes = "Example:\n```\nsetLogLevel(\"DEBUG\")\n```",
        tags = ["core"],
        since = "1.0"
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
    fun setLogLevel(args: List<UDM>): UDM {
        if (args.isEmpty()) {
            throw FunctionArgumentException(
                "setLogLevel expects 1 argument, got 0. " +
                "Hint: Provide a log level string (TRACE, DEBUG, INFO, WARN, ERROR)."
            )
        }
        val level = args[0]
        val levelStr = (level as? UDM.Scalar)?.value?.toString()?.uppercase() ?: return UDM.Scalar(false)
        
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
    
    @UTLXFunction(
        description = "Enables or disables console logging",
        minArgs = 1,
        maxArgs = 1,
        category = "Core",
        parameters = [
            "enabled: Enabled value"
        ],
        returns = "Result of the operation",
        example = "setConsoleLogging(...) => result",
        notes = "Example:\n```\nsetConsoleLogging(false) // Disable console output\n```",
        tags = ["core"],
        since = "1.0"
    )
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
    fun setConsoleLogging(args: List<UDM>): UDM {
        if (args.isEmpty()) {
            throw FunctionArgumentException(
                "setConsoleLogging expects 1 argument, got 0. " +
                "Hint: Provide a boolean value (true to enable, false to disable)."
            )
        }
        val enabled = args[0]
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
    
    @UTLXFunction(
        description = "Logs a value with optional message (passthrough)",
        minArgs = 1,
        maxArgs = 1,
        category = "Core",
        parameters = [
            "value: Value value"
        ],
        returns = "the value unchanged, allowing use in pipelines.",
        example = "log(...) => result",
        notes = "Returns the value unchanged, allowing use in pipelines.\nExamples:\n```\nlog(data) // Logs and returns data\nlog(data, \"Processing:\") // With message\n// In pipeline:\ninput.items\n|> map(item => item.price)\n|> log(\"Prices:\")\n|> sum()\n```",
        tags = ["core"],
        since = "1.0"
    )
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
    fun log(args: List<UDM>): UDM {
        if (args.isEmpty()) {
            throw FunctionArgumentException(
                "log expects at least 1 argument, got 0. " +
                "Hint: Provide the value to log, optionally followed by a message prefix."
            )
        }
        val value = args[0]
        val message = if (args.size > 1) args[1] else UDM.Scalar(null)
        val msg = (message as? UDM.Scalar)?.value?.toString() ?: "LOG"
        logInternal(LogLevel.INFO, msg, value)
        return value // Passthrough
    }
    
    @UTLXFunction(
        description = "Logs with TRACE level",
        minArgs = 1,
        maxArgs = 1,
        category = "Core",
        parameters = [
            "message: Message value"
        ],
        returns = "Result of the operation",
        example = "trace(...) => result",
        notes = "Example:\n```\ntrace(\"Entering function\", input)\n```",
        tags = ["core"],
        since = "1.0"
    )
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
    fun trace(args: List<UDM>): UDM {
        if (args.isEmpty()) {
            throw FunctionArgumentException(
                "trace expects at least 1 argument, got 0. " +
                "Hint: Provide a message string, optionally followed by data to log."
            )
        }
        val message = args[0]
        val data = if (args.size > 1) args[1] else UDM.Scalar(null)
        val msg = (message as? UDM.Scalar)?.value?.toString() ?: ""
        logInternal(LogLevel.TRACE, msg, data)
        return data
    }

    @UTLXFunction(
        description = "Logs with DEBUG level",
        minArgs = 1,
        maxArgs = 1,
        category = "Core",
        parameters = [
            "message: Message value"
        ],
        returns = "Result of the operation",
        example = "debug(...) => result",
        notes = "Example:\n```\ndebug(\"Variable state\", myVar)\n```",
        tags = ["core"],
        since = "1.0"
    )
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
    fun debug(args: List<UDM>): UDM {
        if (args.isEmpty()) {
            throw FunctionArgumentException(
                "debug expects at least 1 argument, got 0. " +
                "Hint: Provide a message string, optionally followed by data to log."
            )
        }
        val message = args[0]
        val data = if (args.size > 1) args[1] else UDM.Scalar(null)
        val msg = (message as? UDM.Scalar)?.value?.toString() ?: ""
        logInternal(LogLevel.DEBUG, msg, data)
        return data
    }

    @UTLXFunction(
        description = "Logs with INFO level",
        minArgs = 1,
        maxArgs = 1,
        category = "Core",
        parameters = [
            "message: Message value"
        ],
        returns = "Result of the operation",
        example = "info(...) => result",
        notes = "Example:\n```\ninfo(\"Processing completed\", result)\n```",
        tags = ["core"],
        since = "1.0"
    )
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
    fun info(args: List<UDM>): UDM {
        if (args.isEmpty()) {
            throw FunctionArgumentException(
                "info expects at least 1 argument, got 0. " +
                "Hint: Provide a message string, optionally followed by data to log."
            )
        }
        val message = args[0]
        val data = if (args.size > 1) args[1] else UDM.Scalar(null)
        val msg = (message as? UDM.Scalar)?.value?.toString() ?: ""
        logInternal(LogLevel.INFO, msg, data)
        return data
    }

    @UTLXFunction(
        description = "Logs with WARN level",
        minArgs = 1,
        maxArgs = 1,
        category = "Core",
        parameters = [
            "array: Input array to process"
        ],
        returns = "Result of the operation",
        example = "warn(...) => result",
        notes = "Example:\n```\nwarn(\"Missing optional field\", fieldName)\n```",
        tags = ["core"],
        since = "1.0"
    )
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
    fun warn(args: List<UDM>): UDM {
        if (args.isEmpty()) {
            throw FunctionArgumentException(
                "warn expects at least 1 argument, got 0. " +
                "Hint: Provide a message string, optionally followed by data to log."
            )
        }
        val message = args[0]
        val data = if (args.size > 1) args[1] else UDM.Scalar(null)
        val msg = (message as? UDM.Scalar)?.value?.toString() ?: ""
        logInternal(LogLevel.WARN, msg, data)
        return data
    }

    @UTLXFunction(
        description = "Logs with ERROR level",
        minArgs = 1,
        maxArgs = 1,
        category = "Core",
        parameters = [
            "array: Input array to process",
        "predicate: Function to test each element (element) => boolean"
        ],
        returns = "Result of the operation",
        example = "error(...) => result",
        notes = "Example:\n```\nerror(\"Validation failed\", invalidData)\n```",
        tags = ["core"],
        since = "1.0"
    )
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
    fun error(args: List<UDM>): UDM {
        if (args.isEmpty()) {
            throw FunctionArgumentException(
                "error expects at least 1 argument, got 0. " +
                "Hint: Provide a message string, optionally followed by data to log."
            )
        }
        val message = args[0]
        val data = if (args.size > 1) args[1] else UDM.Scalar(null)
        val msg = (message as? UDM.Scalar)?.value?.toString() ?: ""
        logInternal(LogLevel.ERROR, msg, data)
        return data
    }
    
    // ============================================
    // INSPECTION FUNCTIONS
    // ============================================
    
    @UTLXFunction(
        description = "Logs the type of a value",
        minArgs = 1,
        maxArgs = 1,
        category = "Core",
        parameters = [
            "array: Input array to process",
        "predicate: Function to test each element (element) => boolean"
        ],
        returns = "Result of the operation",
        example = "logType(...) => result",
        notes = "Example:\n```\nlogType(data, \"Input type:\")\n// Logs: \"Input type: Array\"\n```",
        tags = ["core"],
        since = "1.0"
    )
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
    fun logType(args: List<UDM>): UDM {
        if (args.isEmpty()) {
            throw FunctionArgumentException(
                "logType expects at least 1 argument, got 0. " +
                "Hint: Provide a value to inspect, optionally followed by a message prefix."
            )
        }
        val value = args[0]
        val message = if (args.size > 1) args[1] else UDM.Scalar("Type")
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
    
    @UTLXFunction(
        description = "Logs the size/length of a value",
        minArgs = 1,
        maxArgs = 1,
        category = "Core",
        parameters = [
            "array: Input array to process"
        ],
        returns = "Result of the operation",
        example = "logSize(...) => result",
        notes = "Example:\n```\nlogSize(items, \"Item count:\")\n// Logs: \"Item count: 25\"\n```",
        tags = ["core"],
        since = "1.0"
    )
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
    fun logSize(args: List<UDM>): UDM {
        if (args.isEmpty()) {
            throw FunctionArgumentException(
                "logSize expects at least 1 argument, got 0. " +
                "Hint: Provide a value to measure (string, array, or object), optionally followed by a message prefix."
            )
        }
        val value = args[0]
        val message = if (args.size > 1) args[1] else UDM.Scalar("Size")
        val msg = (message as? UDM.Scalar)?.value ?: "Size"
        val size = when (value) {
            is UDM.Scalar -> (value.value as? String)?.length ?: value.value.toString().length
            is UDM.Array -> value.elements.size
            is UDM.Object -> value.properties.size
            else -> -1
        }
        
        if (size >= 0) {
            logInternal(LogLevel.DEBUG, "$msg: $size", null)
        }
        
        return value
    }
    
    @UTLXFunction(
        description = "Logs a pretty-printed representation of a value",
        minArgs = 1,
        maxArgs = 1,
        category = "Core",
        parameters = [
            "value: Value value"
        ],
        returns = "Result of the operation",
        example = "logPretty(...) => result",
        notes = "Example:\n```\nlogPretty(complexObject, \"Result:\", 4)\n```",
        tags = ["core"],
        since = "1.0"
    )
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
    fun logPretty(args: List<UDM>): UDM {
        if (args.isEmpty()) {
            throw FunctionArgumentException(
                "logPretty expects at least 1 argument, got 0. " +
                "Hint: Provide a value to pretty-print, optionally followed by message and indent size."
            )
        }
        val value = args[0]
        val message = if (args.size > 1) args[1] else UDM.Scalar(null)
        val indent = if (args.size > 2) args[2] else UDM.Scalar(2.0)
        val msg = (message as? UDM.Scalar)?.value
        val indentSize = (indent as? UDM.Scalar)?.value?.toString()?.toIntOrNull() ?: 2
        
        val pretty = prettyPrint(value, 0, indentSize)
        
        if (msg != null) {
            logInternal(LogLevel.INFO, "$msg\n$pretty", null)
        } else {
            logInternal(LogLevel.INFO, pretty, null)
        }
        
        return value
    }
    
    @UTLXFunction(
        description = "Logs execution time of a block",
        minArgs = 1,
        maxArgs = 1,
        category = "Core",
        parameters = [
            "array: Input array to process"
        ],
        returns = "Result of the operation",
        example = "startTimer(...) => result",
        notes = "Note: This is a placeholder for actual timing implementation\nin the UTL-X runtime.\nExample:\n```\nlet timer = startTimer(\"Data processing\")\n// ... do work ...\nendTimer(timer)\n```",
        tags = ["core"],
        since = "1.0"
    )
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
    fun startTimer(args: List<UDM>): UDM {
        val label = if (args.isNotEmpty()) args[0] else UDM.Scalar("Timer")
        val lbl = (label as? UDM.Scalar)?.value ?: "Timer"
        val startTime = System.nanoTime()
        
        return UDM.Object(mapOf(
            "label" to UDM.Scalar(lbl),
            "startTime" to UDM.Scalar(startTime.toDouble())
        ))
    }
    
    @UTLXFunction(
        description = "Logs elapsed time since startTimer()",
        minArgs = 1,
        maxArgs = 1,
        category = "Core",
        parameters = [
            "array: Input array to process"
        ],
        returns = "Result of the operation",
        example = "endTimer(...) => result",
        notes = "Example:\n```\nlet timer = startTimer(\"Processing\")\n// ... work ...\nendTimer(timer) // Logs: \"Processing: 125.3ms\"\n```",
        tags = ["core"],
        since = "1.0"
    )
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
    fun endTimer(args: List<UDM>): UDM {
        if (args.isEmpty()) {
            throw FunctionArgumentException(
                "endTimer expects 1 argument, got 0. " +
                "Hint: Provide the timer object returned by startTimer()."
            )
        }
        val timer = args[0]
        val timerObj = timer as? UDM.Object ?: return UDM.Scalar(0.0)
        val label = (timerObj.properties["label"] as? UDM.Scalar)?.value ?: "Timer"
        val startTime = (timerObj.properties["startTime"] as? UDM.Scalar)?.value?.toString()?.toLongOrNull() ?: 0L
        
        val endTime = System.nanoTime()
        val elapsedMs = (endTime - startTime) / 1_000_000.0
        
        logInternal(LogLevel.INFO, "$label: ${String.format("%.2f", elapsedMs)}ms", null)
        
        return UDM.Scalar(elapsedMs)
    }
    
    // ============================================
    // LOG MANAGEMENT
    // ============================================
    
    @UTLXFunction(
        description = "Retrieves all log entries",
        minArgs = 1,
        maxArgs = 1,
        category = "Core",
        parameters = [
            "array: Input array to process"
        ],
        returns = "Result of the operation",
        example = "getLogs(...) => result",
        notes = "Example:\n```\ngetLogs() // Returns all logged entries\n```",
        tags = ["core"],
        since = "1.0"
    )
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
    fun getLogs(args: List<UDM>): UDM {
        val logs = logBuffer.map { entry ->
            UDM.Object(mapOf(
                "timestamp" to UDM.Scalar(entry.timestamp.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)),
                "level" to UDM.Scalar(entry.level.name),
                "message" to UDM.Scalar(entry.message),
                "data" to (entry.data ?: UDM.Scalar(null))
            ))
        }
        
        return UDM.Array(logs)
    }
    
    @UTLXFunction(
        description = "Clears all log entries",
        minArgs = 1,
        maxArgs = 1,
        category = "Core",
        parameters = [
            "condition: Condition value"
        ],
        returns = "Result of the operation",
        example = "clearLogs(...) => result",
        notes = "Example:\n```\nclearLogs() // Empties log buffer\n```",
        tags = ["core"],
        since = "1.0"
    )
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
    fun clearLogs(args: List<UDM>): UDM {
        val count = logBuffer.size
        logBuffer.clear()
        return UDM.Scalar(count.toDouble())
    }
    
    @UTLXFunction(
        description = "Example:",
        minArgs = 1,
        maxArgs = 1,
        category = "Core",
        parameters = [
            "condition: Condition value"
        ],
        returns = "count of log entries",
        example = "logCount(...) => result",
        notes = "Returns count of log entries\n```\nlogCount() // Returns: 42\n```",
        tags = ["core"],
        since = "1.0"
    )
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
    fun logCount(args: List<UDM>): UDM {
        return UDM.Scalar(logBuffer.size.toDouble())
    }
    
    // ============================================
    // ASSERTION FUNCTIONS
    // ============================================
    
    @UTLXFunction(
        description = "Asserts a condition is true",
        minArgs = 1,
        maxArgs = 1,
        category = "Core",
        parameters = [
            "condition: Condition value",
        "expected: Expected value"
        ],
        returns = "Result of the operation",
        example = "assert(...) => result",
        notes = "Example:\n```\nassert(price > 0, \"Price must be positive\")\n```",
        tags = ["core"],
        since = "1.0"
    )
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
    fun assert(args: List<UDM>): UDM {
        if (args.isEmpty()) {
            throw FunctionArgumentException(
                "assert expects at least 1 argument, got 0. " +
                "Hint: Provide a boolean condition to assert, optionally followed by an error message."
            )
        }
        val condition = args[0]
        val message = if (args.size > 1) args[1] else UDM.Scalar("Assertion failed")
        val isTrue = (condition as? UDM.Scalar)?.value as? Boolean ?: false
        val msg = (message as? UDM.Scalar)?.value?.toString() ?: "Assertion failed"
        
        if (!isTrue) {
            logInternal(LogLevel.ERROR, "ASSERTION FAILED: $msg", null)
            // In production, this might throw an exception
        }
        
        return condition
    }
    
    @UTLXFunction(
        description = "Asserts two values are equal",
        minArgs = 2,
        maxArgs = 2,
        category = "Core",
        parameters = [
            "actual: Actual value",
        "expected: Expected value"
        ],
        returns = "Result of the operation",
        example = "assertEqual(...) => result",
        notes = "Example:\n```\nassertEqual(result, 42, \"Result should be 42\")\n```",
        tags = ["core"],
        since = "1.0"
    )
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
    fun assertEqual(args: List<UDM>): UDM {
        if (args.size < 2) {
            throw FunctionArgumentException(
                "assertEqual expects at least 2 arguments, got ${args.size}. " +
                "Hint: Provide actual value, expected value, and optionally an error message."
            )
        }
        val actual = args[0]
        val expected = args[1]
        val message = if (args.size > 2) args[2] else UDM.Scalar(null)
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

    private fun getTypeDescription(udm: UDM): String {
        return when (udm) {
            is UDM.Scalar -> {
                when (val value = udm.value) {
                    is String -> "string"
                    is Number -> "number"
                    is Boolean -> "boolean"
                    null -> "null"
                    else -> value.javaClass.simpleName
                }
            }
            is UDM.Array -> "array"
            is UDM.Object -> "object"
            is UDM.Binary -> "binary"
            is UDM.DateTime -> "datetime"
            is UDM.Date -> "date"
            is UDM.LocalDateTime -> "localdatetime"
            is UDM.Time -> "time"
            is UDM.Lambda -> "lambda"
            else -> udm.javaClass.simpleName
        }
    }

}
