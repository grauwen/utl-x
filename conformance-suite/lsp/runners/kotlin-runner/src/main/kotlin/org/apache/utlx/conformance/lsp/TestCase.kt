package org.apache.utlx.conformance.lsp

import com.fasterxml.jackson.annotation.JsonProperty

/**
 * Test case structure parsed from YAML
 */
data class TestCase(
    val name: String,
    val description: String,
    val category: String,
    val tags: List<String> = emptyList(),
    val documents: Map<String, TestDocument> = emptyMap(),
    val sequence: List<TestStep>
)

/**
 * Document fixture
 */
data class TestDocument(
    val uri: String,
    val languageId: String,
    val version: Int,
    val text: String
)

/**
 * Test step (request or notification)
 */
data class TestStep(
    val type: StepType,
    val method: String,
    val params: Any? = null,
    val expect: ExpectedResult? = null,
    @JsonProperty("expect_notification")
    val expectNotification: ExpectedNotification? = null
)

enum class StepType {
    @JsonProperty("request")
    REQUEST,

    @JsonProperty("notification")
    NOTIFICATION
}

/**
 * Expected result for a request
 */
data class ExpectedResult(
    val result: Any? = null,
    val error: Any? = null
)

/**
 * Expected notification from server
 */
data class ExpectedNotification(
    val method: String,
    val params: Any? = null
)
