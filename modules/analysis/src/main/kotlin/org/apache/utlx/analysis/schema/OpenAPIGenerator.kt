// modules/analysis/src/main/kotlin/org/apache/utlx/analysis/schema/OpenAPIGenerator.kt
package org.apache.utlx.analysis.schema

import org.apache.utlx.analysis.types.*
import org.apache.utlx.core.ast.Program
import kotlinx.serialization.json.*

/**
 * Generator for OpenAPI 3.0 specifications from UTL-X transformations
 * 
 * Creates REST API documentation that includes:
 * - Request/response schemas from transformation input/output
 * - API endpoints with methods and paths
 * - Example requests and responses
 * - Authentication requirements
 * - Error responses
 */
class OpenAPIGenerator {
    
    /**
     * Generate OpenAPI spec for a transformation endpoint
     */
    fun generate(
        transformation: Program,
        inputType: TypeDefinition,
        outputType: TypeDefinition,
        config: OpenAPIConfig
    ): String {
        val spec = buildJsonObject {
            put("openapi", "3.0.0")
            
            // Info section
            put("info", buildJsonObject {
                put("title", config.title)
                put("version", config.version)
                config.description?.let { put("description", it) }
                config.contactEmail?.let {
                    put("contact", buildJsonObject {
                        put("email", it)
                    })
                }
            })
            
            // Servers
            if (config.servers.isNotEmpty()) {
                put("servers", JsonArray(config.servers.map { server ->
                    buildJsonObject {
                        put("url", server.url)
                        server.description?.let { put("description", it) }
                    }
                }))
            }
            
            // Paths
            put("paths", buildJsonObject {
                put(config.apiPath, buildJsonObject {
                    put(config.method.lowercase(), buildJsonObject {
                        put("summary", config.operationSummary ?: "Transform data")
                        put("operationId", config.operationId ?: generateOperationId(config.apiPath))
                        
                        config.tags?.let { tags ->
                            put("tags", JsonArray(tags.map { JsonPrimitive(it) }))
                        }
                        
                        // Request body
                        put("requestBody", buildRequestBody(inputType, config))
                        
                        // Responses
                        put("responses", buildJsonObject {
                            put("200", buildSuccessResponse(outputType, config))
                            put("400", buildErrorResponse("Bad Request", "Invalid input data"))
                            put("500", buildErrorResponse("Internal Server Error", "Transformation failed"))
                        })
                        
                        // Security
                        if (config.requiresAuth) {
                            put("security", JsonArray(listOf(
                                buildJsonObject {
                                    put(config.securityScheme, JsonArray(config.securityScopes.map { JsonPrimitive(it) }))
                                }
                            )))
                        }
                    })
                })
            })
            
            // Components
            put("components", buildJsonObject {
                // Schemas
                put("schemas", buildJsonObject {
                    put("InputSchema", typeToJsonSchema(inputType, config))
                    put("OutputSchema", typeToJsonSchema(outputType, config))
                    put("ErrorResponse", buildErrorSchemaObject())
                })
                
                // Security schemes
                if (config.requiresAuth) {
                    put("securitySchemes", buildJsonObject {
                        put(config.securityScheme, buildSecurityScheme(config))
                    })
                }
            })
        }
        
        return if (config.pretty) {
            Json { prettyPrint = true }.encodeToString(JsonObject.serializer(), spec)
        } else {
            Json.encodeToString(JsonObject.serializer(), spec)
        }
    }
    
    /**
     * Build request body specification
     */
    private fun buildRequestBody(inputType: TypeDefinition, config: OpenAPIConfig): JsonObject {
        return buildJsonObject {
            put("required", true)
            config.requestDescription?.let { put("description", it) }
            
            put("content", buildJsonObject {
                config.inputContentType.forEach { contentType ->
                    put(contentType, buildJsonObject {
                        put("schema", buildJsonObject {
                            put("\$ref", "#/components/schemas/InputSchema")
                        })
                        
                        if (config.includeExamples && config.inputExample != null) {
                            put("example", JsonPrimitive(config.inputExample))
                        }
                    })
                }
            })
        }
    }
    
    /**
     * Build success response specification
     */
    private fun buildSuccessResponse(outputType: TypeDefinition, config: OpenAPIConfig): JsonObject {
        return buildJsonObject {
            put("description", config.successDescription ?: "Transformation successful")
            
            put("content", buildJsonObject {
                config.outputContentType.forEach { contentType ->
                    put(contentType, buildJsonObject {
                        put("schema", buildJsonObject {
                            put("\$ref", "#/components/schemas/OutputSchema")
                        })
                        
                        if (config.includeExamples && config.outputExample != null) {
                            put("example", JsonPrimitive(config.outputExample))
                        }
                    })
                }
            })
        }
    }
    
    /**
     * Build error response specification
     */
    private fun buildErrorResponse(description: String, message: String): JsonObject {
        return buildJsonObject {
            put("description", description)
            put("content", buildJsonObject {
                put("application/json", buildJsonObject {
                    put("schema", buildJsonObject {
                        put("\$ref", "#/components/schemas/ErrorResponse")
                    })
                    put("example", buildJsonObject {
                        put("error", message)
                        put("code", description.replace(" ", "_").uppercase())
                    })
                })
            })
        }
    }
    
