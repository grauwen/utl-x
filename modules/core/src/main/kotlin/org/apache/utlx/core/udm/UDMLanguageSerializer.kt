package org.apache.utlx.core.udm

import java.time.format.DateTimeFormatter

/**
 * Serializes UDM structures to UDM Language format (.udm files)
 *
 * UDM Language is a meta-format that preserves complete UDM model state including:
 * - Type information (Scalar, Array, Object, DateTime, etc.)
 * - Metadata (source info, line numbers, validation state)
 * - Attributes (XML attributes, hints)
 * - Element names (XML context)
 *
 * This is different from standard YAML/JSON serialization which loses UDM metadata.
 *
 * Example:
 * ```kotlin
 * val udm = UDM.Object(...)
 * val udmLang = UDMLanguageSerializer().serialize(udm)
 * File("output.udm").writeText(udmLang)
 * ```
 */
class UDMLanguageSerializer(
    private val prettyPrint: Boolean = true,
    private val indentSize: Int = 2
) {

    /**
     * Serialize UDM to UDM Language format
     */
    fun serialize(udm: UDM, sourceInfo: Map<String, String> = emptyMap()): String {
        val sb = StringBuilder()

        // Header
        sb.append("@udm-version: 1.0\n")
        if (sourceInfo.isNotEmpty()) {
            sourceInfo["source"]?.let { sb.append("@source: \"$it\"\n") }
            sourceInfo["parsed-at"]?.let { sb.append("@parsed-at: \"$it\"\n") }
        }
        sb.append("\n")

        // Body
        serializeValue(udm, sb, 0)

        return sb.toString()
    }

    private fun serializeValue(udm: UDM, sb: StringBuilder, depth: Int) {
        when (udm) {
            is UDM.Scalar -> serializeScalar(udm, sb)
            is UDM.Array -> serializeArray(udm, sb, depth)
            is UDM.Object -> serializeObject(udm, sb, depth)
            is UDM.DateTime -> sb.append("@DateTime(\"${udm.instant}\")")
            is UDM.Date -> sb.append("@Date(\"${udm.date}\")")
            is UDM.LocalDateTime -> sb.append("@LocalDateTime(\"${udm.dateTime.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)}\")")
            is UDM.Time -> sb.append("@Time(\"${udm.time.format(DateTimeFormatter.ISO_LOCAL_TIME)}\")")
            is UDM.Binary -> serializeBinary(udm, sb)
            is UDM.Lambda -> sb.append("@Lambda()")
        }
    }

    private fun serializeScalar(scalar: UDM.Scalar, sb: StringBuilder) {
        when (val value = scalar.value) {
            null -> sb.append("null")
            is String -> sb.append("\"${escapeString(value)}\"")
            is Boolean -> sb.append(value.toString())
            is Number -> sb.append(value.toString())
            else -> {
                // Explicit type annotation for non-standard types
                sb.append("@Scalar<String>(\"${escapeString(value.toString())}\")")
            }
        }
    }

    private fun serializeArray(array: UDM.Array, sb: StringBuilder, depth: Int) {
        if (array.elements.isEmpty()) {
            sb.append("[]")
            return
        }

        sb.append("[")
        if (prettyPrint) sb.append("\n")

        array.elements.forEachIndexed { index, element ->
            if (prettyPrint) sb.append(indent(depth + 1))
            serializeValue(element, sb, depth + 1)
            if (index < array.elements.size - 1) {
                sb.append(",")
            }
            if (prettyPrint) sb.append("\n")
        }

        if (prettyPrint) sb.append(indent(depth))
        sb.append("]")
    }

    private fun serializeObject(obj: UDM.Object, sb: StringBuilder, depth: Int) {
        // Check if we need explicit @Object annotation
        val needsAnnotation = obj.name != null || obj.metadata.isNotEmpty()

        if (needsAnnotation) {
            sb.append("@Object")
            serializeObjectMeta(obj, sb, depth)
            sb.append(" ")
        }

        sb.append("{")
        if (prettyPrint) sb.append("\n")

        // Attributes section
        if (obj.attributes.isNotEmpty()) {
            if (prettyPrint) sb.append(indent(depth + 1))
            sb.append("attributes: {")
            if (prettyPrint) sb.append("\n")

            obj.attributes.entries.forEachIndexed { index, (key, value) ->
                if (prettyPrint) sb.append(indent(depth + 2))
                sb.append("$key: \"${escapeString(value)}\"")
                if (index < obj.attributes.size - 1) {
                    sb.append(",")
                }
                if (prettyPrint) sb.append("\n")
            }

            if (prettyPrint) sb.append(indent(depth + 1))
            sb.append("},")
            if (prettyPrint) sb.append("\n")
        }

        // Properties section
        if (obj.attributes.isNotEmpty() || needsAnnotation) {
            // Need explicit properties label
            if (prettyPrint) sb.append(indent(depth + 1))
            sb.append("properties: {")
            if (prettyPrint) sb.append("\n")
            serializeProperties(obj.properties, sb, depth + 2)
            if (prettyPrint) sb.append(indent(depth + 1))
            sb.append("}")
            if (prettyPrint) sb.append("\n")
        } else {
            // Shorthand: properties directly in object
            serializeProperties(obj.properties, sb, depth + 1)
        }

        if (prettyPrint) sb.append(indent(depth))
        sb.append("}")
    }

    private fun serializeObjectMeta(obj: UDM.Object, sb: StringBuilder, depth: Int) {
        if (obj.name == null && obj.metadata.isEmpty()) return

        sb.append("(")
        if (prettyPrint) sb.append("\n")

        val entries = mutableListOf<String>()

        // Name
        obj.name?.let {
            entries.add("${indent(depth + 1)}name: \"${escapeString(it)}\"")
        }

        // Metadata
        if (obj.metadata.isNotEmpty()) {
            val metadataStr = StringBuilder()
            metadataStr.append("${indent(depth + 1)}metadata: {")
            obj.metadata.entries.forEachIndexed { index, (key, value) ->
                metadataStr.append("$key: \"${escapeString(value)}\"")
                if (index < obj.metadata.size - 1) {
                    metadataStr.append(", ")
                }
            }
            metadataStr.append("}")
            entries.add(metadataStr.toString())
        }

        sb.append(entries.joinToString(",\n"))
        if (prettyPrint) sb.append("\n")
        if (prettyPrint) sb.append(indent(depth))
        sb.append(")")
    }

    private fun serializeProperties(properties: Map<String, UDM>, sb: StringBuilder, depth: Int) {
        properties.entries.forEachIndexed { index, (key, value) ->
            if (prettyPrint) sb.append(indent(depth))

            // Quote key if it contains special characters or is a reserved word
            val quotedKey = if (needsQuoting(key)) "\"${escapeString(key)}\"" else key
            sb.append("$quotedKey: ")

            serializeValue(value, sb, depth)

            if (index < properties.size - 1) {
                sb.append(",")
            }
            if (prettyPrint) sb.append("\n")
        }
    }

    private fun serializeBinary(binary: UDM.Binary, sb: StringBuilder) {
        // For now, just serialize size and indicate it's binary
        sb.append("@Binary(size: ${binary.data.size})")
        // TODO: Support base64 inline or external reference
    }

    private fun indent(depth: Int): String {
        return if (prettyPrint) " ".repeat(depth * indentSize) else ""
    }

    private fun escapeString(str: String): String {
        return str
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
    }

    private fun needsQuoting(key: String): Boolean {
        // Quote if key contains special characters or starts with @
        return key.contains(Regex("[^a-zA-Z0-9_]")) || key.startsWith("@")
    }
}

/**
 * Extension function for convenient UDM serialization
 */
fun UDM.toUDMLanguage(prettyPrint: Boolean = true, sourceInfo: Map<String, String> = emptyMap()): String {
    return UDMLanguageSerializer(prettyPrint).serialize(this, sourceInfo)
}
