// modules/cli/src/main/kotlin/org/apache/utlx/cli/service/UDMService.kt
package org.apache.utlx.cli.service

import org.apache.utlx.core.udm.UDM
import org.apache.utlx.core.udm.UDMLanguageParser
import org.apache.utlx.core.udm.toUDMLanguage
import org.apache.utlx.formats.avro.AvroSchemaParser
import org.apache.utlx.formats.avro.AvroSchemaSerializer
import org.apache.utlx.formats.csv.CSVDialect
import org.apache.utlx.formats.csv.CSVParser
import org.apache.utlx.formats.csv.CSVSerializer
import org.apache.utlx.formats.csv.RegionalFormat
import org.apache.utlx.formats.jsch.JSONSchemaParser
import org.apache.utlx.formats.jsch.JSONSchemaSerializer
import org.apache.utlx.formats.json.JSONParser
import org.apache.utlx.formats.json.JSONSerializer
import org.apache.utlx.formats.protobuf.ProtobufSchemaParser
import org.apache.utlx.formats.protobuf.ProtobufSchemaSerializer
import org.apache.utlx.formats.xml.XMLParser
import org.apache.utlx.formats.xml.XMLSerializer
import org.apache.utlx.formats.xsd.XSDParser
import org.apache.utlx.formats.xsd.XSDSerializer
import org.apache.utlx.formats.yaml.YAMLParser
import org.apache.utlx.formats.yaml.YAMLSerializer
import java.time.ZonedDateTime

/**
 * Core UDM service - single source of truth for UDM export/import operations
 *
 * Architecture:
 *   UDMService (core logic)
 *         ↑
 *         │
 *    ├── CLI (thin wrapper for I/O and arg parsing)
 *    └── REST API (thin wrapper for HTTP requests/responses)
 *
 * This service extracts core logic from UDMCommand.kt to enable sharing
 * between CLI and REST API with zero code duplication.
 */
class UDMService {

    companion object {
        /**
         * Supported formats for export/import
         */
        val SUPPORTED_FORMATS = listOf(
            // Tier 1
            "json", "xml", "csv", "yaml",
            // Tier 2
            "jsonschema", "xsd", "avro", "protobuf"
        )
    }

    // ==================== DATA CLASSES ====================

    /**
     * Format-specific options for parsing and serialization
     */
    data class FormatOptions(
        // Common options
        val prettyPrint: Boolean = true,

        // CSV options
        val delimiter: Char? = null,
        val hasHeaders: Boolean? = null,
        val regional: String? = null,  // "usa", "european", "french", "swiss"

        // XML options
        val arrayHints: String? = null,  // Comma-separated element names
        val rootName: String? = null,
        val encoding: String? = null,

        // YAML options
        val multiDoc: Boolean? = null,

        // JSON Schema options
        val draft: String? = null,  // "2020-12", "draft-07", etc.

        // XSD options
        val version: String? = null,  // "1.0", "1.1"
        val namespace: String? = null,  // Target namespace
        val pattern: String? = null,  // "russian-doll", "salami-slice", etc.

        // Avro options
        val validate: Boolean? = null
    ) {
        /**
         * Convert to Map<String, Any> for internal use
         */
        fun toMap(): Map<String, Any> = buildMap {
            delimiter?.let { put("delimiter", it) }
            hasHeaders?.let { put("hasHeaders", it) }
            regional?.let { put("regional", it) }
            arrayHints?.let { put("arrayHints", it) }
            rootName?.let { put("rootName", it) }
            encoding?.let { put("encoding", it) }
            multiDoc?.let { put("multiDoc", it) }
            draft?.let { put("draft", it) }
            version?.let { put("version", it) }
            namespace?.let { put("namespace", it) }
            pattern?.let { put("pattern", it) }
            validate?.let { put("validate", it) }
        }
    }

    /**
     * Result of export operation (Format → UDM → .udm)
     */
    data class ExportResult(
        val udmLanguage: String,
        val sourceFormat: String,
        val parsedAt: String
    )

    /**
     * Result of import operation (.udm → UDM → Format)
     */
    data class ImportResult(
        val output: String,
        val targetFormat: String,
        val sourceInfo: Map<String, String>
    )

    /**
     * Result of validate operation
     */
    data class ValidateResult(
        val valid: Boolean,
        val errors: List<String> = emptyList(),
        val udmVersion: String? = null,
        val sourceInfo: Map<String, String> = emptyMap()
    )