    /**
     * Build error schema object
     */
    private fun buildErrorSchemaObject(): JsonObject {
        return buildJsonObject {
            put("type", "object")
            put("properties", buildJsonObject {
                put("error", buildJsonObject {
                    put("type", "string")
                    put("description", "Error message")
                })
                put("code", buildJsonObject {
                    put("type", "string")
                    put("description", "Error code")
                })
                put("details", buildJsonObject {
                    put("type", "object")
                    put("description", "Additional error details")
                })
            })
            put("required", JsonArray(listOf(JsonPrimitive("error"), JsonPrimitive("code"))))
        }
    }
    
    /**
     * Build security scheme
     */
    private fun buildSecurityScheme(config: OpenAPIConfig): JsonObject {
        return buildJsonObject {
            when (config.authType) {
                AuthType.BEARER -> {
                    put("type", "http")
                    put("scheme", "bearer")
                    put("bearerFormat", "JWT")
                }
                AuthType.API_KEY -> {
                    put("type", "apiKey")
                    put("in", "header")
                    put("name", "X-API-Key")
                }
                AuthType.OAUTH2 -> {
                    put("type", "oauth2")
                    put("flows", buildJsonObject {
                        put("authorizationCode", buildJsonObject {
                            put("authorizationUrl", config.oauthAuthUrl ?: "")
                            put("tokenUrl", config.oauthTokenUrl ?: "")
                            put("scopes", buildJsonObject {
                                config.securityScopes.forEach { scope ->
                                    put(scope, "Access to $scope")
                                }
                            })
                        })
                    })
                }
            }
        }
    }
    
    /**
     * Convert type definition to JSON Schema for OpenAPI
     */
    private fun typeToJsonSchema(type: TypeDefinition, config: OpenAPIConfig): JsonObject {
        val generator = JSONSchemaGenerator()
        val schemaString = generator.generate(
            type,
            SchemaFormat.JSON_SCHEMA,
            GeneratorOptions(
                pretty = false,
                includeComments = false
            )
        )
        return Json.parseToJsonElement(schemaString).jsonObject
    }
    
    /**
     * Generate operation ID from path
     */
    private fun generateOperationId(path: String): String {
        return path
            .split("/")
            .filter { it.isNotEmpty() }
            .joinToString("") { it.capitalize() }
            .replaceFirstChar { it.lowercase() }
    }
    
    companion object {
        /**
         * Quick generate method
         */
        fun generateAPI(
            transformation: Program,
            inputType: TypeDefinition,
            outputType: TypeDefinition,
            apiPath: String = "/transform",
            method: String = "POST",
            title: String = "Transformation API"
        ): String {
            return OpenAPIGenerator().generate(
                transformation,
                inputType,
                outputType,
                OpenAPIConfig(
                    apiPath = apiPath,
                    method = method,
                    title = title
                )
            )
        }
    }
}

/**
 * Configuration for OpenAPI generation
 */
data class OpenAPIConfig(
    // Basic info
    val title: String = "UTL-X Transformation API",
    val version: String = "1.0.0",
    val description: String? = "Auto-generated API for UTL-X transformation",
    val contactEmail: String? = null,
    
    // Servers
    val servers: List<ServerConfig> = listOf(
        ServerConfig("http://localhost:8080", "Development server")
    ),
    
    // API endpoint
    val apiPath: String = "/api/transform",
    val method: String = "POST",
    val operationId: String? = null,
    val operationSummary: String? = null,
    val tags: List<String>? = null,
    
    // Content types
    val inputContentType: List<String> = listOf("application/xml", "application/json"),
    val outputContentType: List<String> = listOf("application/json"),
    
    // Descriptions
    val requestDescription: String? = null,
    val successDescription: String? = null,
    
    // Examples
    val includeExamples: Boolean = false,
    val inputExample: String? = null,
    val outputExample: String? = null,
    
    // Security
    val requiresAuth: Boolean = false,
    val authType: AuthType = AuthType.BEARER,
    val securityScheme: String = "bearerAuth",
    val securityScopes: List<String> = emptyList(),
    val oauthAuthUrl: String? = null,
    val oauthTokenUrl: String? = null,
    
    // Formatting
    val pretty: Boolean = true
)

data class ServerConfig(
    val url: String,
    val description: String? = null
)

enum class AuthType {
    BEARER,
    API_KEY,
    OAUTH2
}

/**
 * CLI command for OpenAPI generation
 */
fun generateOpenAPISpec(
    transformFile: String,
    inputSchemaFile: String,
    outputSchemaFile: String?,
    config: OpenAPIConfig
): String {
    // Parse transformation
    val parser = org.apache.utlx.core.parser.Parser()
    val program = parser.parse(java.io.File(transformFile).readText())
    
    // Parse input schema
    val xsdParser = XSDSchemaParser()
    val inputType = xsdParser.parse(
        java.io.File(inputSchemaFile).readText(),
        SchemaFormat.XSD
    )
    
    // Infer or parse output schema
    val outputType = if (outputSchemaFile != null) {
        val jsonParser = JSONSchemaParser()
        jsonParser.parse(
            java.io.File(outputSchemaFile).readText(),
            SchemaFormat.JSON_SCHEMA
        )
    } else {
        val inference = AdvancedTypeInference(inputType)
        inference.inferOutputType(program)
    }
    
    // Generate OpenAPI spec
    return OpenAPIGenerator().generate(program, inputType, outputType, config)
}
