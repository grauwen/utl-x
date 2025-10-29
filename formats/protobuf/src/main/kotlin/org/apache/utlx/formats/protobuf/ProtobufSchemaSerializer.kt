package org.apache.utlx.formats.protobuf

import org.apache.utlx.core.udm.UDM

/**
 * Protobuf Schema Serializer - Converts USDL to Protocol Buffers (Proto3) Schema
 *
 * Converts Universal Schema Definition Language (USDL) to .proto files.
 * Supports Proto3 syntax only, schema operations only (no binary data).
 *
 * Features:
 * - Multi-type schema support (N types in %types → N messages in .proto)
 * - Message, enum, oneof, map, repeated field generation
 * - Field number preservation and auto-assignment
 * - Reserved fields/numbers
 * - Documentation comment generation
 * - Validation (enum first value = 0, field number ranges)
 *
 * Example:
 * ```kotlin
 * val serializer = ProtobufSchemaSerializer()
 * val protoText = serializer.serialize(usdlSchema)
 * ```
 *
 * USDL → Proto3 Mapping:
 * - %namespace → package
 * - %types → messages/enums
 * - %kind: "structure" → message
 * - %kind: "enumeration" → enum
 * - %fieldNumber → field number
 * - %array: true → repeated
 * - %map: true → map<K,V>
 * - %oneof → oneof group
 * - %reserved → reserved declarations
 * - %documentation → comments
 */
class ProtobufSchemaSerializer {

    /**
     * Serialize USDL schema to Proto3 .proto text format
     *
     * @param usdl The USDL schema (must be UDM.Object with %types)
     * @return The formatted .proto file content
     * @throws IllegalArgumentException if USDL is not a valid schema
     */
    fun serialize(usdl: UDM): String {
        if (usdl !is UDM.Object) {
            throw IllegalArgumentException("USDL schema must be an object, got: ${usdl::class.simpleName}")
        }

        // Extract USDL directives
        val namespace = extractNamespace(usdl)
        val types = extractTypes(usdl)

        if (types.isEmpty()) {
            throw IllegalArgumentException("USDL schema must have %types directive with at least one type")
        }

        // Build ProtoFile model
        val protoFile = buildProtoFile(namespace, types)

        // Render to .proto text
        return renderProtoFile(protoFile)
    }

    /**
     * Extract namespace from USDL %namespace directive
     */
    private fun extractNamespace(usdl: UDM.Object): String? {
        val namespaceValue = usdl.properties["%namespace"] as? UDM.Scalar
        return namespaceValue?.value as? String
    }

    /**
     * Extract types from USDL %types directive
     */
    private fun extractTypes(usdl: UDM.Object): Map<String, UDM.Object> {
        val typesValue = usdl.properties["%types"] as? UDM.Object
            ?: return emptyMap()

        return typesValue.properties.mapNotNull { (name, typeUdm) ->
            if (typeUdm is UDM.Object) {
                name to typeUdm
            } else {
                null
            }
        }.toMap()
    }

    /**
     * Build ProtoFile model from USDL types
     */
    private fun buildProtoFile(namespace: String?, types: Map<String, UDM.Object>): ProtoFile {
        val messages = mutableListOf<ProtoMessage>()
        val enums = mutableListOf<ProtoEnum>()

        // Separate enums and messages (enums must be defined before messages that use them)
        val enumTypes = types.filter { (_, typeUdm) ->
            val kind = (typeUdm.properties["%kind"] as? UDM.Scalar)?.value as? String
            kind == "enumeration"
        }
        val messageTypes = types.filter { (_, typeUdm) ->
            val kind = (typeUdm.properties["%kind"] as? UDM.Scalar)?.value as? String
            kind == "structure"
        }

        // Convert enums
        enumTypes.forEach { (name, typeUdm) ->
            enums.add(buildEnum(name, typeUdm))
        }

        // Convert messages
        messageTypes.forEach { (name, typeUdm) ->
            messages.add(buildMessage(name, typeUdm))
        }

        return ProtoFile(
            syntax = "proto3",
            packageName = namespace,
            messages = messages,
            enums = enums
        )
    }

    /**
     * Build ProtoMessage from USDL structure type
     */
    private fun buildMessage(name: String, typeUdm: UDM.Object): ProtoMessage {
        val documentation = extractDocumentation(typeUdm)
        val fields = extractFields(typeUdm)
        val reserved = extractReserved(typeUdm)
        val oneofs = extractOneofs(fields)

        // Filter out oneof fields from regular fields (they'll be in oneof groups)
        val regularFields = fields.filter { it.oneofGroup == null }

        return ProtoMessage(
            name = name,
            fields = regularFields,
            oneofs = oneofs,
            reserved = reserved,
            documentation = documentation
        )
    }

