// modules/analysis/src/main/kotlin/org/apache/utlx/analysis/schema/JSONSchemaParser.kt
package org.apache.utlx.analysis.schema

import kotlinx.serialization.json.*
import org.apache.utlx.analysis.types.*

/**
 * Parser for JSON Schema (Draft 7) documents
 * 
 * Converts JSON Schema specifications to internal TypeDefinition representation
 * for use in type inference and validation.
 * 
 * Supports:
 * - Basic types (string, number, integer, boolean, null, object, array)
 * - Constraints (minLength, maxLength, pattern, minimum, maximum, etc.)
 * - Required properties
 * - oneOf/anyOf/allOf combinators
 * - $ref references (simplified)
 * - format specifiers (date-time, email, uri, etc.)
 */
class JSONSchemaParser : InputSchemaParser {
    
    private val json = Json { ignoreUnknownKeys = true }
    
    /**
     * Parse JSON Schema string into TypeDefinition
     */
    override fun parse(schema: String, format: SchemaFormat): TypeDefinition {
        if (format != SchemaFormat.JSON_SCHEMA) {
            throw IllegalArgumentException("JSONSchemaParser only handles JSON_SCHEMA format")
        }
        
        return try {
            val jsonElement = json.parseToJsonElement(schema)
            parseJsonSchema(jsonElement.jsonObject)
        } catch (e: Exception) {
            throw SchemaParseException("Failed to parse JSON Schema: ${e.message}", e)
        }
    }
    
    /**
     * Parse a JSON Schema object
     */
    private fun parseJsonSchema(schema: JsonObject): TypeDefinition {
        // Handle $ref (simplified - doesn't resolve external refs)
        if (schema.containsKey("\$ref")) {
            val ref = schema["\$ref"]!!.jsonPrimitive.content
            // For now, treat refs as Any type (proper resolution would need context)
            return TypeDefinition.Any
        }
        
        // Handle combinators (oneOf, anyOf, allOf)
        when {
            schema.containsKey("anyOf") || schema.containsKey("oneOf") -> {
                val key = if (schema.containsKey("anyOf")) "anyOf" else "oneOf"
                val types = schema[key]!!.jsonArray.map { parseJsonSchema(it.jsonObject) }
                return TypeDefinition.Union(types)
            }
            schema.containsKey("allOf") -> {
                // Merge all schemas (simplified intersection)
                val types = schema["allOf"]!!.jsonArray.map { parseJsonSchema(it.jsonObject) }
                return mergeAllOf(types)
            }
        }
        
        // Get type (can be string or array)
        val type = when {
            schema.containsKey("type") -> {
                val typeElement = schema["type"]!!
                when {
                    typeElement is JsonPrimitive -> typeElement.content
                    typeElement is JsonArray -> typeElement.jsonArray.first().jsonPrimitive.content
                    else -> null
                }
            }
            else -> null
        }
        
        // Get description
        val description = schema["description"]?.jsonPrimitive?.content
        
        return when (type) {
            "object" -> parseObjectSchema(schema, description)
            "array" -> parseArraySchema(schema, description)
            "string" -> parseStringSchema(schema, description)
            "integer" -> parseIntegerSchema(schema, description)
            "number" -> parseNumberSchema(schema, description)
            "boolean" -> TypeDefinition.Scalar(ScalarKind.BOOLEAN)
            "null" -> TypeDefinition.Scalar(ScalarKind.NULL)
            else -> {
                // No type specified - could be any type
                if (schema.containsKey("properties")) {
                    parseObjectSchema(schema, description)
                } else {
                    TypeDefinition.Any
                }
            }
        }
    }
    
    /**
     * Parse object schema
     */
    private fun parseObjectSchema(schema: JsonObject, description: String?): TypeDefinition {
        val properties = mutableMapOf<String, PropertyType>()
        val required = schema["required"]?.jsonArray?.map { 
            it.jsonPrimitive.content 
        }?.toSet() ?: emptySet()
        
        // Parse properties
        schema["properties"]?.jsonObject?.forEach { (name, propSchema) ->
            val propType = parseJsonSchema(propSchema.jsonObject)
            val propDesc = propSchema.jsonObject["description"]?.jsonPrimitive?.content
            
            properties[name] = PropertyType(
                type = propType,
                nullable = !required.contains(name),
                description = propDesc
            )
        }
        
        // Get additionalProperties setting
        val additionalProperties = when {
            schema.containsKey("additionalProperties") -> {
                val addProp = schema["additionalProperties"]!!
                when {
                    addProp is JsonPrimitive -> addProp.boolean
                    else -> true // Schema object means additional props with that schema
                }
            }
            else -> false
        }
        
        return TypeDefinition.Object(
            properties = properties,
            required = required,
            additionalProperties = additionalProperties,
            description = description
        )
    }
    
    /**
     * Parse array schema
     */
    private fun parseArraySchema(schema: JsonObject, description: String?): TypeDefinition {
        val items = schema["items"]?.jsonObject
        val elementType = if (items != null) {
            parseJsonSchema(items)
        } else {
            TypeDefinition.Any
        }
        
        val minItems = schema["minItems"]?.jsonPrimitive?.int
        val maxItems = schema["maxItems"]?.jsonPrimitive?.int
        val uniqueItems = schema["uniqueItems"]?.jsonPrimitive?.boolean ?: false
        
        val constraints = mutableListOf<Constraint>()
        if (uniqueItems) {
            constraints.add(Constraint.Custom("uniqueItems", "true"))
        }
        
        return TypeDefinition.Array(
            elementType = elementType,
            minItems = minItems,
            maxItems = maxItems,
            description = description
        )
    }
    
