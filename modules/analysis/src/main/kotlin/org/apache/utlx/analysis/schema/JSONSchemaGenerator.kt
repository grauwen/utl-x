// modules/analysis/src/main/kotlin/org/apache/utlx/analysis/schema/JSONSchemaGenerator.kt
package org.apache.utlx.analysis.schema

import org.apache.utlx.analysis.types.*
import kotlinx.serialization.json.*

/**
 * Generator for JSON Schema (Draft 07) from UTL-X type definitions
 * 
 * Converts internal type representations to JSON Schema format for:
 * - API documentation
 * - Validation
 * - IDE support
 * - Contract testing
 */
class JSONSchemaGenerator : SchemaGenerator {
    
    override fun generate(
        type: TypeDefinition,
        format: SchemaFormat,
        options: GeneratorOptions
    ): String {
        if (format != SchemaFormat.JSON_SCHEMA) {
            throw IllegalArgumentException("JSONSchemaGenerator only handles JSON_SCHEMA format")
        }
        
        val schema = buildJsonObject {
            put("\$schema", "http://json-schema.org/draft-07/schema#")
            
            if (options.includeComments) {
                put("title", "Generated Schema")
                put("description", "Auto-generated from UTL-X transformation")
            }
            
            putAll(typeToJsonSchema(type, options))
        }
        
        return if (options.pretty) {
            Json { prettyPrint = true }.encodeToString(JsonObject.serializer(), schema)
        } else {
            Json.encodeToString(JsonObject.serializer(), schema)
        }
    }
    
    /**
     * Convert type definition to JSON Schema object
     */
    private fun typeToJsonSchema(
        type: TypeDefinition,
        options: GeneratorOptions,
        depth: Int = 0
    ): Map<String, JsonElement> {
        return when (type) {
            is TypeDefinition.Scalar -> scalarToJsonSchema(type, options)
            is TypeDefinition.Array -> arrayToJsonSchema(type, options, depth)
            is TypeDefinition.Object -> objectToJsonSchema(type, options, depth)
            is TypeDefinition.Union -> unionToJsonSchema(type, options, depth)
            is TypeDefinition.Any -> mapOf("type" to JsonPrimitive("object"))
        }
    }
    
    /**
     * Convert scalar type to JSON Schema
     */
    private fun scalarToJsonSchema(
        scalar: TypeDefinition.Scalar,
        options: GeneratorOptions
    ): Map<String, JsonElement> {
        val schema = mutableMapOf<String, JsonElement>()
        
        // Map scalar kind to JSON Schema type
        schema["type"] = JsonPrimitive(when (scalar.kind) {
            ScalarKind.STRING -> "string"
            ScalarKind.INTEGER -> "integer"
            ScalarKind.NUMBER -> "number"
            ScalarKind.BOOLEAN -> "boolean"
            ScalarKind.NULL -> "null"
            ScalarKind.DATE, ScalarKind.DATETIME -> "string"
        })
        
        // Add format for date/datetime
        when (scalar.kind) {
            ScalarKind.DATE -> schema["format"] = JsonPrimitive("date")
            ScalarKind.DATETIME -> schema["format"] = JsonPrimitive("date-time")
            else -> {}
        }
        
        // Add constraints
        scalar.constraints.forEach { constraint ->
            when (constraint.kind) {
                ConstraintKind.MIN_LENGTH -> 
                    schema["minLength"] = JsonPrimitive(constraint.value as Int)
                ConstraintKind.MAX_LENGTH -> 
                    schema["maxLength"] = JsonPrimitive(constraint.value as Int)
                ConstraintKind.PATTERN -> 
                    schema["pattern"] = JsonPrimitive(constraint.value as String)
                ConstraintKind.MINIMUM -> 
                    schema["minimum"] = JsonPrimitive(constraint.value as Double)
                ConstraintKind.MAXIMUM -> 
                    schema["maximum"] = JsonPrimitive(constraint.value as Double)
                ConstraintKind.ENUM -> {
                    val enumValues = (constraint.value as List<*>).map { JsonPrimitive(it.toString()) }
                    schema["enum"] = JsonArray(enumValues)
                }
            }
        }
        
        return schema
    }
    
