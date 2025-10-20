// stdlib/src/main/kotlin/org/apache/utlx/stdlib/FunctionRegistry.kt
package org.apache.utlx.stdlib

import com.fasterxml.jackson.annotation.JsonProperty
import java.time.Instant

/**
 * Function registry data model for JSON/YAML export
 *
 * This registry is consumed by:
 * - VS Code extensions
 * - CLI help system
 * - Documentation generators
 * - Other IDE plugins
 */
data class FunctionRegistry(
    @JsonProperty("version")
    val version: String = "1.0.0",

    @JsonProperty("generated")
    val generatedAt: String = Instant.now().toString(),

    @JsonProperty("totalFunctions")
    val totalFunctions: Int,

    @JsonProperty("functions")
    val functions: List<FunctionInfo>,

    @JsonProperty("categories")
    val categories: Map<String, List<FunctionInfo>>
)

/**
 * Information about a single stdlib function
 */
data class FunctionInfo(
    @JsonProperty("name")
    val name: String,

    @JsonProperty("category")
    val category: String,

    @JsonProperty("description")
    val description: String,

    @JsonProperty("signature")
    val signature: String,

    @JsonProperty("minArgs")
    val minArgs: Int = -1,

    @JsonProperty("maxArgs")
    val maxArgs: Int = -1,

    @JsonProperty("parameters")
    val parameters: List<ParameterInfo> = emptyList(),

    @JsonProperty("returns")
    val returns: ReturnInfo? = null,

    @JsonProperty("examples")
    val examples: List<String> = emptyList(),

    @JsonProperty("notes")
    val notes: String? = null,

    @JsonProperty("tags")
    val tags: List<String> = emptyList(),

    @JsonProperty("seeAlso")
    val seeAlso: List<String> = emptyList(),

    @JsonProperty("since")
    val since: String = "1.0",

    @JsonProperty("deprecated")
    val deprecated: Boolean = false,

    @JsonProperty("deprecationMessage")
    val deprecationMessage: String? = null,

    @JsonProperty("isAlias")
    val isAlias: Boolean = false,

    @JsonProperty("aliasOf")
    val aliasOf: String? = null
)

/**
 * Parameter information
 */
data class ParameterInfo(
    @JsonProperty("name")
    val name: String,

    @JsonProperty("type")
    val type: String,

    @JsonProperty("description")
    val description: String
)

/**
 * Return value information
 */
data class ReturnInfo(
    @JsonProperty("type")
    val type: String,

    @JsonProperty("description")
    val description: String
)