    // ==================== PUBLIC API ====================

    /**
     * Export: Format → UDM → .udm
     *
     * Parse input in specified format, convert to UDM, serialize to .udm language
     *
     * @param content Input content in the specified format
     * @param format Input format (json, xml, csv, yaml, jsonschema, xsd, avro, protobuf)
     * @param options Format-specific parsing options
     * @param sourceInfo Optional source metadata (file name, etc.)
     * @return .udm language representation with metadata
     */
    fun export(
        content: String,
        format: String,
        options: FormatOptions = FormatOptions(),
        sourceInfo: Map<String, String> = emptyMap()
    ): ExportResult {
        // Step 1: Parse input to UDM
        val udm = parseInputToUDM(content, format, options.toMap())

        // Step 2: Serialize to .udm language
        val parsedAt = ZonedDateTime.now().toString()
        val fullSourceInfo = sourceInfo + mapOf("parsed-at" to parsedAt)

        val udmLanguage = udm.toUDMLanguage(
            prettyPrint = options.prettyPrint,
            sourceInfo = fullSourceInfo
        )

        return ExportResult(
            udmLanguage = udmLanguage,
            sourceFormat = format,
            parsedAt = parsedAt
        )
    }

    /**
     * Import: .udm → UDM → Format
     *
     * Parse .udm language file, convert to UDM, serialize to specified format
     *
     * @param udmLanguage .udm language content
     * @param targetFormat Output format (json, xml, csv, yaml, jsonschema, xsd, avro, protobuf)
     * @param options Format-specific serialization options
     * @return Serialized output in target format
     */
    fun import(
        udmLanguage: String,
        targetFormat: String,
        options: FormatOptions = FormatOptions()
    ): ImportResult {
        // Step 1: Parse .udm to UDM
        val udm = UDMLanguageParser.parse(udmLanguage)

        // Step 2: Extract source info from header (if present)
        val sourceInfo = extractSourceInfo(udmLanguage)

        // Step 3: Serialize to target format
        val output = serializeUDMToFormat(udm, targetFormat, options.toMap())

        return ImportResult(
            output = output,
            targetFormat = targetFormat,
            sourceInfo = sourceInfo
        )
    }

    /**
     * Validate: Check .udm syntax
     *
     * Parse .udm language file to verify syntax is correct
     *
     * @param udmLanguage .udm language content
     * @return Validation result with errors (if any)
     */
    fun validate(udmLanguage: String): ValidateResult {
        return try {
            // Attempt to parse
            UDMLanguageParser.parse(udmLanguage)

            // Extract metadata
            val sourceInfo = extractSourceInfo(udmLanguage)
            val version = extractVersion(udmLanguage)

            ValidateResult(
                valid = true,
                udmVersion = version,
                sourceInfo = sourceInfo
            )
        } catch (e: Exception) {
            ValidateResult(
                valid = false,
                errors = listOf(e.message ?: "Unknown parsing error")
            )
        }
    }

    // ==================== INTERNAL METHODS ====================

    /**
     * Parse input content to UDM based on format
     * Extracted from UDMCommand.kt for reuse
     */
    private fun parseInputToUDM(content: String, format: String, options: Map<String, Any>): UDM {
        return when (format.lowercase()) {
            "json" -> {
                JSONParser(content).parse()
            }

            "xml" -> {
                val arrayHints = (options["arrayHints"] as? String)
                    ?.split(",")?.map { it.trim() }?.toSet() ?: emptySet()
                XMLParser(content, arrayHints).parse()
            }

            "csv" -> {
                val delimiter = (options["delimiter"] as? Char) ?: ','
                val hasHeaders = (options["hasHeaders"] as? Boolean) ?: true
                val dialect = CSVDialect(delimiter = delimiter)
                CSVParser(content, dialect).parse(hasHeaders)
            }

            "yaml" -> {
                val multiDoc = (options["multiDoc"] as? Boolean) ?: false
                val parseOpts = YAMLParser.ParseOptions(multiDocument = multiDoc)
                YAMLParser().parse(content, parseOpts)
            }

            "jsonschema" -> {
                JSONSchemaParser(content).parse()
            }

            "xsd" -> {
                val arrayHints = (options["arrayHints"] as? String)
                    ?.split(",")?.map { it.trim() }?.toSet() ?: emptySet()
                XSDParser(content, arrayHints).parse()
            }

            "avro" -> {
                AvroSchemaParser().parse(content)
            }

            "protobuf" -> {
                ProtobufSchemaParser().parse(content)
            }

            else -> throw IllegalArgumentException(
                "Unsupported format: $format\n" +
                "Supported formats: ${SUPPORTED_FORMATS.joinToString(", ")}"
            )
        }
    }