    /**
     * Convert array type to JSON Schema
     */
    private fun arrayToJsonSchema(
        array: TypeDefinition.Array,
        options: GeneratorOptions,
        depth: Int
    ): Map<String, JsonElement> {
        val schema = mutableMapOf<String, JsonElement>()
        
        schema["type"] = JsonPrimitive("array")
        
        // Add items schema
        val itemsSchema = typeToJsonSchema(array.elementType, options, depth + 1)
        schema["items"] = buildJsonObject {
            itemsSchema.forEach { (key, value) -> put(key, value) }
        }
        
        // Add min/max items
        array.minItems?.let { schema["minItems"] = JsonPrimitive(it) }
        array.maxItems?.let { schema["maxItems"] = JsonPrimitive(it) }
        
        return schema
    }
    
    /**
     * Convert object type to JSON Schema
     */
    private fun objectToJsonSchema(
        obj: TypeDefinition.Object,
        options: GeneratorOptions,
        depth: Int
    ): Map<String, JsonElement> {
        val schema = mutableMapOf<String, JsonElement>()
        
        schema["type"] = JsonPrimitive("object")
        
        // Add properties
        if (obj.properties.isNotEmpty()) {
            val properties = buildJsonObject {
                obj.properties.forEach { (name, property) ->
                    val propertySchema = buildJsonObject {
                        typeToJsonSchema(property.type, options, depth + 1).forEach { (key, value) ->
                            put(key, value)
                        }
                        
                        // Add description if available
                        property.description?.let { 
                            if (options.includeComments) {
                                put("description", it)
                            }
                        }
                        
                        // Add nullable if needed
                        if (property.nullable && options.strictMode) {
                            // In strict mode, use oneOf with null
                            val existingType = get("type")
                            remove("type")
                            put("oneOf", buildJsonArray {
                                add(buildJsonObject { put("type", existingType!!) })
                                add(buildJsonObject { put("type", "null") })
                            })
                        }
                    }
                    
                    put(name, propertySchema)
                }
            }
            schema["properties"] = properties
        }
        
        // Add required fields
        if (obj.required.isNotEmpty()) {
            schema["required"] = JsonArray(obj.required.map { JsonPrimitive(it) })
        }
        
        // Add additionalProperties
        schema["additionalProperties"] = JsonPrimitive(obj.additionalProperties)
        
        return schema
    }
    
    /**
     * Convert union type to JSON Schema
     */
    private fun unionToJsonSchema(
        union: TypeDefinition.Union,
        options: GeneratorOptions,
        depth: Int
    ): Map<String, JsonElement> {
        val schema = mutableMapOf<String, JsonElement>()
        
        // Use anyOf for union types
        val anyOf = buildJsonArray {
            union.types.forEach { type ->
                add(buildJsonObject {
                    typeToJsonSchema(type, options, depth + 1).forEach { (key, value) ->
                        put(key, value)
                    }
                })
            }
        }
        
        schema["anyOf"] = anyOf
        
        return schema
    }
    
    companion object {
        /**
         * Quick generate method
         */
        fun toJSONSchema(
            type: TypeDefinition,
            pretty: Boolean = true,
            includeComments: Boolean = true
        ): String {
            return JSONSchemaGenerator().generate(
                type,
                SchemaFormat.JSON_SCHEMA,
                GeneratorOptions(
                    pretty = pretty,
                    includeComments = includeComments
                )
            )
        }
    }
}

/**
 * Parser for JSON Schema documents
 * 
 * Converts JSON Schema to internal type definitions
 */
class JSONSchemaParser : InputSchemaParser {
    
    override fun parse(schema: String, format: SchemaFormat): TypeDefinition {
        if (format != SchemaFormat.JSON_SCHEMA) {
            throw IllegalArgumentException("JSONSchemaParser only handles JSON_SCHEMA format")
        }
        
        val json = Json.parseToJsonElement(schema).jsonObject
        return parseJsonSchema(json)
    }
    
