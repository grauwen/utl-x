package org.apache.utlx.conformance.lsp

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import org.slf4j.LoggerFactory

/**
 * Executes LSP conformance tests
 */
class TestExecutor(
    private val client: JsonRpcClient
) {
    private val logger = LoggerFactory.getLogger(TestExecutor::class.java)
    private val objectMapper = ObjectMapper().registerKotlinModule()

    /**
     * Execute a test case
     */
    fun execute(testCase: TestCase): TestResult {
        logger.info("Executing test: ${testCase.name}")

        try {
            // Process each step in sequence
            for ((index, step) in testCase.sequence.withIndex()) {
                logger.debug("Step ${index + 1}: ${step.type} ${step.method}")

                // Substitute document placeholders in params
                val resolvedParams = resolveTemplates(step.params, testCase.documents)

                when (step.type) {
                    StepType.REQUEST -> {
                        val response = client.sendRequest(step.method, resolvedParams)

                        // Validate response if expected result is provided
                        if (step.expect != null) {
                            validateResponse(response, step.expect, testCase.name, index)
                        }

                        // Wait for expected notification if specified
                        if (step.expectNotification != null) {
                            validateNotification(step.expectNotification, testCase.name, index)
                        }
                    }

                    StepType.NOTIFICATION -> {
                        client.sendNotification(step.method, resolvedParams)

                        // Wait for expected notification if specified
                        if (step.expectNotification != null) {
                            validateNotification(step.expectNotification, testCase.name, index)
                        }
                    }
                }
            }

            return TestResult.Success(testCase.name)
        } catch (e: Exception) {
            logger.error("Test failed: ${testCase.name}", e)
            return TestResult.Failure(testCase.name, e.message ?: "Unknown error")
        }
    }

    private fun resolveTemplates(value: Any?, documents: Map<String, TestDocument>): Any? {
        return when (value) {
            is String -> {
                // Replace {{documents.xxx.yyy}} patterns
                val pattern = Regex("""\{\{documents\.(\w+)\.(\w+)\}\}""")
                pattern.replace(value) { match ->
                    val docName = match.groupValues[1]
                    val field = match.groupValues[2]
                    val doc = documents[docName]
                    if (doc != null) {
                        when (field) {
                            "uri" -> doc.uri
                            "languageId" -> doc.languageId
                            "version" -> doc.version.toString()
                            "text" -> doc.text
                            else -> match.value
                        }
                    } else {
                        match.value
                    }
                }
            }
            is Map<*, *> -> {
                @Suppress("UNCHECKED_CAST")
                (value as Map<String, Any?>).mapValues { (_, v) -> resolveTemplates(v, documents) }
            }
            is List<*> -> {
                value.map { resolveTemplates(it, documents) }
            }
            else -> value
        }
    }

    private fun validateResponse(
        response: Map<String, Any?>,
        expected: ExpectedResult,
        testName: String,
        stepIndex: Int
    ) {
        // Check for error
        if (expected.error != null && response["error"] == null) {
            throw AssertionError("Expected error in step $stepIndex, but got success")
        }

        // Check result
        if (expected.result != null) {
            val actualResult = response["result"]
            if (!matchesExpectation(actualResult, expected.result)) {
                throw AssertionError(
                    "Step $stepIndex result mismatch:\nExpected: ${expected.result}\nActual: $actualResult"
                )
            }
        }
    }

    private fun validateNotification(
        expected: ExpectedNotification,
        testName: String,
        stepIndex: Int
    ) {
        val notification = client.waitForNotification(expected.method, 2000)
            ?: throw AssertionError("Step $stepIndex: Expected notification '${expected.method}' not received")

        logger.debug("Received notification: ${expected.method}")

        // Validate notification params if specified
        if (expected.params != null) {
            val actualParams = notification["params"]
            if (!matchesExpectation(actualParams, expected.params)) {
                throw AssertionError(
                    "Step $stepIndex notification params mismatch:\nExpected: ${expected.params}\nActual: $actualParams"
                )
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun matchesExpectation(actual: Any?, expected: Any?): Boolean {
        return when {
            expected == null -> actual == null
            expected is Map<*, *> -> {
                val expectedMap = expected as Map<String, Any?>

                // Check if expected is a special matcher (before checking if actual is a Map)
                if (expectedMap.containsKey("contains")) {
                    // Special "contains" matcher for strings - checks if actual contains ANY of the patterns
                    val containsValue = expectedMap["contains"]
                    if (containsValue is List<*> && actual is String) {
                        return containsValue.any { pattern ->
                            actual.contains(pattern.toString(), ignoreCase = true)
                        }
                    }
                }

                // Regular map matching - expected keys must be present in actual
                if (actual !is Map<*, *>) return false
                val actualMap = actual as Map<String, Any?>
                expectedMap.all { (key, value) ->
                    actualMap.containsKey(key) && matchesExpectation(actualMap[key], value)
                }
            }
            expected is List<*> -> {
                if (actual !is List<*>) return false
                if (expected.isEmpty()) return actual.isEmpty()

                // For lists, check if actual contains all expected items
                expected.all { expectedItem ->
                    actual.any { actualItem ->
                        matchesExpectation(actualItem, expectedItem)
                    }
                }
            }
            else -> actual == expected
        }
    }
}

/**
 * Test result
 */
sealed class TestResult {
    abstract val testName: String

    data class Success(override val testName: String) : TestResult()
    data class Failure(override val testName: String, val reason: String) : TestResult()
    data class Skipped(override val testName: String, val reason: String) : TestResult()
}
