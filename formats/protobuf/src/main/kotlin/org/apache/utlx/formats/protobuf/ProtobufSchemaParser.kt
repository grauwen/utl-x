package org.apache.utlx.formats.protobuf

import org.apache.utlx.core.udm.UDM

/**
 * Protobuf Schema Parser - Converts Protocol Buffers (Proto3) Schema to USDL
 *
 * Parses .proto files and extracts all type definitions into USDL format.
 * Supports Proto3 syntax only, schema operations only (no binary data).
 *
 * Features:
 * - Multi-type schema extraction (N messages in .proto → N types in %types)
 * - Message, enum, oneof, map, repeated field parsing
 * - Field number preservation
 * - Reserved fields/numbers extraction
 * - Documentation comment extraction
 * - Validation (proto3 only, no proto2)
 *
 * Example:
 * ```kotlin
 * val parser = ProtobufSchemaParser()
 * val usdl = parser.parse(protoFileContent)
 * ```
 *
 * Proto3 → USDL Mapping:
 * - package → %namespace
 * - messages/enums → %types
 * - message → %kind: "structure"
 * - enum → %kind: "enumeration"
 * - field number → %fieldNumber
 * - repeated → %array: true
 * - map<K,V> → %map: true, %keyType, %itemType
 * - oneof → %oneof group
 * - reserved → %reserved, %reservedNames
 * - comments → %documentation
 */
class ProtobufSchemaParser {

    /**
     * Parse Proto3 schema text to USDL
     *
     * @param protoSource The .proto file content as text
     * @return USDL representation with %namespace and %types
     * @throws IllegalArgumentException if proto is not valid proto3
     */
    fun parse(protoSource: String): UDM {
        val protoFile = parseProtoText(protoSource)
        return buildUSDLFromProtoFile(protoFile)
    }

    /**
     * Simple text-based proto parser
     *
     * Parses proto3 syntax to our ProtoFile model.
     * This is a simplified parser focused on the common subset we need.
     */
    private fun parseProtoText(source: String): ProtoFile {
        var packageName: String? = null
        val messages = mutableListOf<ProtoMessage>()
        val enums = mutableListOf<ProtoEnum>()

        val lines = source.lines()
        var i = 0

        while (i < lines.size) {
            val line = lines[i].trim()

            when {
                line.startsWith("syntax =") -> {
                    val syntax = line.substringAfter("\"").substringBefore("\"")
                    if (syntax != "proto3") {
                        throw IllegalArgumentException("Only proto3 is supported, found: $syntax")
                    }
                }
                line.startsWith("package ") -> {
                    packageName = line.substringAfter("package ").substringBefore(";").trim()
                }
                line.startsWith("enum ") -> {
                    val (enum, nextIndex) = parseEnum(lines, i)
                    enums.add(enum)
                    i = nextIndex - 1
                }
                line.startsWith("message ") -> {
                    val (message, nextIndex) = parseMessage(lines, i)
                    messages.add(message)
                    i = nextIndex - 1
                }
            }
            i++
        }

        return ProtoFile(
            packageName = packageName,
            messages = messages,
            enums = enums
        )
    }

    /**
     * Parse enum definition from proto text
     */
    private fun parseEnum(lines: List<String>, startIndex: Int): Pair<ProtoEnum, Int> {
        val headerLine = lines[startIndex].trim()
        val enumName = headerLine.substringAfter("enum ").substringBefore("{").trim()

        val values = mutableListOf<ProtoEnumValue>()
        var i = startIndex + 1
        var documentation: String? = null

        while (i < lines.size) {
            val line = lines[i].trim()

            when {
                line.startsWith("//") -> {
                    // Extract documentation
                    val doc = line.substringAfter("//").trim()
                    documentation = if (documentation == null) doc else "$documentation\n$doc"
                }
                line.contains("=") && !line.startsWith("}") -> {
                    // Parse enum value: VALUE_NAME = number;
                    val valueName = line.substringBefore("=").trim()
                    val numberStr = line.substringAfter("=").substringBefore(";").trim()
                    val number = numberStr.toIntOrNull() ?: 0

                    values.add(ProtoEnumValue(
                        name = valueName,
                        number = number,
                        documentation = documentation
                    ))
                    documentation = null
                }
                line.startsWith("}") -> {
                    return ProtoEnum(name = enumName, values = values) to (i + 1)
                }
            }
            i++
        }

        return ProtoEnum(name = enumName, values = values) to i
    }

