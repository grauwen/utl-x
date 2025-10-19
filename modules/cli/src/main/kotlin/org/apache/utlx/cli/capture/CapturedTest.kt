// modules/cli/src/main/kotlin/org/apache/utlx/cli/capture/CapturedTest.kt
package org.apache.utlx.cli.capture

import java.time.Instant

/**
 * Represents a captured test execution
 */
data class CapturedTest(
    val id: String,                    // Unique ID based on content hash
    val timestamp: Instant,
    val transformation: String,         // UTL-X script content
    val inputData: String,
    val inputFormat: String,
    val outputData: String,
    val outputFormat: String,
    val success: Boolean,              // Did transformation succeed?
    val error: String? = null,         // Error message if failed
    val durationMs: Long,              // Execution time
    val category: String? = null,      // Auto-detected category
    val functionNames: List<String> = emptyList()  // Functions used in transformation
)

/**
 * Configuration for test capture
 */
data class CaptureConfig(
    val enabled: Boolean = false,  // DISABLED by default for production
    val captureLocation: String = "conformance-suite/tests/auto-captured/",
    val deduplicate: Boolean = true,
    val captureFailures: Boolean = true,
    val maxTestsPerFunction: Int = 50,
    val ignorePatterns: List<String> = listOf("**/tmp/**", "**/test_*.utlx"),
    val verbose: Boolean = false
) {
    companion object {
        val DEFAULT = CaptureConfig()

        /**
         * Load configuration with priority:
         * 1. Environment variable UTLX_CAPTURE_TESTS (highest priority)
         * 2. Config file ~/.utlx/capture-config.yaml
         * 3. Default (disabled)
         */
        fun load(): CaptureConfig {
            // Check environment variable first (cross-platform)
            val envEnabled = System.getenv("UTLX_CAPTURE_TESTS")
            val enabledFromEnv = when (envEnabled?.lowercase()) {
                "true", "1", "yes", "on" -> true
                "false", "0", "no", "off" -> false
                null -> null  // Not set, check config file
                else -> {
                    System.err.println("Warning: Invalid UTLX_CAPTURE_TESTS value '$envEnabled', expected true/false")
                    null
                }
            }

            // If env var is set, use it
            if (enabledFromEnv != null) {
                return DEFAULT.copy(enabled = enabledFromEnv)
            }

            // Otherwise, check config file
            val configFile = java.io.File(System.getProperty("user.home"), ".utlx/capture-config.yaml")
            return if (configFile.exists()) {
                try {
                    parseConfigFile(configFile)
                } catch (e: Exception) {
                    System.err.println("Warning: Failed to parse capture config: ${e.message}")
                    DEFAULT
                }
            } else {
                // No env var, no config file - use default (DISABLED)
                DEFAULT
            }
        }

        /**
         * Simple YAML parser for config file
         * Supports basic key: value format
         */
        private fun parseConfigFile(configFile: java.io.File): CaptureConfig {
            val lines = configFile.readLines()
            var enabled = DEFAULT.enabled
            var captureLocation = DEFAULT.captureLocation
            var deduplicate = DEFAULT.deduplicate
            var captureFailures = DEFAULT.captureFailures
            var maxTestsPerFunction = DEFAULT.maxTestsPerFunction
            var verbose = DEFAULT.verbose
            val ignorePatterns = mutableListOf<String>()

            var inIgnorePatterns = false

            for (line in lines) {
                val trimmed = line.trim()
                if (trimmed.isEmpty() || trimmed.startsWith("#")) continue

                // Check if we're in ignore_patterns section
                if (trimmed.startsWith("ignore_patterns:")) {
                    inIgnorePatterns = true
                    continue
                }

                // If line starts with non-space, we've left ignore_patterns
                if (!line.startsWith(" ") && !line.startsWith("\t") && inIgnorePatterns) {
                    inIgnorePatterns = false
                }

                if (inIgnorePatterns) {
                    // Parse list item: - "pattern"
                    val pattern = trimmed.removePrefix("-").trim().removeSurrounding("\"")
                    if (pattern.isNotEmpty()) {
                        ignorePatterns.add(pattern)
                    }
                    continue
                }

                // Parse key: value
                val parts = trimmed.split(":", limit = 2)
                if (parts.size != 2) continue

                val key = parts[0].trim()
                val value = parts[1].trim()

                when (key) {
                    "enabled" -> enabled = value.toBoolean()
                    "capture_location" -> captureLocation = value.removeSurrounding("\"")
                    "deduplicate" -> deduplicate = value.toBoolean()
                    "capture_failures" -> captureFailures = value.toBoolean()
                    "max_tests_per_function" -> maxTestsPerFunction = value.toIntOrNull() ?: DEFAULT.maxTestsPerFunction
                    "verbose" -> verbose = value.toBoolean()
                }
            }

            return CaptureConfig(
                enabled = enabled,
                captureLocation = captureLocation,
                deduplicate = deduplicate,
                captureFailures = captureFailures,
                maxTestsPerFunction = maxTestsPerFunction,
                ignorePatterns = if (ignorePatterns.isEmpty()) DEFAULT.ignorePatterns else ignorePatterns,
                verbose = verbose
            )
        }
    }
}

/**
 * Known issue marker for failing tests
 */
data class KnownIssue(
    val status: String = "failing",
    val capturedOutput: String,
    val expectedOutput: String? = null,
    val issueDescription: String = "Auto-captured failure - needs investigation",
    val capturedAt: Instant = Instant.now()
)