    /**
     * Parse string schema
     */
    private fun parseStringSchema(schema: JsonObject, description: String?): TypeDefinition {
        val constraints = mutableListOf<Constraint>()
        
        // Determine scalar kind based on format
        val format = schema["format"]?.jsonPrimitive?.content
        val kind = when (format) {
            "date" -> ScalarKind.DATE
            "date-time" -> ScalarKind.DATETIME
            "time" -> ScalarKind.TIME
            "email" -> {
                constraints.add(Constraint.Pattern("^[^@]+@[^@]+\\.[^@]+$"))
                ScalarKind.STRING
            }
            "uri", "url" -> {
                constraints.add(Constraint.Custom("format", "uri"))
                ScalarKind.STRING
            }
            "uuid" -> {
                constraints.add(Constraint.Pattern("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$"))
                ScalarKind.STRING
            }
            else -> ScalarKind.STRING
        }
        
        // Length constraints
        schema["minLength"]?.jsonPrimitive?.int?.let {
            constraints.add(Constraint.MinLength(it))
        }
        schema["maxLength"]?.jsonPrimitive?.int?.let {
            constraints.add(Constraint.MaxLength(it))
        }
        
        // Pattern constraint
        schema["pattern"]?.jsonPrimitive?.content?.let {
            constraints.add(Constraint.Pattern(it))
        }
        
        // Enum constraint
        schema["enum"]?.jsonArray?.let { enumArray ->
            val values = enumArray.map { it.jsonPrimitive.content }
            constraints.add(Constraint.Enum(values))
        }
        
        return TypeDefinition.Scalar(
            kind = kind,
            constraints = constraints,
            description = description
        )
    }
    
    /**
     * Parse integer schema
     */
    private fun parseIntegerSchema(schema: JsonObject, description: String?): TypeDefinition {
        val constraints = mutableListOf<Constraint>()
        
        // Numeric constraints
        schema["minimum"]?.jsonPrimitive?.long?.let {
            constraints.add(Constraint.Minimum(it.toDouble()))
        }
        schema["maximum"]?.jsonPrimitive?.long?.let {
            constraints.add(Constraint.Maximum(it.toDouble()))
        }
        schema["exclusiveMinimum"]?.jsonPrimitive?.long?.let {
            constraints.add(Constraint.ExclusiveMinimum(it.toDouble()))
        }
        schema["exclusiveMaximum"]?.jsonPrimitive?.long?.let {
            constraints.add(Constraint.ExclusiveMaximum(it.toDouble()))
        }
        schema["multipleOf"]?.jsonPrimitive?.long?.let {
            constraints.add(Constraint.MultipleOf(it.toDouble()))
        }
        
        // Enum constraint
        schema["enum"]?.jsonArray?.let { enumArray ->
            val values = enumArray.map { it.jsonPrimitive.long.toString() }
            constraints.add(Constraint.Enum(values))
        }
        
        return TypeDefinition.Scalar(
            kind = ScalarKind.INTEGER,
            constraints = constraints,
            description = description
        )
    }
    
    /**
     * Parse number (float/double) schema
     */
    private fun parseNumberSchema(schema: JsonObject, description: String?): TypeDefinition {
        val constraints = mutableListOf<Constraint>()
        
        // Numeric constraints
        schema["minimum"]?.jsonPrimitive?.double?.let {
            constraints.add(Constraint.Minimum(it))
        }
        schema["maximum"]?.jsonPrimitive?.double?.let {
            constraints.add(Constraint.Maximum(it))
        }
        schema["exclusiveMinimum"]?.jsonPrimitive?.double?.let {
            constraints.add(Constraint.ExclusiveMinimum(it))
        }
        schema["exclusiveMaximum"]?.jsonPrimitive?.double?.let {
            constraints.add(Constraint.ExclusiveMaximum(it))
        }
        schema["multipleOf"]?.jsonPrimitive?.double?.let {
            constraints.add(Constraint.MultipleOf(it))
        }
        
        // Enum constraint
        schema["enum"]?.jsonArray?.let { enumArray ->
            val values = enumArray.map { it.jsonPrimitive.double.toString() }
            constraints.add(Constraint.Enum(values))
        }
        
        return TypeDefinition.Scalar(
            kind = ScalarKind.NUMBER,
            constraints = constraints,
            description = description
        )
    }
    
    /**
     * Merge allOf schemas (simplified intersection)
     */
    private fun mergeAllOf(types: List<TypeDefinition>): TypeDefinition {
        // Simplified: just return first object type found, or first type
        return types.firstOrNull { it is TypeDefinition.Object } ?: types.firstOrNull() ?: TypeDefinition.Any
    }
    
    companion object {
        /**
         * Quick parse method
         */
        fun fromJSONSchema(schema: String): TypeDefinition {
            return JSONSchemaParser().parse(schema, SchemaFormat.JSON_SCHEMA)
        }
    }
}

/**
 * Exception thrown when schema parsing fails
 */
class SchemaParseException(message: String, cause: Throwable? = null) : Exception(message, cause)