    /**
     * Build ProtoEnum from USDL enumeration type
     */
    private fun buildEnum(name: String, typeUdm: UDM.Object): ProtoEnum {
        val documentation = extractDocumentation(typeUdm)
        val valuesArray = typeUdm.properties["%values"] as? UDM.Array
            ?: throw IllegalArgumentException("Enum '$name' must have %values directive")

        val values = valuesArray.elements.mapIndexed { index, valueUdm ->
            if (valueUdm !is UDM.Object) {
                throw IllegalArgumentException("Enum value must be an object")
            }

            val valueName = (valueUdm.properties["%value"] as? UDM.Scalar)?.value as? String
                ?: throw IllegalArgumentException("Enum value must have %value directive")

            val ordinal = when (val ordinalValue = valueUdm.properties["%ordinal"]) {
                is UDM.Scalar -> when (val num = ordinalValue.value) {
                    is Int -> num
                    is Long -> num.toInt()
                    is String -> num.toIntOrNull() ?: index
                    else -> index
                }
                else -> index
            }

            ProtoEnumValue(
                name = valueName,
                number = ordinal,
                documentation = extractDocumentation(valueUdm)
            )
        }

        val protoEnum = ProtoEnum(
            name = name,
            values = values,
            documentation = documentation
        )

        // Validate proto3 requirement: first value must be 0
        protoEnum.validate()

        return protoEnum
    }

    /**
     * Extract fields from USDL %fields directive
     */
    private fun extractFields(typeUdm: UDM.Object): List<ProtoField> {
        val fieldsArray = typeUdm.properties["%fields"] as? UDM.Array
            ?: return emptyList()

        return fieldsArray.elements.mapIndexed { index, fieldUdm ->
            if (fieldUdm !is UDM.Object) {
                throw IllegalArgumentException("Field must be an object")
            }

            buildField(fieldUdm, index + 1)
        }
    }

    /**
     * Build ProtoField from USDL field definition
     */
    private fun buildField(fieldUdm: UDM.Object, defaultFieldNumber: Int): ProtoField {
        val name = (fieldUdm.properties["%name"] as? UDM.Scalar)?.value as? String
            ?: throw IllegalArgumentException("Field must have %name directive")

        val type = (fieldUdm.properties["%type"] as? UDM.Scalar)?.value as? String
            ?: throw IllegalArgumentException("Field '$name' must have %type directive")

        val fieldNumber = when (val numValue = fieldUdm.properties["%fieldNumber"]) {
            is UDM.Scalar -> when (val num = numValue.value) {
                is Int -> num
                is Long -> num.toInt()
                is String -> num.toIntOrNull() ?: defaultFieldNumber
                else -> defaultFieldNumber
            }
            else -> defaultFieldNumber
        }

        validateFieldNumber(fieldNumber, name)

        val isArray = (fieldUdm.properties["%array"] as? UDM.Scalar)?.value == true
        val isMap = (fieldUdm.properties["%map"] as? UDM.Scalar)?.value == true
        val oneofGroup = (fieldUdm.properties["%oneof"] as? UDM.Scalar)?.value as? String

        val documentation = extractDocumentation(fieldUdm)

        return when {
            isMap -> {
                val keyType = (fieldUdm.properties["%keyType"] as? UDM.Scalar)?.value as? String
                    ?: throw IllegalArgumentException("Map field '$name' must have %keyType")
                val valueType = (fieldUdm.properties["%itemType"] as? UDM.Scalar)?.value as? String
                    ?: throw IllegalArgumentException("Map field '$name' must have %itemType")

                ProtoField(
                    name = name,
                    number = fieldNumber,
                    type = "map",
                    label = ProtoField.FieldLabel.MAP,
                    mapKeyType = mapUSDLTypeToProto(keyType),
                    mapValueType = mapUSDLTypeToProto(valueType),
                    documentation = documentation,
                    oneofGroup = oneofGroup
                )
            }
            isArray -> {
                ProtoField(
                    name = name,
                    number = fieldNumber,
                    type = mapUSDLTypeToProto(type, fieldUdm),
                    label = ProtoField.FieldLabel.REPEATED,
                    documentation = documentation,
                    oneofGroup = oneofGroup
                )
            }
            else -> {
                ProtoField(
                    name = name,
                    number = fieldNumber,
                    type = mapUSDLTypeToProto(type, fieldUdm),
                    label = ProtoField.FieldLabel.SINGULAR,
                    documentation = documentation,
                    oneofGroup = oneofGroup
                )
            }
        }
    }