    private fun parseJsonSchema(schema: JsonObject): TypeDefinition {
        // Check for oneOf/anyOf/allOf
        when {
            schema.containsKey("anyOf") || schema.containsKey("oneOf") -> {
                val unionKey = if (schema.containsKey("anyOf")) "anyOf" else "oneOf"
                val types = schema[unionKey]!!.jsonArray.map { parseJsonSchema(it.jsonObject) }
                return TypeDefinition.Union(types)
            }
            schema.containsKey("allOf") -> {
                // Merge all schemas (simplified - real implementation would be more complex)
                val types = schema["allOf"]!!.jsonArray.map { parseJsonSchema(it.jsonObject) }
                return types.firstOrNull() ?: TypeDefinition.Any
            }
        }
        
        // Get type
        val type = schema["type"]?.jsonPrimitive?.content
        
        return when (type) {
            "object" -> parseObjectSchema(schema)
            "array" -> parseArraySchema(schema)
            "string" -> parseStringSchema(schema)
            "integer" -> parseIntegerSchema(schema)
            "number" -> parseNumberSchema(schema)
            "boolean" -> TypeDefinition.Scalar(ScalarKind.BOOLEAN)
            "null" -> TypeDefinition.Scalar(ScalarKind.NULL)
            else -> TypeDefinition.Any
        }
    }
    
    private fun parseObjectSchema(schema: JsonObject): TypeDefinition {
        val properties = mutableMapOf<String, PropertyType>()
        val required = schema["required"]?.jsonArray?.map { it.jsonPrimitive.content }?.toSet() ?: emptySet()
        
        schema["properties"]?.jsonObject?.forEach { (name, propSchema) ->
            properties[name] = PropertyType(
                type = parseJsonSchema(propSchema.jsonObject),
                nullable = !required.contains(name),
                description = propSchema.jsonObject["description"]?.jsonPrimitive?.content
            )
        }
        
        val additionalProperties = schema["additionalProperties"]?.jsonPrimitive?.boolean ?: false
        
        return TypeDefinition.Object(
            properties = properties,
            required = required,
            additionalProperties = additionalProperties
        )
    }
    
    private fun parseArraySchema(schema: JsonObject): TypeDefinition {
        val items = schema["items"]?.jsonObject
        val elementType = if (items != null) {
            parseJsonSchema(items)
        } else {
            TypeDefinition.Any
        }
        
        val minItems = schema["minItems"]?.jsonPrimitive?.int
        val maxItems = schema["maxItems"]?.jsonPrimitive?.int
        
        return TypeDefinition.Array(
            elementType = elementType,
            minItems = minItems,
            maxItems = maxItems
        )
    }
    
    private fun parseStringSchema(schema: JsonObject): TypeDefinition {
        val constraints = mutableListOf<Constraint>()
        
        val format = schema["format"]?.jsonPrimitive?.content
        val kind = when (format) {
            "date" -> ScalarKind.DATE
            "date-time" -> ScalarKind.DATETIME
            else -> ScalarKind.STRING
        }
        
        schema["minLength"]?.jsonPrimitive?.int?.let {
            constraints.add(Constraint(ConstraintKind.MIN_LENGTH, it))
        }
        
        schema["maxLength"]?.jsonPrimitive?.int?.let {
            constraints.add(Constraint(ConstraintKind.MAX_LENGTH, it))
        }
        
        schema["pattern"]?.jsonPrimitive?.content?.let {
            constraints.add(Constraint(ConstraintKind.PATTERN, it))
        }
        
        schema["enum"]?.jsonArray?.let { enumArray ->
            val enumValues = enumArray.map { it.jsonPrimitive.content }
            constraints.add(Constraint(ConstraintKind.ENUM, enumValues))
        }
        
        return TypeDefinition.Scalar(kind, constraints)
    }
    
    private fun parseIntegerSchema(schema: JsonObject): TypeDefinition {
        val constraints = mutableListOf<Constraint>()
        
        schema["minimum"]?.jsonPrimitive?.double?.let {
            constraints.add(Constraint(ConstraintKind.MINIMUM, it))
        }
        
        schema["maximum"]?.jsonPrimitive?.double?.let {
            constraints.add(Constraint(ConstraintKind.MAXIMUM, it))
        }
        
        return TypeDefinition.Scalar(ScalarKind.INTEGER, constraints)
    }
    
    private fun parseNumberSchema(schema: JsonObject): TypeDefinition {
        val constraints = mutableListOf<Constraint>()
        
        schema["minimum"]?.jsonPrimitive?.double?.let {
            constraints.add(Constraint(ConstraintKind.MINIMUM, it))
        }
        
        schema["maximum"]?.jsonPrimitive?.double?.let {
            constraints.add(Constraint(ConstraintKind.MAXIMUM, it))
        }
        
        return TypeDefinition.Scalar(ScalarKind.NUMBER, constraints)
    }
}
