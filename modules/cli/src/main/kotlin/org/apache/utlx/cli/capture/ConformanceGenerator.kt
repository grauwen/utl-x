// modules/cli/src/main/kotlin/org/apache/utlx/cli/capture/ConformanceGenerator.kt
package org.apache.utlx.cli.capture

import java.io.File
import java.time.format.DateTimeFormatter
import java.time.LocalDate
import java.time.ZoneId

/**
 * Generates conformance test YAML files from captured tests
 */
object ConformanceGenerator {

    /**
     * Generate YAML conformance test from captured test
     */
    fun generateYaml(test: CapturedTest, knownIssue: KnownIssue? = null): String {
        val primaryFunction = TestCategorizer.getPrimaryFunction(test.transformation)
        val testName = generateTestName(primaryFunction, test.id, test.timestamp)

        val yaml = StringBuilder()

        // Header
        yaml.appendLine("name: \"$testName\"")
        yaml.appendLine("category: \"${test.category ?: "uncategorized"}\"")
        yaml.appendLine("description: \"Auto-captured test for ${primaryFunction ?: "transformation"}\"")

        // Tags
        val tags = buildList {
            add("auto-generated")
            primaryFunction?.let { add(it) }
            test.functionNames.forEach { add(it) }
        }
        yaml.appendLine("tags: [${tags.joinToString(", ") { "\"$it\"" }}]")

        // Auto-generated marker
        yaml.appendLine("auto_generated: true")
        yaml.appendLine("captured_at: \"${test.timestamp.toString()}\"")

        yaml.appendLine()

        // Input
        yaml.appendLine("input:")
        yaml.appendLine("  format: ${test.inputFormat}")
        yaml.appendLine("  data: ${formatYamlValue(test.inputData)}")

        yaml.appendLine()

        // Transformation
        yaml.appendLine("transformation: |")
        test.transformation.lines().forEach { line ->
            yaml.appendLine("  $line")
        }

        yaml.appendLine()

        // Expected output or known issue
        if (knownIssue != null) {
            yaml.appendLine("known_issue:")
            yaml.appendLine("  status: \"${knownIssue.status}\"")
            yaml.appendLine("  captured_output: ${formatYamlValue(knownIssue.capturedOutput)}")
            yaml.appendLine("  issue_description: \"${knownIssue.issueDescription}\"")
            yaml.appendLine("  captured_at: \"${knownIssue.capturedAt}\"")
        } else {
            yaml.appendLine("expected:")
            yaml.appendLine("  format: ${test.outputFormat}")
            yaml.appendLine("  data: ${formatYamlValue(test.outputData)}")
        }

        yaml.appendLine()

        // Performance metadata
        yaml.appendLine("performance_limits:")
        yaml.appendLine("  max_duration_ms: ${test.durationMs * 2}") // 2x actual time as limit
        yaml.appendLine("  max_memory_mb: 10")

        yaml.appendLine()

        // Metadata
        yaml.appendLine("metadata:")
        yaml.appendLine("  author: \"UTL-X Auto-Capture\"")
        val localDate = LocalDate.ofInstant(test.timestamp, ZoneId.systemDefault())
        yaml.appendLine("  captured_date: \"${localDate.format(DateTimeFormatter.ISO_LOCAL_DATE)}\"")
        yaml.appendLine("  references: [\"Auto-generated test case\"]")

        return yaml.toString()
    }

    /**
     * Save captured test to file
     */
    fun saveTest(test: CapturedTest, config: CaptureConfig, knownIssue: KnownIssue? = null): File {
        val category = test.category ?: "uncategorized"
        val primaryFunction = TestCategorizer.getPrimaryFunction(test.transformation)
        val testName = generateTestName(primaryFunction, test.id, test.timestamp)

        // Create directory structure
        val categoryPath = File(config.captureLocation, category.replace("/", File.separator))
        categoryPath.mkdirs()

        // Create file
        val testFile = File(categoryPath, "$testName.yaml")

        // Generate and write YAML
        val yaml = generateYaml(test, knownIssue)
        testFile.writeText(yaml)

        return testFile
    }

    /**
     * Generate test name with timestamp and ID
     * Format: auto_<timestamp>_<uid>
     * Example: auto_2025-11-02-21-45-30_16e0f6d6
     */
    private fun generateTestName(@Suppress("UNUSED_PARAMETER") primaryFunction: String?, testId: String, timestamp: java.time.Instant): String {
        val localDateTime = java.time.LocalDateTime.ofInstant(timestamp, java.time.ZoneId.systemDefault())
        val formattedTime = localDateTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd-HH-mm-ss"))
        return "auto_${formattedTime}_$testId"
    }

    /**
     * Format value for YAML output
     */
    private fun formatYamlValue(value: String): String {
        // Try to parse as JSON to determine if it needs quotes
        return when {
            // Simple string that needs quoting
            value.contains("\n") -> {
                // Multi-line string
                "|\n" + value.lines().joinToString("\n") { "    $it" }
            }
            // JSON object or array
            value.trim().startsWith("{") || value.trim().startsWith("[") -> value
            // Number
            value.toDoubleOrNull() != null -> value
            // Boolean
            value == "true" || value == "false" -> value
            // String - needs quotes
            else -> "\"${value.replace("\"", "\\\"")}\""
        }
    }
}
