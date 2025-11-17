package org.apache.utlx.schema.usdl

import com.fasterxml.jackson.annotation.JsonProperty
import java.time.Instant

/**
 * Registry of all USDL directives with metadata for tooling, documentation, and API exposure.
 *
 * This is the single source of truth for USDL directive information.
 * Used by:
 * - REST API (/api/usdl/directives)
 * - LSP (Language Server Protocol)
 * - IDE plugins (autocomplete, validation, hover tooltips)
 * - MCP tools (AI-assisted schema generation)
 * - CLI tools
 * - Frontend (USDL schema editor)
 *
 * Pattern follows OperatorRegistry and FunctionRegistry for consistency.
 */
object DirectiveRegistry {

    /**
     * Metadata for a USDL directive (JSON-serializable)
     */
    data class DirectiveInfo(
        @JsonProperty("name") val name: String,
        @JsonProperty("tier") val tier: String,
        @JsonProperty("scopes") val scopes: List<String>,
        @JsonProperty("valueType") val valueType: String,
        @JsonProperty("required") val required: Boolean,
        @JsonProperty("description") val description: String,
        @JsonProperty("supportedFormats") val supportedFormats: List<String>,
        @JsonProperty("examples") val examples: List<String>,
        @JsonProperty("syntax") val syntax: String,
        @JsonProperty("tooltip") val tooltip: String,
        @JsonProperty("seeAlso") val seeAlso: List<String>
    )

    /**
     * Format information (JSON-serializable)
     */
    data class FormatInfo(
        @JsonProperty("name") val name: String,
        @JsonProperty("abbreviation") val abbreviation: String,
        @JsonProperty("tier1Support") val tier1Support: Int,
        @JsonProperty("tier2Support") val tier2Support: Int,
        @JsonProperty("tier3Support") val tier3Support: Int,
        @JsonProperty("overallSupport") val overallSupport: Int,
        @JsonProperty("supportedDirectives") val supportedDirectives: List<String>,
        @JsonProperty("notes") val notes: String,
        @JsonProperty("domain") val domain: String
    )

    /**
     * Complete directive registry data (JSON-serializable)
     */
    data class DirectiveRegistryData(
        @JsonProperty("version") val version: String,
        @JsonProperty("generatedAt") val generatedAt: String,
        @JsonProperty("totalDirectives") val totalDirectives: Int,
        @JsonProperty("directives") val directives: List<DirectiveInfo>,
        @JsonProperty("tiers") val tiers: Map<String, List<DirectiveInfo>>,
        @JsonProperty("scopes") val scopes: Map<String, List<DirectiveInfo>>,
        @JsonProperty("formats") val formats: Map<String, FormatInfo>
    )

    /**
     * Convert USDL10.Directive to DirectiveInfo (JSON-serializable)
     */
    private fun toDirectiveInfo(directive: USDL10.Directive): DirectiveInfo {
        return DirectiveInfo(
            name = directive.name,
            tier = directive.tier.name.lowercase(),
            scopes = directive.scopes.map { it.name },
            valueType = directive.valueType,
            required = directive.required,
            description = directive.description,
            supportedFormats = directive.supportedFormats.toList(),
            examples = directive.examples,
            syntax = directive.syntax,
            tooltip = buildTooltip(directive),
            seeAlso = directive.seeAlso
        )
    }

    /**
     * Build tooltip for IDE hover
     */
    private fun buildTooltip(directive: USDL10.Directive): String {
        val required = if (directive.required) " (REQUIRED)" else ""
        val scopeList = directive.scopes.joinToString(", ")
        return "${directive.description}$required\n\nScopes: $scopeList\nType: ${directive.valueType}"
    }

    /**
     * Convert format metadata to FormatInfo
     */
    private fun toFormatInfo(abbreviation: String, metadata: FormatCompatibility.FormatMetadata): FormatInfo {
        // Get all directives supported by this format
        val supportedDirectives = USDL10.ALL_DIRECTIVES_FLAT
            .filter { abbreviation in it.supportedFormats }
            .map { it.name }

        return FormatInfo(
            name = metadata.name,
            abbreviation = abbreviation,
            tier1Support = metadata.tier1Support,
            tier2Support = metadata.tier2Support,
            tier3Support = metadata.tier3Support,
            overallSupport = metadata.overallSupport,
            supportedDirectives = supportedDirectives,
            notes = metadata.notes,
            domain = metadata.domain
        )
    }

    /**
     * Export the complete directive registry
     */
    fun exportRegistry(): DirectiveRegistryData {
        val allDirectives = USDL10.ALL_DIRECTIVES_FLAT.map { toDirectiveInfo(it) }

        // Group by tier
        val tierMap = allDirectives.groupBy { it.tier }

        // Group by scope
        val scopeMap = mutableMapOf<String, MutableList<DirectiveInfo>>()
        allDirectives.forEach { directive ->
            directive.scopes.forEach { scope ->
                scopeMap.getOrPut(scope) { mutableListOf() }.add(directive)
            }
        }

        // Build format map
        val formatMap = FormatCompatibility.FORMATS.mapValues { (abbrev, metadata) ->
            toFormatInfo(abbrev, metadata)
        }

        return DirectiveRegistryData(
            version = USDL10.VERSION,
            generatedAt = Instant.now().toString(),
            totalDirectives = allDirectives.size,
            directives = allDirectives,
            tiers = tierMap,
            scopes = scopeMap,
            formats = formatMap
        )
    }

