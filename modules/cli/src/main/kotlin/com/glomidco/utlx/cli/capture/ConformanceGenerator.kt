// modules/cli/src/main/kotlin/com/glomidco/utlx/cli/capture/ConformanceGenerator.kt
package com.glomidco.utlx.cli.capture

import com.glomidco.utlx.core.udm.UDM
import com.glomidco.utlx.formats.json.JSONParser
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
            yaml.appendLine("  data: ${formatExpectedYamlValue(test.outputData, test.outputFormat)}")
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
     * A YAML double-quoted scalar holding exactly [v].
     *
     * CB01: the backslash MUST be escaped before the quote. Escaping only the quote left the
     * backslashes in JSON-escaped content raw and emitted `\\"`, which YAML reads as a backslash
     * followed by the end of the scalar. Seven captured fixtures were unparseable as a result, and
     * the conformance runner skipped them silently: neither passes nor failures, asserting nothing.
     */
    private fun quoteYamlString(v: String): String {
        val sb = StringBuilder("\"")
        for (c in v) {
            when {
                c == '\\' -> sb.append("\\\\")
                c == '"' -> sb.append("\\\"")
                // CB03: a literal control character inside a double-quoted YAML scalar is not
                // preserved — a newline is folded to a space and a carriage return is dropped, so
                // `a\rb` came back as `a b`. They have to be written as escapes.
                c == '\n' -> sb.append("\\n")
                c == '\r' -> sb.append("\\r")
                c == '\t' -> sb.append("\\t")
                c < ' ' -> sb.append("\\x%02x".format(c.code))
                else -> sb.append(c)
            }
        }
        return sb.append("\"").toString()
    }

    /**
     * Is [v] safe to write as a literal block scalar (`|`)?
     *
     * CB03: a block scalar is the readable choice for multi-line content and the corpus uses it
     * widely, but it cannot represent everything. `|` always ends the value with exactly one
     * newline, and trailing whitespace on a line is not reliably preserved. Where it cannot be
     * exact, [quoteYamlString] can, so this gates the choice rather than guessing.
     */
    private fun isBlockSafe(v: String): Boolean {
        if (v.isEmpty() || v.contains('\r') || v.contains('\u0000')) return false
        if (v.endsWith("\n\n")) return false                       // `|` collapses to one trailing newline
        val body = if (v.endsWith("\n")) v.dropLast(1) else v
        if (body.isEmpty()) return false
        return body.lines().none { it != it.trimEnd() }           // trailing spaces are not safe
    }

    /** A number YAML 1.1 reads back as the same number: no leading zero, no exponent. */
    private val SAFE_NUMBER = Regex("-?(0|[1-9]\\d*)(\\.\\d+)?")


    /**
     * CB02: format the **expected** value, which is not the same thing as formatting the output text.
     *
     * The conformance runner treats a string `expected.data` as the expected **value**, and only
     * re-parses it as text when it is a multi-line block beginning with `{` or `[`:
     *
     * ```python
     * if isinstance(expected_data, str) and '\n' in expected_data and expected_data.strip()[0] in '{[':
     *     expected_data = json.loads(expected_data)
     * ```
     *
     * Writing the raw output text therefore only works by accident — when the text happens to be a
     * multi-line object or array, or a bare number or boolean that YAML decodes to the same value.
     * For a transformation whose output document is a JSON **string** (`upper($input)`, `renderJson(...)`)
     * the text is one escaping level deeper than the value, and the fixture fails the moment it is
     * run. Three captured fixtures did exactly that, and only after CB01 made them parseable enough
     * to run at all.
     *
     * Note the asymmetry with `input.data`, which the runner uses as-is when it looks like JSON —
     * so the input side must stay as text. That asymmetry is the runner's convention; this function
     * exists to respect it on the output side rather than to change it.
     */
    private fun formatExpectedYamlValue(value: String, format: String): String {
        val jsonish = format.lowercase() in setOf("json", "avro", "avsc", "jsch")
        if (jsonish) {
            val trimmed = value.trim()
            if (trimmed.length >= 2 && trimmed.startsWith("\"") && trimmed.endsWith("\"")) {
                // A JSON string document: emit what it decodes to, which is what gets compared.
                val decoded = runCatching {
                    ((JSONParser(trimmed).parse() as? UDM.Scalar)?.value as? String)
                }.getOrNull()
                // Always quote: the decoded value is a STRING, and a string that happens to
                // look like JSON (`{"name":"John"}`) would otherwise take the `{`/`[` branch
                // below and be emitted raw, which YAML reads as a mapping rather than text.
                if (decoded != null) return quoteYamlString(decoded)
            }
        }
        return formatYamlValue(value)
    }

    /**
     * Format value for YAML output
     */
    private fun formatYamlValue(value: String): String {
        // Try to parse as JSON to determine if it needs quotes
        return when {
            // Multi-line string. CB03: `|` appends exactly one trailing newline, so a value that
            // does not end in one needs `|-` (strip) or it gains a character. Anything a block
            // scalar cannot hold exactly falls through to the quoted form, which always can.
            value.contains("\n") && isBlockSafe(value) -> {
                val chomp = if (value.endsWith("\n")) "|" else "|-"
                val body = if (value.endsWith("\n")) value.dropLast(1) else value
                chomp + "\n" + body.lines().joinToString("\n") { "    $it" }
            }
            // JSON object or array — emitted raw, so YAML decodes it to the same structure the
            // runner compares. (The text changes; the value does not.)
            value.trim().startsWith("{") || value.trim().startsWith("[") -> value
            // Number. CB03: only forms YAML 1.1 reads back unchanged go out raw. `0123` is OCTAL
            // (83), `007` is 7, and `1e5` is read as a *string* — all silent corruption. Anything
            // else numeric is normalised to a form that round-trips to the same number.
            SAFE_NUMBER.matches(value) -> value
            value.toDoubleOrNull() != null -> {
                val d = value.toDouble()
                if (d.isFinite() && d == Math.floor(d) && Math.abs(d) < 1e18) d.toLong().toString()
                else d.toString()
            }
            // Boolean
            value == "true" || value == "false" -> value
            // String - needs quotes
            // CB01: the backslash MUST be escaped before the quote. This escaped only the quote,
            // so a value carrying JSON-escaped content kept its raw backslashes and emitted
            // \\" — which YAML reads as a backslash followed by the end of the scalar. Seven
            // captured fixtures were unparseable as a result, and the conformance runner skipped
            // them silently: neither passes nor failures, asserting nothing at all.
            else -> quoteYamlString(value)
        }
    }
}