    /**
     * Parse message definition from proto text
     */
    private fun parseMessage(lines: List<String>, startIndex: Int): Pair<ProtoMessage, Int> {
        val headerLine = lines[startIndex].trim()
        val messageName = headerLine.substringAfter("message ").substringBefore("{").trim()

        val fields = mutableListOf<ProtoField>()
        val oneofs = mutableListOf<ProtoOneof>()
        val nestedMessages = mutableListOf<ProtoMessage>()
        val nestedEnums = mutableListOf<ProtoEnum>()
        var reserved: ProtoReserved? = null
        var documentation: String? = null

        var i = startIndex + 1

        while (i < lines.size) {
            val line = lines[i].trim()

            when {
                line.startsWith("//") -> {
                    val doc = line.substringAfter("//").trim()
                    documentation = if (documentation == null) doc else "$documentation\n$doc"
                }
                line.startsWith("reserved ") -> {
                    reserved = parseReserved(line, reserved)
                }
                line.startsWith("oneof ") -> {
                    val (oneof, nextIndex) = parseOneof(lines, i)
                    oneofs.add(oneof)
                    i = nextIndex - 1
                }
                line.startsWith("message ") -> {
                    val (nestedMsg, nextIndex) = parseMessage(lines, i)
                    nestedMessages.add(nestedMsg)
                    i = nextIndex - 1
                }
                line.startsWith("enum ") -> {
                    val (nestedEnum, nextIndex) = parseEnum(lines, i)
                    nestedEnums.add(nestedEnum)
                    i = nextIndex - 1
                }
                line.startsWith("repeated ") || line.startsWith("map<") ||
                (line.contains("=") && !line.startsWith("}")) -> {
                    // Parse field
                    val field = parseField(line, documentation)
                    if (field != null) {
                        fields.add(field)
                        documentation = null
                    }
                }
                line.startsWith("}") -> {
                    return ProtoMessage(
                        name = messageName,
                        fields = fields,
                        oneofs = oneofs,
                        nestedMessages = nestedMessages,
                        nestedEnums = nestedEnums,
                        reserved = reserved
                    ) to (i + 1)
                }
            }
            i++
        }

        return ProtoMessage(
            name = messageName,
            fields = fields,
            oneofs = oneofs,
            nestedMessages = nestedMessages,
            nestedEnums = nestedEnums,
            reserved = reserved
        ) to i
    }

    /**
     * Parse oneof group from proto text
     */
    private fun parseOneof(lines: List<String>, startIndex: Int): Pair<ProtoOneof, Int> {
        val headerLine = lines[startIndex].trim()
        val oneofName = headerLine.substringAfter("oneof ").substringBefore("{").trim()

        val fields = mutableListOf<ProtoField>()
        var i = startIndex + 1
        var documentation: String? = null

        while (i < lines.size) {
            val line = lines[i].trim()

            when {
                line.startsWith("//") -> {
                    val doc = line.substringAfter("//").trim()
                    documentation = if (documentation == null) doc else "$documentation\n$doc"
                }
                line.contains("=") && !line.startsWith("}") -> {
                    val field = parseField(line, documentation, oneofGroup = oneofName)
                    if (field != null) {
                        fields.add(field)
                        documentation = null
                    }
                }
                line.startsWith("}") -> {
                    return ProtoOneof(name = oneofName, fields = fields) to (i + 1)
                }
            }
            i++
        }

        return ProtoOneof(name = oneofName, fields = fields) to i
    }

