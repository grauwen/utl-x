// modules/cli/src/main/kotlin/org/apache/utlx/cli/capture/TestCaptureService.kt
package org.apache.utlx.cli.capture

import java.io.File
import java.time.Instant

/**
 * Main service for capturing test executions and generating conformance tests
 */
object TestCaptureService {

    private val config: CaptureConfig = CaptureConfig.load()

    /**
     * Capture a transformation execution
     *
     * @param overrideEnabled Optional override for capture enabled state:
     *   - null: use config.enabled (default behavior)
     *   - true: force enable capture (ignore config)
     *   - false: force disable capture (ignore config)
     */
    fun captureExecution(
        transformation: String,
        inputData: String,
        inputFormat: String,
        outputData: String,
        outputFormat: String,
        success: Boolean,
        error: String? = null,
        durationMs: Long,
        scriptFile: File,
        overrideEnabled: Boolean? = null
    ) {
        // Check if capture is enabled (with CLI override support)
        val captureEnabled = overrideEnabled ?: config.enabled
        if (!captureEnabled) {
            if (config.verbose && overrideEnabled == false) {
                println("  [Capture] Disabled via --no-capture flag")
            }
            return
        }

        if (config.verbose && overrideEnabled == true) {
            println("  [Capture] Enabled via --capture flag (overriding config)")
        }

        // Check if script file matches ignore patterns
        if (shouldIgnore(scriptFile)) {
            if (config.verbose) {
                println("  [Capture] Skipping (matches ignore pattern): ${scriptFile.path}")
            }
            return
        }

        try {
            // Generate test ID
            val testId = TestDeduplicator.generateTestId(transformation, inputData, inputFormat)

            // Check for duplicates
            if (config.deduplicate && TestDeduplicator.testExists(testId, config.captureLocation)) {
                if (config.verbose) {
                    println("  [Capture] Skipping duplicate test: $testId")
                }
                return
            }

            // Extract functions and categorize
            val functions = TestCategorizer.extractFunctions(transformation)
            val category = TestCategorizer.categorize(transformation, inputFormat, outputFormat)

            // Check if we've exceeded the limit for this category
            if (TestDeduplicator.exceedsLimit(category, config.captureLocation, config.maxTestsPerFunction)) {
                if (config.verbose) {
                    println("  [Capture] Skipping (category limit reached): $category")
                }
                return
            }

            // Create captured test
            val capturedTest = CapturedTest(
                id = testId,
                timestamp = Instant.now(),
                transformation = transformation,
                inputData = inputData,
                inputFormat = inputFormat,
                outputData = outputData,
                outputFormat = outputFormat,
                success = success,
                error = error,
                durationMs = durationMs,
                category = category,
                functionNames = functions
            )

            // Determine if this is a known issue
            val knownIssue = if (!success && config.captureFailures) {
                KnownIssue(
                    status = "failing",
                    capturedOutput = outputData,
                    expectedOutput = null,
                    issueDescription = error ?: "Transformation failed - needs investigation",
                    capturedAt = Instant.now()
                )
            } else if (!success) {
                // Not capturing failures
                if (config.verbose) {
                    println("  [Capture] Skipping failed test (capture failures disabled)")
                }
                return
            } else {
                null
            }

            // Save test
            val testFile = ConformanceGenerator.saveTest(capturedTest, config, knownIssue)

            // Print capture notification
            val status = if (knownIssue != null) "known issue" else "passing"
            println("  ✓ Test captured: ${testFile.relativeTo(File(config.captureLocation)).path} ($status)")

        } catch (e: Exception) {
            System.err.println("  [Capture Error] Failed to capture test: ${e.message}")
            if (config.verbose) {
                e.printStackTrace()
            }
        }
    }

    /**
     * Check if script file should be ignored based on patterns
     */
    private fun shouldIgnore(scriptFile: File): Boolean {
        val path = scriptFile.absolutePath
        return config.ignorePatterns.any { pattern ->
            path.contains(pattern.replace("**", "").replace("*", ""))
        }
    }

    /**
     * Get capture statistics
     */
    fun getStatistics(): CaptureStatistics {
        val captureDir = File(config.captureLocation)
        if (!captureDir.exists()) {
            return CaptureStatistics(0, 0, 0, emptyMap())
        }

        val allTests = captureDir.walk()
            .filter { it.extension == "yaml" || it.extension == "yml" }
            .toList()

        val knownIssues = allTests.count { file ->
            file.readText().contains("known_issue:")
        }

        val passingTests = allTests.size - knownIssues

        val testsByCategory = allTests.groupBy { file ->
            // Extract category from path
            val relativePath = file.relativeTo(captureDir).parent
            relativePath?.replace(File.separator, "/") ?: "uncategorized"
        }.mapValues { (_, files) -> files.size }

        return CaptureStatistics(
            totalTests = allTests.size,
            passingTests = passingTests,
            knownIssues = knownIssues,
            testsByCategory = testsByCategory
        )
    }

    /**
     * Enable or disable capture
     */
    fun setEnabled(enabled: Boolean) {
        // This would update the config file in a full implementation
        println(if (enabled) "Test capture enabled" else "Test capture disabled")
    }
}

/**
 * Statistics about captured tests
 */
data class CaptureStatistics(
    val totalTests: Int,
    val passingTests: Int,
    val knownIssues: Int,
    val testsByCategory: Map<String, Int>
)