    /**
     * Get directives by tier
     */
    fun getDirectivesByTier(tier: String): List<DirectiveInfo> {
        val tierEnum = when (tier.lowercase()) {
            "core", "tier1", "1" -> USDL10.Tier.CORE
            "common", "tier2", "2" -> USDL10.Tier.COMMON
            "format_specific", "formatspecific", "tier3", "3" -> USDL10.Tier.FORMAT_SPECIFIC
            "reserved", "tier4", "4" -> USDL10.Tier.RESERVED
            else -> null
        }

        return if (tierEnum != null) {
            USDL10.getDirectivesByTier(tierEnum).map { toDirectiveInfo(it) }
        } else {
            emptyList()
        }
    }

    /**
     * Get directives by scope
     */
    fun getDirectivesByScope(scope: String): List<DirectiveInfo> {
        val scopeEnum = try {
            USDL10.Scope.valueOf(scope.uppercase())
        } catch (e: IllegalArgumentException) {
            null
        }

        return if (scopeEnum != null) {
            USDL10.getDirectivesByScope(scopeEnum).map { toDirectiveInfo(it) }
        } else {
            emptyList()
        }
    }

    /**
     * Get directives by format
     */
    fun getDirectivesByFormat(format: String): List<DirectiveInfo> {
        return USDL10.ALL_DIRECTIVES_FLAT
            .filter { format in it.supportedFormats }
            .map { toDirectiveInfo(it) }
    }

    /**
     * Get a specific directive by name
     */
    fun getDirective(name: String): DirectiveInfo? {
        val directive = USDL10.getDirective(name)
        return directive?.let { toDirectiveInfo(it) }
    }

    /**
     * Get format information
     */
    fun getFormatInfo(format: String): FormatInfo? {
        val metadata = FormatCompatibility.getFormatMetadata(format)
        return metadata?.let { toFormatInfo(format, it) }
    }

    /**
     * Get all format abbreviations
     */
    fun getAllFormats(): List<String> {
        return FormatCompatibility.getAllFormatAbbreviations()
    }

    /**
     * Get directives grouped by tier with counts
     */
    fun getTierSummary(): Map<String, Int> {
        return USDL10.ALL_DIRECTIVES.mapValues { it.value.size }
            .mapKeys { it.key.name.lowercase() }
    }

    /**
     * Get directives grouped by scope with counts
     */
    fun getScopeSummary(): Map<String, Int> {
        val scopeCounts = mutableMapOf<String, Int>()
        USDL10.ALL_DIRECTIVES_FLAT.forEach { directive ->
            directive.scopes.forEach { scope ->
                scopeCounts[scope.name] = scopeCounts.getOrDefault(scope.name, 0) + 1
            }
        }
        return scopeCounts
    }

    /**
     * Get formats grouped by domain
     */
    fun getFormatsByDomain(domain: String): List<FormatInfo> {
        return FormatCompatibility.getFormatsByDomain(domain)
            .map { (abbrev, metadata) -> toFormatInfo(abbrev, metadata) }
    }

    /**
     * Search directives by keyword (name, description, examples)
     */
    fun searchDirectives(keyword: String): List<DirectiveInfo> {
        val lowerKeyword = keyword.lowercase()
        return USDL10.ALL_DIRECTIVES_FLAT
            .filter { directive ->
                directive.name.lowercase().contains(lowerKeyword) ||
                directive.description.lowercase().contains(lowerKeyword) ||
                directive.examples.any { it.lowercase().contains(lowerKeyword) }
            }
            .map { toDirectiveInfo(it) }
    }

    /**
     * Get statistics about the directive registry
     */
    data class DirectiveStatistics(
        @JsonProperty("totalDirectives") val totalDirectives: Int,
        @JsonProperty("tierCounts") val tierCounts: Map<String, Int>,
        @JsonProperty("scopeCounts") val scopeCounts: Map<String, Int>,
        @JsonProperty("totalFormats") val totalFormats: Int,
        @JsonProperty("averageCompatibility") val averageCompatibility: Int
    )

    /**
     * Get registry statistics
     */
    fun getStatistics(): DirectiveStatistics {
        return DirectiveStatistics(
            totalDirectives = USDL10.ALL_DIRECTIVES_FLAT.size,
            tierCounts = getTierSummary(),
            scopeCounts = getScopeSummary(),
            totalFormats = FormatCompatibility.FORMATS.size,
            averageCompatibility = FormatCompatibility.getAverageCompatibility()
        )
    }
}
