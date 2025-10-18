// modules/analysis/src/main/kotlin/org/apache/utlx/analysis/schema/JSONSchemaParser.kt
package org.apache.utlx.analysis.schema

import kotlinx.serialization.json.*
import org.apache.utlx.analysis.types.*

/**
 * Parser for JSON Schema (Draft 7) documents
 * 
 * Converts JSON Schema specifications to internal TypeDefinition representation
 * for use in type inference and validation.
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
            // For now, treat refs as Any type (proper resolution would need context)
            return TypeDefinition.Any
        }
        
        // Get the type field
        val typeField = schema["type"]
        
        return when {
            typeField == null -> {
                // No type specified - check for other indicators
                when {
                    schema.containsKey("properties") -> parseObjectSchema(schema)
                    schema.containsKey("items") -> parseArraySchema(schema)
                    schema.containsKey("enum") -> parseEnumSchema(schema)
                    schema.containsKey("oneOf") || schema.containsKey("anyOf") -> parseUnionSchema(schema)
                    else -> TypeDefinition.Any
                }
            }
            typeField.jsonPrimitive.isString -> {
                when (typeField.jsonPrimitive.content) {
                    "string" -> parseStringSchema(schema)
                    "number" -> parseNumberSchema(schema)
                    "integer" -> parseIntegerSchema(schema)
                    "boolean" -> TypeDefinition.Scalar(ScalarKind.BOOLEAN)
                    "null" -> TypeDefinition.Scalar(ScalarKind.NULL)
                    "object" -> parseObjectSchema(schema)
                    "array" -> parseArraySchema(schema)
                    else -> TypeDefinition.Any
                }
            }
            typeField.jsonArray.isNotEmpty() -> {
                // Multiple types - create union
                val types = typeField.jsonArray.map { element ->
                    val singleTypeSchema = JsonObject(schema.toMutableMap().apply {
                        put("type", element)
                    })
                    parseJsonSchema(singleTypeSchema)
                }
                if (types.size == 1) types.first() else TypeDefinition.Union(types)
            }
            else -> TypeDefinition.Any
        }
    }
    
    private fun parseStringSchema(schema: JsonObject): TypeDefinition.Scalar {
        val constraints = mutableListOf<Constraint>()
        
        schema["minLength"]?.jsonPrimitive?.intOrNull?.let {
            constraints.add(Constraint(ConstraintKind.MIN_LENGTH, it))
        }
        
        schema["maxLength"]?.jsonPrimitive?.intOrNull?.let {
            constraints.add(Constraint(ConstraintKind.MAX_LENGTH, it))
        }
        
        schema["pattern"]?.jsonPrimitive?.contentOrNull?.let {
            constraints.add(Constraint(ConstraintKind.PATTERN, it))
        }
        
        schema["enum"]?.jsonArray?.let { enumArray ->
            val enumValues = enumArray.map { it.jsonPrimitive.content }
            constraints.add(Constraint(ConstraintKind.ENUM, enumValues))
        }
        
        // Check format for special string types
        val format = schema["format"]?.jsonPrimitive?.contentOrNull
        val scalarKind = when (format) {
            "date" -> ScalarKind.DATE
            "date-time" -> ScalarKind.DATETIME
            else -> ScalarKind.STRING
        }
        
        return TypeDefinition.Scalar(scalarKind, constraints)
    }
    
    private fun parseNumberSchema(schema: JsonObject): TypeDefinition.Scalar {
        val constraints = mutableListOf<Constraint>()
        
        schema["minimum"]?.jsonPrimitive?.doubleOrNull?.let {
            constraints.add(Constraint(ConstraintKind.MINIMUM, it))
        }
        
        schema["maximum"]?.jsonPrimitive?.doubleOrNull?.let {
            constraints.add(Constraint(ConstraintKind.MAXIMUM, it))
        }
        
        return TypeDefinition.Scalar(ScalarKind.NUMBER, constraints)
    }
    
    private fun parseIntegerSchema(schema: JsonObject): TypeDefinition.Scalar {
        val constraints = mutableListOf<Constraint>()
        
        schema["minimum"]?.jsonPrimitive?.intOrNull?.let {
            constraints.add(Constraint(ConstraintKind.MINIMUM, it.toDouble()))
        }
        
        schema["maximum"]?.jsonPrimitive?.intOrNull?.let {
            constraints.add(Constraint(ConstraintKind.MAXIMUM, it.toDouble()))
        }
        
        return TypeDefinition.Scalar(ScalarKind.INTEGER, constraints)
    }
    
    private fun parseObjectSchema(schema: JsonObject): TypeDefinition.Object {
        val properties = mutableMapOf<String, PropertyType>()
        val required = mutableSetOf<String>()
        
        // Parse properties
        schema["properties"]?.jsonObject?.let { propsObj ->
            propsObj.forEach { (propName, propSchema) ->
                val propType = parseJsonSchema(propSchema.jsonObject)
                properties[propName] = PropertyType(propType, nullable = false)
            }
        }
        
        // Parse required fields
        schema["required"]?.jsonArray?.let { requiredArray ->
            requiredArray.forEach { element ->
                required.add(element.jsonPrimitive.content)
            }
        }
        
        // Check additionalProperties
        val additionalProperties = schema["additionalProperties"]?.jsonPrimitive?.booleanOrNull ?: false
        
        return TypeDefinition.Object(properties, required, additionalProperties)
    }
    
    private fun parseArraySchema(schema: JsonObject): TypeDefinition.Array {
        val elementType = schema["items"]?.let { items ->
            when (items) {
                is JsonObject -> parseJsonSchema(items)
                is JsonArray -> {
                    // Multiple item schemas - create union
                    val types = items.map { parseJsonSchema(it.jsonObject) }
                    if (types.size == 1) types.first() else TypeDefinition.Union(types)
                }
                else -> TypeDefinition.Any
            }
        } ?: TypeDefinition.Any
        
        val minItems = schema["minItems"]?.jsonPrimitive?.intOrNull
        val maxItems = schema["maxItems"]?.jsonPrimitive?.intOrNull
        
        return TypeDefinition.Array(elementType, minItems, maxItems)
    }
    
    private fun parseEnumSchema(schema: JsonObject): TypeDefinition {
        val enumArray = schema["enum"]?.jsonArray ?: return TypeDefinition.Any
        
        // Determine the type of enum values
        val firstValue = enumArray.firstOrNull()?.jsonPrimitive
        val scalarKind = when {
            firstValue?.isString == true -> ScalarKind.STRING
            firstValue?.booleanOrNull != null -> ScalarKind.BOOLEAN
            firstValue?.intOrNull != null -> ScalarKind.INTEGER
            firstValue?.doubleOrNull != null -> ScalarKind.NUMBER
            else -> ScalarKind.STRING
        }
        
        val enumValues = enumArray.map { 
            when (scalarKind) {
                ScalarKind.STRING -> it.jsonPrimitive.content
                ScalarKind.BOOLEAN -> it.jsonPrimitive.boolean.toString()
                ScalarKind.INTEGER -> it.jsonPrimitive.int.toString()
                ScalarKind.NUMBER -> it.jsonPrimitive.double.toString()
                else -> it.jsonPrimitive.content
            }
        }
        
        return TypeDefinition.Scalar(
            scalarKind, 
            listOf(Constraint(ConstraintKind.ENUM, enumValues))
        )
    }
    
    private fun parseUnionSchema(schema: JsonObject): TypeDefinition {
        val oneOf = schema["oneOf"]?.jsonArray
        val anyOf = schema["anyOf"]?.jsonArray
        val allOf = schema["allOf"]?.jsonArray
        
        val schemas = oneOf ?: anyOf ?: allOf ?: return TypeDefinition.Any
        
        val types = schemas.map { parseJsonSchema(it.jsonObject) }
        
        return if (types.size == 1) types.first() else TypeDefinition.Union(types)
    }
}