    /**
     * Map USDL type to Proto3 type
     */
    private fun mapUSDLTypeToProto(usdlType: String, fieldUdm: UDM.Object? = null): String {
        return when (usdlType) {
            "string" -> "string"
            "boolean" -> "bool"
            "bytes" -> "bytes"
            "integer" -> {
                val size = (fieldUdm?.properties?.get("%size") as? UDM.Scalar)?.value
                when (size) {
                    32, "32" -> "int32"
                    64, "64" -> "int64"
                    else -> "int32" // Default to int32
                }
            }
            "number" -> "double"
            "float" -> "float"
            else -> usdlType // Assume it's a message or enum name
        }
    }

    /**
     * Extract oneof groups from fields
     */
    private fun extractOneofs(fields: List<ProtoField>): List<ProtoOneof> {
        return fields
            .filter { it.oneofGroup != null }
            .groupBy { it.oneofGroup!! }
            .map { (groupName, groupFields) ->
                ProtoOneof(
                    name = groupName,
                    fields = groupFields
                )
            }
    }

    /**
     * Extract reserved fields from USDL
     */
    private fun extractReserved(typeUdm: UDM.Object): ProtoReserved? {
        val reservedNumbers = mutableListOf<Int>()
        val reservedRanges = mutableListOf<IntRange>()
        val reservedNames = mutableListOf<String>()

        // Extract reserved numbers/ranges
        when (val reserved = typeUdm.properties["%reserved"]) {
            is UDM.Array -> {
                reserved.elements.forEach { elem ->
                    when (elem) {
                        is UDM.Scalar -> {
                            when (val value = elem.value) {
                                is Int -> reservedNumbers.add(value)
                                is Long -> reservedNumbers.add(value.toInt())
                            }
                        }
                        is UDM.Object -> {
                            val from = (elem.properties["from"] as? UDM.Scalar)?.value as? Int
                            val to = (elem.properties["to"] as? UDM.Scalar)?.value as? Int
                            if (from != null && to != null) {
                                reservedRanges.add(from..to)
                            }
                        }
                        else -> {}
                    }
                }
            }
            else -> {}
        }

        // Extract reserved names
        when (val reservedNamesValue = typeUdm.properties["%reservedNames"]) {
            is UDM.Array -> {
                reservedNames.addAll(
                    reservedNamesValue.elements.mapNotNull { (it as? UDM.Scalar)?.value as? String }
                )
            }
            else -> {}
        }

        return if (reservedNumbers.isEmpty() && reservedRanges.isEmpty() && reservedNames.isEmpty()) {
            null
        } else {
            ProtoReserved(
                numbers = reservedNumbers,
                ranges = reservedRanges,
                names = reservedNames
            )
        }
    }

    /**
     * Extract documentation from USDL %documentation directive
     */
    private fun extractDocumentation(udm: UDM.Object): String? {
        return (udm.properties["%documentation"] as? UDM.Scalar)?.value as? String
    }

    /**
     * Validate field number is within valid range
     */
    private fun validateFieldNumber(number: Int, fieldName: String) {
        if (number < 1 || number > 536_870_911) {
            throw IllegalArgumentException(
                "Field '$fieldName' has invalid field number: $number. " +
                "Field numbers must be between 1 and 536,870,911."
            )
        }
        if (number in 19000..19999) {
            throw IllegalArgumentException(
                "Field '$fieldName' uses reserved field number: $number. " +
                "Field numbers 19000-19999 are reserved for Protocol Buffers implementation."
            )
        }
    }

    /**
     * Render ProtoFile to .proto text format
     */
    private fun renderProtoFile(protoFile: ProtoFile): String {
        val builder = StringBuilder()

        // Syntax declaration
        builder.appendLine("syntax = \"${protoFile.syntax}\";")
        builder.appendLine()

        // Package declaration
        if (protoFile.packageName != null) {
            builder.appendLine("package ${protoFile.packageName};")
            builder.appendLine()
        }

        // File-level documentation
        if (protoFile.documentation != null) {
            builder.appendLine("// ${protoFile.documentation}")
            builder.appendLine()
        }

        // Enums (must come before messages that reference them)
        protoFile.enums.forEach { enum ->
            renderEnum(enum, builder, indent = 0)
            builder.appendLine()
        }

        // Messages
        protoFile.messages.forEach { message ->
            renderMessage(message, builder, indent = 0)
            builder.appendLine()
        }

        return builder.toString().trimEnd()
    }