    /**
     * Parse field from proto text
     */
    private fun parseField(line: String, documentation: String?, oneofGroup: String? = null): ProtoField? {
        if (!line.contains("=")) return null

        val parts = line.substringBefore(";").trim().split(Regex("\\s+"))
        if (parts.size < 3) return null

        var label = ProtoField.FieldLabel.SINGULAR
        var type = ""
        var name = ""
        var number = 0
        var mapKeyType: String? = null
        var mapValueType: String? = null

        when {
            line.startsWith("repeated ") -> {
                label = ProtoField.FieldLabel.REPEATED
                type = parts[1]
                name = parts[2]
                number = parts.last().toIntOrNull() ?: 0
            }
            line.startsWith("map<") -> {
                label = ProtoField.FieldLabel.MAP
                val mapTypes = line.substringAfter("map<").substringBefore(">")
                val keyValue = mapTypes.split(",").map { it.trim() }
                mapKeyType = keyValue.getOrNull(0)
                mapValueType = keyValue.getOrNull(1)
                type = "map"
                name = line.substringAfter(">").trim().split(Regex("\\s+"))[0]
                number = parts.last().toIntOrNull() ?: 0
            }
            else -> {
                type = parts[0]
                name = parts[1]
                number = parts.last().toIntOrNull() ?: 0
            }
        }

        return ProtoField(
            name = name,
            number = number,
            type = type,
            label = label,
            mapKeyType = mapKeyType,
            mapValueType = mapValueType,
            documentation = documentation,
            oneofGroup = oneofGroup
        )
    }

    /**
     * Parse reserved declaration
     */
    private fun parseReserved(line: String, existing: ProtoReserved?): ProtoReserved {
        val numbers = mutableListOf<Int>()
        val ranges = mutableListOf<IntRange>()
        val names = mutableListOf<String>()

        if (existing != null) {
            numbers.addAll(existing.numbers)
            ranges.addAll(existing.ranges)
            names.addAll(existing.names)
        }

        val content = line.substringAfter("reserved ").substringBefore(";").trim()

        if (content.contains("\"")) {
            // Reserved names
            val nameMatches = Regex("\"([^\"]+)\"").findAll(content)
            names.addAll(nameMatches.map { it.groupValues[1] })
        } else {
            // Reserved numbers/ranges
            content.split(",").forEach { part ->
                val trimmed = part.trim()
                if (trimmed.contains(" to ")) {
                    val rangeParts = trimmed.split(" to ")
                    val start = rangeParts[0].trim().toIntOrNull() ?: 0
                    val end = rangeParts[1].trim().toIntOrNull() ?: 0
                    ranges.add(start..end)
                } else {
                    trimmed.toIntOrNull()?.let { numbers.add(it) }
                }
            }
        }

        return ProtoReserved(
            numbers = numbers,
            ranges = ranges,
            names = names
        )
    }

    /**
     * Build USDL from ProtoFile model
     */
    private fun buildUSDLFromProtoFile(protoFile: ProtoFile): UDM {
        val types = mutableMapOf<String, UDM>()

        // Add enums
        protoFile.enums.forEach { enum ->
            types[enum.name] = buildEnumUSDL(enum)
        }

        // Add messages
        protoFile.messages.forEach { message ->
            types[message.name] = buildMessageUSDL(message)
        }

        val properties = mutableMapOf<String, UDM>()

        if (protoFile.packageName != null) {
            properties["%namespace"] = UDM.Scalar(protoFile.packageName)
        }

        properties["%types"] = UDM.Object(properties = types)

        return UDM.Object(properties = properties)
    }

    /**
     * Build USDL for enum type
     */
    private fun buildEnumUSDL(enum: ProtoEnum): UDM {
        val properties = mutableMapOf<String, UDM>(
            "%kind" to UDM.Scalar("enumeration")
        )

        if (enum.documentation != null) {
            properties["%documentation"] = UDM.Scalar(enum.documentation)
        }

        val values = enum.values.map { value ->
            val valueProps = mutableMapOf<String, UDM>(
                "%value" to UDM.Scalar(value.name),
                "%ordinal" to UDM.Scalar(value.number)
            )
            if (value.documentation != null) {
                valueProps["%documentation"] = UDM.Scalar(value.documentation)
            }
            UDM.Object(properties = valueProps)
        }

        properties["%values"] = UDM.Array(elements = values)

        return UDM.Object(properties = properties)
    }

