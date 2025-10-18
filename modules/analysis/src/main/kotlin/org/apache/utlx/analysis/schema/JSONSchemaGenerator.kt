// modules/analysis/src/main/kotlin/org/apache/utlx/analysis/schema/JSONSchemaGenerator.kt
package org.apache.utlx.analysis.schema

import kotlinx.serialization.json.*
import org.apache.utlx.analysis.types.*

/**
 * Generator for JSON Schema (Draft 7) documents
 * 
 * Converts internal TypeDefinition representation to JSON Schema format
 * for documentation and validation purposes.
 */
class JSONSchemaGenerator : OutputSchemaGenerator {
    
    private val json = Json { 
        prettyPrint = true
        ignoreUnknownKeys = true
    }
    
    /**
     * Generate JSON Schema string from TypeDefinition
     */
    override fun generate(type: TypeDefinition, options: GeneratorOptions): String {
        val schemaObject = buildJsonObject {
            put("\$schema", "https://json-schema.org/draft/2020-12/schema")
            
            if (options.includeComments) {
                put("description", "Generated schema from UTL-X transformation")
            }
            
            // Add the type definition
            addTypeDefinition(this, type, options)
        }
        
        return if (options.pretty) {
            json.encodeToString(JsonObject.serializer(), schemaObject)
        } else {
            Json { prettyPrint = false }.encodeToString(JsonObject.serializer(), schemaObject)
        }
    }
    
    /**
     * Add type definition to JSON object builder
     */
    private fun addTypeDefinition(
        builder: JsonObjectBuilder, 
        type: TypeDefinition, 
        options: GeneratorOptions
    ) {
        when (type) {
            is TypeDefinition.Scalar -> addScalarType(builder, type, options)
            is TypeDefinition.Array -> addArrayType(builder, type, options)
            is TypeDefinition.Object -> addObjectType(builder, type, options)
            is TypeDefinition.Union -> addUnionType(builder, type, options)
            is TypeDefinition.Any -> {
                // Any type - don't add type constraint
                if (options.includeComments) {
                    builder.put("description", "Any type (inferred from transformation)")
                }
            }
        }
    }
    
    private fun addScalarType(
        builder: JsonObjectBuilder, 
        scalar: TypeDefinition.Scalar, 
        options: GeneratorOptions
    ) {
        builder.put("type", scalar.kind.toJsonType())
        
        // Add format for special types
        when (scalar.kind) {
            ScalarKind.DATE -> builder.put("format", "date")
            ScalarKind.DATETIME -> builder.put("format", "date-time")
            else -> {}
        }
        
        // Add constraints
        scalar.constraints.forEach { constraint ->
            when (constraint.kind) {
                ConstraintKind.MIN_LENGTH -> builder.put("minLength", (constraint.value as Int))
                ConstraintKind.MAX_LENGTH -> builder.put("maxLength", (constraint.value as Int))
                ConstraintKind.PATTERN -> builder.put("pattern", (constraint.value as String))
                ConstraintKind.MINIMUM -> builder.put("minimum", (constraint.value as Double))
                ConstraintKind.MAXIMUM -> builder.put("maximum", (constraint.value as Double))
                ConstraintKind.ENUM -> {
                    val enumValues = constraint.value as List<*>
                    builder.put("enum", JsonArray(enumValues.map { JsonPrimitive(it.toString()) }))
                }
            }
        }
    }
    
    private fun addArrayType(
        builder: JsonObjectBuilder, 
        array: TypeDefinition.Array, 
        options: GeneratorOptions
    ) {
        builder.put("type", "array")
        
        // Add items schema
        builder.put("items", buildJsonObject {
            addTypeDefinition(this, array.elementType, options)
        })
        
        // Add array constraints
        array.minItems?.let { builder.put("minItems", it) }
        array.maxItems?.let { builder.put("maxItems", it) }
    }
    
    private fun addObjectType(
        builder: JsonObjectBuilder, 
        obj: TypeDefinition.Object, 
        options: GeneratorOptions
    ) {
        builder.put("type", "object")
        
        // Add properties
        if (obj.properties.isNotEmpty()) {
            builder.put("properties", buildJsonObject {
                obj.properties.forEach { (propName, propType) ->
                    put(propName, buildJsonObject {
                        if (propType.nullable) {
                            // Make the type nullable by creating a union with null
                            put("oneOf", buildJsonArray {
                                add(buildJsonObject {
                                    addTypeDefinition(this, propType.type, options)
                                })
                                add(buildJsonObject { put("type", "null") })
                            })
                        } else {
                            addTypeDefinition(this, propType.type, options)
                        }
                        
                        propType.description?.let { desc ->
                            if (options.includeComments) {
                                put("description", desc)
                            }
                        }
                        
                        propType.defaultValue?.let { default ->
                            put("default", JsonPrimitive(default.toString()))
                        }
                    })
                }
            })
        }
        
        // Add required fields
        if (obj.required.isNotEmpty()) {
            builder.put("required", JsonArray(obj.required.map { JsonPrimitive(it) }))
        }
        
        // Add additionalProperties
        builder.put("additionalProperties", obj.additionalProperties)
    }
    
    private fun addUnionType(
        builder: JsonObjectBuilder, 
        union: TypeDefinition.Union, 
        options: GeneratorOptions
    ) {
        builder.put("oneOf", buildJsonArray {
            union.types.forEach { type ->
                add(buildJsonObject {
                    addTypeDefinition(this, type, options)
                })
            }
        })
    }
    
    /**
     * Convert ScalarKind to JSON Schema type string
     */
    private fun ScalarKind.toJsonType(): String = when (this) {
        ScalarKind.STRING -> "string"
        ScalarKind.INTEGER -> "integer"
        ScalarKind.NUMBER -> "number"
        ScalarKind.BOOLEAN -> "boolean"
        ScalarKind.NULL -> "null"
        ScalarKind.DATE, ScalarKind.DATETIME -> "string"
    }
}