    /**
     * Render ProtoMessage to .proto text
     */
    private fun renderMessage(message: ProtoMessage, builder: StringBuilder, indent: Int) {
        val indentStr = "  ".repeat(indent)

        // Documentation
        if (message.documentation != null) {
            builder.appendLine("$indentStr// ${message.documentation}")
        }

        builder.appendLine("${indentStr}message ${message.name} {")

        // Reserved declarations
        if (message.reserved != null) {
            renderReserved(message.reserved, builder, indent + 1)
        }

        // Oneof groups
        message.oneofs.forEach { oneof ->
            renderOneof(oneof, builder, indent + 1)
        }

        // Regular fields
        message.fields.forEach { field ->
            renderField(field, builder, indent + 1)
        }

        // Nested enums
        message.nestedEnums.forEach { nestedEnum ->
            renderEnum(nestedEnum, builder, indent + 1)
        }

        // Nested messages
        message.nestedMessages.forEach { nestedMessage ->
            renderMessage(nestedMessage, builder, indent + 1)
        }

        builder.appendLine("$indentStr}")
    }

    /**
     * Render ProtoEnum to .proto text
     */
    private fun renderEnum(enum: ProtoEnum, builder: StringBuilder, indent: Int) {
        val indentStr = "  ".repeat(indent)

        // Documentation
        if (enum.documentation != null) {
            builder.appendLine("$indentStr// ${enum.documentation}")
        }

        builder.appendLine("${indentStr}enum ${enum.name} {")

        // Enum values
        enum.values.forEach { value ->
            val valueIndent = "  ".repeat(indent + 1)
            if (value.documentation != null) {
                builder.appendLine("$valueIndent// ${value.documentation}")
            }
            builder.appendLine("$valueIndent${value.name} = ${value.number};")
        }

        builder.appendLine("$indentStr}")
    }

    /**
     * Render ProtoField to .proto text
     */
    private fun renderField(field: ProtoField, builder: StringBuilder, indent: Int) {
        val indentStr = "  ".repeat(indent)

        // Documentation
        if (field.documentation != null) {
            builder.appendLine("$indentStr// ${field.documentation}")
        }

        val fieldDecl = when (field.label) {
            ProtoField.FieldLabel.REPEATED -> "repeated ${field.type} ${field.name} = ${field.number};"
            ProtoField.FieldLabel.MAP -> "map<${field.mapKeyType}, ${field.mapValueType}> ${field.name} = ${field.number};"
            ProtoField.FieldLabel.SINGULAR -> "${field.type} ${field.name} = ${field.number};"
        }

        builder.appendLine("$indentStr$fieldDecl")
    }

    /**
     * Render ProtoOneof to .proto text
     */
    private fun renderOneof(oneof: ProtoOneof, builder: StringBuilder, indent: Int) {
        val indentStr = "  ".repeat(indent)

        // Documentation
        if (oneof.documentation != null) {
            builder.appendLine("$indentStr// ${oneof.documentation}")
        }

        builder.appendLine("${indentStr}oneof ${oneof.name} {")

        // Oneof fields
        oneof.fields.forEach { field ->
            val fieldIndent = "  ".repeat(indent + 1)
            if (field.documentation != null) {
                builder.appendLine("$fieldIndent// ${field.documentation}")
            }
            builder.appendLine("$fieldIndent${field.type} ${field.name} = ${field.number};")
        }

        builder.appendLine("$indentStr}")
    }

    /**
     * Render ProtoReserved to .proto text
     */
    private fun renderReserved(reserved: ProtoReserved, builder: StringBuilder, indent: Int) {
        val indentStr = "  ".repeat(indent)

        // Reserved numbers and ranges
        if (reserved.numbers.isNotEmpty() || reserved.ranges.isNotEmpty()) {
            val parts = mutableListOf<String>()
            parts.addAll(reserved.numbers.map { it.toString() })
            parts.addAll(reserved.ranges.map { "${it.first} to ${it.last}" })
            builder.appendLine("${indentStr}reserved ${parts.joinToString(", ")};")
        }

        // Reserved names
        if (reserved.names.isNotEmpty()) {
            val names = reserved.names.joinToString(", ") { "\"$it\"" }
            builder.appendLine("${indentStr}reserved $names;")
        }
    }
}