    /**
     * Build USDL for message type
     */
    private fun buildMessageUSDL(message: ProtoMessage): UDM {
        val properties = mutableMapOf<String, UDM>(
            "%kind" to UDM.Scalar("structure")
        )

        if (message.documentation != null) {
            properties["%documentation"] = UDM.Scalar(message.documentation)
        }

        // Build fields (including oneof fields)
        // Sort by field number to preserve original declaration order
        val allFields = mutableListOf<ProtoField>()
        allFields.addAll(message.fields)
        message.oneofs.forEach { oneof ->
            allFields.addAll(oneof.fields)
        }
        allFields.sortBy { it.number }

        val fieldUdms = allFields.map { field ->
            buildFieldUSDL(field)
        }

        properties["%fields"] = UDM.Array(elements = fieldUdms)

        // Add reserved if present
        if (message.reserved != null) {
            val reserved = message.reserved
            if (reserved.numbers.isNotEmpty() || reserved.ranges.isNotEmpty()) {
                val reservedItems = mutableListOf<UDM>()
                reservedItems.addAll(reserved.numbers.map { UDM.Scalar(it) })
                reservedItems.addAll(reserved.ranges.map { range ->
                    UDM.Object(properties = mapOf(
                        "from" to UDM.Scalar(range.first),
                        "to" to UDM.Scalar(range.last)
                    ))
                })
                properties["%reserved"] = UDM.Array(elements = reservedItems)
            }
            if (reserved.names.isNotEmpty()) {
                properties["%reservedNames"] = UDM.Array(
                    elements = reserved.names.map { UDM.Scalar(it) }
                )
            }
        }

        return UDM.Object(properties = properties)
    }

    /**
     * Build USDL for field
     */
    private fun buildFieldUSDL(field: ProtoField): UDM {
        val properties = mutableMapOf<String, UDM>(
            "%name" to UDM.Scalar(field.name),
            "%fieldNumber" to UDM.Scalar(field.number)
        )

        when (field.label) {
            ProtoField.FieldLabel.REPEATED -> {
                properties["%type"] = UDM.Scalar(mapProtoTypeToUSDL(field.type))
                properties["%array"] = UDM.Scalar(true)
            }
            ProtoField.FieldLabel.MAP -> {
                properties["%type"] = UDM.Scalar("map")
                properties["%map"] = UDM.Scalar(true)
                properties["%keyType"] = UDM.Scalar(mapProtoTypeToUSDL(field.mapKeyType!!))
                properties["%itemType"] = UDM.Scalar(mapProtoTypeToUSDL(field.mapValueType!!))
            }
            ProtoField.FieldLabel.SINGULAR -> {
                val (usdlType, size) = mapProtoTypeToUSDLWithSize(field.type)
                properties["%type"] = UDM.Scalar(usdlType)
                if (size != null) {
                    properties["%size"] = UDM.Scalar(size)
                }
            }
        }

        if (field.oneofGroup != null) {
            properties["%oneof"] = UDM.Scalar(field.oneofGroup)
        }

        if (field.documentation != null) {
            properties["%documentation"] = UDM.Scalar(field.documentation)
        }

        return UDM.Object(properties = properties)
    }

    /**
     * Map Proto3 type to USDL type
     */
    private fun mapProtoTypeToUSDL(protoType: String): String {
        return when (protoType) {
            "string" -> "string"
            "bool" -> "boolean"
            "bytes" -> "bytes"
            "int32", "sint32", "sfixed32", "uint32", "fixed32" -> "integer"
            "int64", "sint64", "sfixed64", "uint64", "fixed64" -> "integer"
            "double" -> "number"
            "float" -> "float"
            else -> protoType // Assume it's a custom message/enum type
        }
    }

    /**
     * Map Proto3 type to USDL type with size annotation
     */
    private fun mapProtoTypeToUSDLWithSize(protoType: String): Pair<String, Int?> {
        return when (protoType) {
            "string" -> "string" to null
            "bool" -> "boolean" to null
            "bytes" -> "bytes" to null
            "int32", "sint32", "sfixed32", "uint32", "fixed32" -> "integer" to 32
            "int64", "sint64", "sfixed64", "uint64", "fixed64" -> "integer" to 64
            "double" -> "number" to null
            "float" -> "float" to null
            else -> protoType to null // Custom type
        }
    }
}