    /**
     * Serialize UDM to format
     * Extracted from UDMCommand.kt for reuse
     */
    private fun serializeUDMToFormat(udm: UDM, format: String, options: Map<String, Any>): String {
        return when (format.lowercase()) {
            "json" -> {
                JSONSerializer(prettyPrint = true).serialize(udm)
            }

            "xml" -> {
                val rootName = (options["rootName"] as? String) ?: "root"
                val encoding = options["encoding"] as? String
                XMLSerializer(prettyPrint = true, outputEncoding = encoding)
                    .serialize(udm, rootName)
            }

            "csv" -> {
                val delimiter = (options["delimiter"] as? Char) ?: ','
                val includeHeaders = (options["hasHeaders"] as? Boolean) ?: true
                val regionalFormat = when (options["regional"] as? String) {
                    "usa" -> RegionalFormat.USA
                    "european" -> RegionalFormat.EUROPEAN
                    "french" -> RegionalFormat.FRENCH
                    "swiss" -> RegionalFormat.SWISS
                    else -> RegionalFormat.NONE
                }
                val dialect = CSVDialect(delimiter = delimiter)
                CSVSerializer(dialect, includeHeaders, regionalFormat = regionalFormat)
                    .serialize(udm)
            }

            "yaml" -> {
                val serOpts = YAMLSerializer.SerializeOptions(pretty = true)
                YAMLSerializer().serialize(udm, serOpts)
            }

            "jsonschema" -> {
                val draft = (options["draft"] as? String) ?: "2020-12"
                JSONSchemaSerializer(draft, prettyPrint = true).serialize(udm)
            }

            "xsd" -> {
                val version = (options["version"] as? String) ?: "1.0"
                val pattern = when (options["pattern"] as? String) {
                    "russian-doll" -> XSDSerializer.XSDPattern.RUSSIAN_DOLL
                    "salami-slice" -> XSDSerializer.XSDPattern.SALAMI_SLICE
                    "venetian-blind" -> XSDSerializer.XSDPattern.VENETIAN_BLIND
                    "garden-of-eden" -> XSDSerializer.XSDPattern.GARDEN_OF_EDEN
                    else -> null
                }

                // Note: XSD namespace option is available in FormatOptions but not yet
                // passed to XSDSerializer - may need enhancement in the future
                val serializer = XSDSerializer(
                    pattern = pattern,
                    version = version,
                    prettyPrint = true
                )

                serializer.serialize(udm)
            }

            "avro" -> {
                val namespace = options["namespace"] as? String
                val validate = (options["validate"] as? Boolean) ?: true
                AvroSchemaSerializer(namespace, prettyPrint = true, validate).serialize(udm)
            }

            "protobuf" -> {
                ProtobufSchemaSerializer().serialize(udm)
            }

            else -> throw IllegalArgumentException(
                "Unsupported format: $format\n" +
                "Supported formats: ${SUPPORTED_FORMATS.joinToString(", ")}"
            )
        }
    }

    /**
     * Extract source info from .udm header
     */
    private fun extractSourceInfo(udmLanguage: String): Map<String, String> {
        val sourceInfo = mutableMapOf<String, String>()
        val lines = udmLanguage.lines().take(10)  // Only check first 10 lines

        for (line in lines) {
            val trimmed = line.trim()
            when {
                trimmed.startsWith("@source:") -> {
                    val value = trimmed.substringAfter("@source:").trim().removeSurrounding("\"")
                    sourceInfo["source"] = value
                }
                trimmed.startsWith("@parsed-at:") -> {
                    val value = trimmed.substringAfter("@parsed-at:").trim().removeSurrounding("\"")
                    sourceInfo["parsed-at"] = value
                }
                // Stop at first non-header line
                !trimmed.startsWith("@") && trimmed.isNotEmpty() -> break
            }
        }

        return sourceInfo
    }

    /**
     * Extract UDM version from .udm header
     */
    private fun extractVersion(udmLanguage: String): String? {
        val lines = udmLanguage.lines().take(5)
        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.startsWith("@udm-version:")) {
                return trimmed.substringAfter("@udm-version:").trim()
            }
        }
        return null
    }
}
