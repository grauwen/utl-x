package org.apache.utlx.core.debug

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import org.slf4j.LoggerFactory
import java.io.File
import java.util.Properties

/**
 * Central configuration manager for UTL-X debugging and logging
 *
 * Supports multiple configuration sources with priority:
 * 1. Programmatic API (highest priority)
 * 2. Environment variables
 * 3. Configuration file (./utlx-logging.properties or ~/.utlx/logging.properties)
 * 4. Default logback.xml
 *
 * @since 1.0.0
 */
object DebugConfig {

    /**
     * Logging components
     */
    enum class Component(val loggerName: String) {
        LEXER("org.apache.utlx.core.lexer"),
        PARSER("org.apache.utlx.core.parser"),
        INTERPRETER("org.apache.utlx.core.interpreter"),
        TYPE_SYSTEM("org.apache.utlx.core.types"),
        UDM("org.apache.utlx.core.udm"),
        AST("org.apache.utlx.core.ast"),
        FORMATS("org.apache.utlx.formats"),
        ROOT("ROOT")
    }

    /**
     * Log levels matching SLF4J/Logback
     */
    enum class LogLevel {
        TRACE, DEBUG, INFO, WARN, ERROR, OFF
    }

    /**
     * Feature-specific trace flags
     */
    private val featureTraces = mutableSetOf<String>()

    /**
     * Whether configuration has been initialized
     */
    private var initialized = false

    init {
        initialize()
    }

    /**
     * Initialize configuration from all sources
     */
    fun initialize() {
        if (initialized) return

        // Load from config file if exists
        loadConfigFile()

        // Override with environment variables
        loadEnvironmentVariables()

        initialized = true
    }

    /**
     * Load configuration from file
     */
    private fun loadConfigFile() {
        // Try current directory first
        val localConfig = File("./utlx-logging.properties")
        if (localConfig.exists()) {
            loadProperties(localConfig)
            return
        }

        // Try user home directory
        val userConfig = File(System.getProperty("user.home"), ".utlx/logging.properties")
        if (userConfig.exists()) {
            loadProperties(userConfig)
        }
    }

    /**
     * Load properties from file
     */
    private fun loadProperties(file: File) {
        val props = Properties()
        file.inputStream().use { props.load(it) }

        // Set global level
        props.getProperty("utlx.log.level")?.let { level ->
            setGlobalLogLevel(LogLevel.valueOf(level.uppercase()))
        }

        // Set component-specific levels
        Component.values().forEach { component ->
            props.getProperty("utlx.log.${component.name.lowercase()}")?.let { level ->
                setComponentLogLevel(component, LogLevel.valueOf(level.uppercase()))
            }
        }

        // Load feature traces
        props.getProperty("utlx.trace.features")?.split(",")?.forEach { feature ->
            featureTraces.add(feature.trim())
        }
    }

    /**
     * Load configuration from environment variables
     */
    private fun loadEnvironmentVariables() {
        // Global level: UTLX_LOG_LEVEL=DEBUG
        System.getenv("UTLX_LOG_LEVEL")?.let { level ->
            setGlobalLogLevel(LogLevel.valueOf(level.uppercase()))
        }

        // Component-specific: UTLX_DEBUG_PARSER=true
        Component.values().forEach { component ->
            val envVar = "UTLX_DEBUG_${component.name}"
            val value = System.getenv(envVar)
            if (value?.toBoolean() == true) {
                setComponentLogLevel(component, LogLevel.DEBUG)
            }
        }

        // Feature traces: UTLX_TRACE_FEATURES=let-bindings,array-parsing
        System.getenv("UTLX_TRACE_FEATURES")?.split(",")?.forEach { feature ->
            featureTraces.add(feature.trim())
        }
    }

    /**
     * Set global log level for all components
     */
    fun setGlobalLogLevel(level: LogLevel) {
        val logbackLevel = level.toLogbackLevel()
        val rootLogger = LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME) as Logger
        rootLogger.level = logbackLevel
    }

    /**
     * Set log level for a specific component
     */
    fun setComponentLogLevel(component: Component, level: LogLevel) {
        val logger = LoggerFactory.getLogger(component.loggerName) as Logger
        logger.level = level.toLogbackLevel()
    }

    /**
     * Enable DEBUG level for a component
     */
    fun enableComponent(component: Component) {
        setComponentLogLevel(component, LogLevel.DEBUG)
    }

    /**
     * Enable TRACE level for a component
     */
    fun enableComponentTrace(component: Component) {
        setComponentLogLevel(component, LogLevel.TRACE)
    }

    /**
     * Disable logging for a component (set to ERROR)
     */
    fun disableComponent(component: Component) {
        setComponentLogLevel(component, LogLevel.ERROR)
    }

    /**
     * Check if DEBUG is enabled for a component
     */
    fun isDebugEnabled(component: Component): Boolean {
        val logger = LoggerFactory.getLogger(component.loggerName) as Logger
        return logger.isDebugEnabled
    }

    /**
     * Check if TRACE is enabled for a component
     */
    fun isTraceEnabled(component: Component): Boolean {
        val logger = LoggerFactory.getLogger(component.loggerName) as Logger
        return logger.isTraceEnabled
    }

    /**
     * Enable trace for a specific feature
     */
    fun enableFeatureTrace(feature: String) {
        featureTraces.add(feature)
    }

    /**
     * Check if feature trace is enabled
     */
    fun isFeatureTraceEnabled(feature: String): Boolean {
        return featureTraces.contains(feature)
    }

    /**
     * Get current log level for a component
     */
    fun getComponentLogLevel(component: Component): LogLevel {
        val logger = LoggerFactory.getLogger(component.loggerName) as Logger
        return logger.level?.toUtlxLevel() ?: LogLevel.INFO
    }

    /**
     * Reset all configuration to defaults
     */
    fun reset() {
        featureTraces.clear()
        setGlobalLogLevel(LogLevel.INFO)
        initialized = false
    }

    /**
     * Convert UTL-X LogLevel to Logback Level
     */
    private fun LogLevel.toLogbackLevel(): Level = when (this) {
        LogLevel.TRACE -> Level.TRACE
        LogLevel.DEBUG -> Level.DEBUG
        LogLevel.INFO -> Level.INFO
        LogLevel.WARN -> Level.WARN
        LogLevel.ERROR -> Level.ERROR
        LogLevel.OFF -> Level.OFF
    }

    /**
     * Convert Logback Level to UTL-X LogLevel
     */
    private fun Level.toUtlxLevel(): LogLevel = when (this) {
        Level.TRACE -> LogLevel.TRACE
        Level.DEBUG -> LogLevel.DEBUG
        Level.INFO -> LogLevel.INFO
        Level.WARN -> LogLevel.WARN
        Level.ERROR -> LogLevel.ERROR
        Level.OFF -> LogLevel.OFF
        else -> LogLevel.INFO
    }

    /**
     * Print current configuration (for debugging)
     */
    fun printConfiguration() {
        println("=== UTL-X Debug Configuration ===")
        Component.values().forEach { component ->
            val level = getComponentLogLevel(component)
            println("  ${component.name.padEnd(20)}: $level")
        }
        if (featureTraces.isNotEmpty()) {
            println("  Feature Traces: ${featureTraces.joinToString(", ")}")
        }
        println("=================================")
    }
}
