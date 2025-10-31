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
            is TypeDefinition.Unknown -> {
                // Unknown type - don't add type constraint
                if (options.includeComments) {
                    builder.put("description", "Unknown type (not yet determined)")
                }
            }
            is TypeDefinition.Never -> {
                // Never type - represents an impossible type
                if (options.includeComments) {
                    builder.put("description", "Never type (impossible/error)")
                }
                // Use JSON Schema "not" with empty object to represent Never
                builder.put("not", buildJsonObject {})
            }
        }
    }
    
    private fun addScalarType(
        builder: JsonObjectBuilder,
        scalar: TypeDefinition.Scalar,
        @Suppress("UNUSED_PARAMETER") options: GeneratorOptions
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
            when (constraint) {
                is Constraint.MinLength -> builder.put("minLength", constraint.value)
                is Constraint.MaxLength -> builder.put("maxLength", constraint.value)
                is Constraint.Pattern -> builder.put("pattern", constraint.regex)
                is Constraint.Minimum -> builder.put("minimum", constraint.value)
                is Constraint.Maximum -> builder.put("maximum", constraint.value)
                is Constraint.Enum -> {
                    builder.put("enum", JsonArray(constraint.values.map { JsonPrimitive(it.toString()) }))
                }
                is Constraint.Custom -> {
                    // Custom constraints can be added as extensions
                    builder.put(constraint.name, JsonPrimitive(constraint.params.toString()))
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

    // Note: ScalarKind.toJsonType() is already defined as a member function in TypeDefinition.kt
}