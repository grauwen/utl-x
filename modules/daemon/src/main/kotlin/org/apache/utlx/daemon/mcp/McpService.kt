// modules/server/src/main/kotlin/org/apache/utlx/server/mcp/McpService.kt
package org.apache.utlx.daemon.mcp

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import kotlinx.coroutines.withTimeout
import org.apache.utlx.core.ast.Program
import org.apache.utlx.core.interpreter.Interpreter
import org.apache.utlx.core.interpreter.RuntimeValue
import org.apache.utlx.core.lexer.Lexer
import org.apache.utlx.core.parser.Parser
import org.apache.utlx.core.udm.UDM
import org.slf4j.LoggerFactory
import kotlin.time.Duration.Companion.seconds

/**
 * MCP Service - Business logic for MCP tool operations
 *
 * Handles transformations, validation, and schema generation
 * using the UTL-X core engine.
 */
class McpService(
    private val maxTransformSizeMb: Int = 10,
    private val timeoutSeconds: Int = 30
) {
    private val logger = LoggerFactory.getLogger(McpService::class.java)
    private val objectMapper: ObjectMapper = jacksonObjectMapper()
    private val interpreter = Interpreter()

    companion object {
        private const val VERSION = "1.0.0-SNAPSHOT"

        /**
         * List of available MCP tools
         */
        val AVAILABLE_TOOLS = listOf(
            McpTool(
                name = "utlx_transform",
                description = "Transform data using UTL-X scripts. Supports JSON, XML, CSV, YAML, Avro, and more.",
                inputSchema = mapOf(
                    "type" to "object",
                    "properties" to mapOf(
                        "script" to mapOf(
                            "type" to "string",
                            "description" to "UTL-X transformation script"
                        ),
                        "input" to mapOf(
                            "type" to "object",
                            "description" to "Input data to transform"
                        ),
                        "input_format" to mapOf(
                            "type" to "string",
                            "enum" to listOf("json", "xml", "csv", "yaml", "avro", "protobuf"),
                            "default" to "json"
                        ),
                        "output_format" to mapOf(
                            "type" to "string",
                            "enum" to listOf("json", "xml", "csv", "yaml", "avro", "protobuf"),
                            "default" to "json"
                        )
                    ),
                    "required" to listOf("script", "input")
                )
            ),
            McpTool(
                name = "utlx_validate",
                description = "Validate UTL-X scripts for syntax, semantic, or schema errors.",
                inputSchema = mapOf(
                    "type" to "object",
                    "properties" to mapOf(
                        "script" to mapOf(
                            "type" to "string",
                            "description" to "UTL-X script to validate"
                        ),
                        "level" to mapOf(
                            "type" to "string",
                            "enum" to listOf("syntax", "semantic", "schema"),
                            "default" to "syntax",
                            "description" to "Validation level"
                        ),
                        "schema" to mapOf(
                            "type" to "object",
                            "description" to "Input schema for schema-level validation"
                        )
                    ),
                    "required" to listOf("script")
                )
            ),
            McpTool(
                name = "utlx_generate_schema",
                description = "Generate JSON Schema, Avro, or XSD from UTL-X script output.",
                inputSchema = mapOf(
                    "type" to "object",
                    "properties" to mapOf(
                        "script" to mapOf(
                            "type" to "string",
                            "description" to "UTL-X script"
                        ),
                        "schema_type" to mapOf(
                            "type" to "string",
                            "enum" to listOf("json_schema", "avro", "xsd"),
                            "default" to "json_schema"
                        )
                    ),
                    "required" to listOf("script")
                )
            )
        )
    }

    /**
     * List available MCP tools
     */
    fun listTools(): ToolsListResponse {
        return ToolsListResponse(tools = AVAILABLE_TOOLS)
    }

    /**
     * Transform data using UTL-X script
     */
    suspend fun transform(request: TransformRequest): TransformResponse {
        try {
            // Validate input size
            val inputSize = estimateSizeMb(request.input)
            if (inputSize > maxTransformSizeMb) {
                throw McpException(
                    JsonRpcError.sizeLimitError(
                        "Input size ($inputSize MB) exceeds limit ($maxTransformSizeMb MB)"
                    )
                )
            }

            // Parse and execute script with timeout
            val result = withTimeout(timeoutSeconds.seconds) {
                executeScript(request.script, request.input)
            }

            logger.info("Transform completed successfully")
            return TransformResponse(
                output = result,
                format = request.outputFormat ?: "json",
                metadata = mapOf(
                    "execution_time_ms" to System.currentTimeMillis()
                )
            )
        } catch (e: Exception) {
            logger.error("Transform failed: ${e.message}", e)
            throw when (e) {
                is McpException -> e
                is kotlinx.coroutines.TimeoutCancellationException ->
                    McpException(JsonRpcError.timeoutError("Transform timed out after $timeoutSeconds seconds"))
                else ->
                    McpException(JsonRpcError.transformError("Transform failed: ${e.message}"))
            }
        }
    }

    /**
     * Validate UTL-X script
     */
    suspend fun validate(request: ValidateRequest): ValidateResponse {
        try {
            val errors = mutableListOf<ValidationError>()
            val warnings = mutableListOf<ValidationWarning>()

            // Level 1: Syntax validation
            try {
                // Tokenize the script
                val lexer = Lexer(request.script)
                val tokens = lexer.tokenize()

                // Parse the tokens
                val parser = Parser(tokens)
                val parseResult = parser.parse()

                when (parseResult) {
                    is org.apache.utlx.core.parser.ParseResult.Failure -> {
                        // Add parse errors
                        parseResult.errors.forEach { error ->
                            errors.add(
                                ValidationError(
                                    message = error.message,
                                    line = error.location.line,
                                    column = error.location.column,
                                    severity = "error",
                                    code = "PARSE_ERROR"
                                )
                            )
                        }
                    }
                    is org.apache.utlx.core.parser.ParseResult.Success -> {
                        logger.debug("Syntax validation passed")

                        // Level 2: Semantic validation (if requested)
                        if (request.level in listOf("semantic", "schema")) {
                            // TODO: Add semantic validation using analysis module
                            logger.debug("Semantic validation not yet implemented")
                        }

                        // Level 3: Schema validation (if requested)
                        if (request.level == "schema" && request.schema != null) {
                            // TODO: Add schema validation using analysis module
                            logger.debug("Schema validation not yet implemented")
                        }
                    }
                }

            } catch (e: Exception) {
                errors.add(
                    ValidationError(
                        message = e.message ?: "Parse error",
                        severity = "error",
                        code = "PARSE_ERROR"
                    )
                )
            }

            return ValidateResponse(
                valid = errors.isEmpty(),
                errors = errors,
                warnings = warnings
            )
        } catch (e: Exception) {
            logger.error("Validation failed: ${e.message}", e)
            throw McpException(JsonRpcError.validationError("Validation failed: ${e.message}"))
        }
    }

    /**
     * Generate schema from UTL-X script
     */
    suspend fun generateSchema(request: GenerateSchemaRequest): GenerateSchemaResponse {
        try {
            // TODO: Implement schema generation using analysis module
            logger.warn("Schema generation not yet fully implemented")

            // For now, return a placeholder schema
            val schema = mapOf(
                "type" to "object",
                "description" to "Generated from UTL-X script",
                "properties" to mapOf<String, Any>()
            )

            return GenerateSchemaResponse(
                schema = schema,
                format = request.schemaType,
                metadata = mapOf(
                    "generated_at" to System.currentTimeMillis()
                )
            )
        } catch (e: Exception) {
            logger.error("Schema generation failed: ${e.message}", e)
            throw McpException(JsonRpcError.internalError("Schema generation failed: ${e.message}"))
        }
    }

    /**
     * Get server status
     */
    fun getStatus(uptime: Long, activeSessions: Int, totalRequests: Long): ServerStatusResponse {
        return ServerStatusResponse(
            status = "running",
            version = VERSION,
            uptime = uptime,
            activeSessions = activeSessions,
            totalRequests = totalRequests,
            features = mapOf(
                "transform" to true,
                "validate" to true,
                "schema_generation" to false,  // Not yet implemented
                "lsp" to true,
                "rest_api" to true
            )
        )
    }

    /**
     * Execute UTL-X script with input data
     */
    private fun executeScript(scriptText: String, input: Any?): Any {
        // Tokenize the script
        val lexer = Lexer(scriptText)
        val tokens = lexer.tokenize()

        // Parse the tokens
        val parser = Parser(tokens)
        val parseResult = parser.parse()

        val program = when (parseResult) {
            is org.apache.utlx.core.parser.ParseResult.Failure -> {
                throw McpException(JsonRpcError.parseError("Failed to parse script: ${parseResult.errors.firstOrNull()?.message}"))
            }
            is org.apache.utlx.core.parser.ParseResult.Success -> parseResult.program
        }

        // Convert input to UDM
        val inputUdm = when (input) {
            null -> UDM.Scalar(null)
            is String -> objectMapper.readTree(input).let { convertToUDM(it) }
            else -> convertToUDM(objectMapper.valueToTree(input))
        }

        // Execute transformation
        val result = interpreter.execute(program, inputUdm)

        // Convert result back to JSON
        return when (result) {
            is RuntimeValue.UDMValue -> convertFromUDM(result.udm) ?: emptyMap<String, Any>()
            is RuntimeValue.StringValue -> result.value
            is RuntimeValue.NumberValue -> result.value
            is RuntimeValue.BooleanValue -> result.value
            is RuntimeValue.NullValue -> emptyMap<String, Any>()
            else -> throw McpException(JsonRpcError.internalError("Unexpected result type: ${result::class.simpleName}"))
        }
    }

    /**
     * Convert Jackson JsonNode to UDM
     */
    private fun convertToUDM(node: com.fasterxml.jackson.databind.JsonNode): UDM {
        return when {
            node.isNull -> UDM.Scalar(null)
            node.isBoolean -> UDM.Scalar(node.booleanValue())
            node.isInt || node.isLong || node.isShort -> UDM.Scalar(node.longValue())
            node.isFloatingPointNumber -> UDM.Scalar(node.doubleValue())
            node.isNumber -> UDM.Scalar(node.decimalValue())
            node.isTextual -> UDM.Scalar(node.textValue())
            node.isArray -> {
                val elements = node.map { convertToUDM(it) }
                UDM.Array(elements)
            }
            node.isObject -> {
                val fields = node.fields().asSequence().associate { (key, value) ->
                    key to convertToUDM(value)
                }
                UDM.Object(fields, metadata = emptyMap())
            }
            else -> UDM.Scalar(null)
        }
    }

    /**
     * Convert UDM to Jackson-compatible object
     */
    private fun convertFromUDM(udm: UDM): Any? {
        return when (udm) {
            is UDM.Scalar -> udm.value
            is UDM.Array -> udm.elements.map { convertFromUDM(it) }
            is UDM.Object -> udm.properties.mapValues { (_, v) -> convertFromUDM(v) }
            is UDM.Binary -> udm.data.toString()  // Convert binary data to string representation
            is UDM.Date, is UDM.Time, is UDM.DateTime, is UDM.LocalDateTime -> udm.toString()
            is UDM.Lambda -> mapOf("type" to "lambda", "description" to "Function value")
        }
    }

    /**
     * Estimate size of data in MB
     */
    private fun estimateSizeMb(data: Any?): Double {
        if (data == null) return 0.0
        val json = objectMapper.writeValueAsString(data)
        return json.length / (1024.0 * 1024.0)
    }
}

/**
 * MCP-specific exception
 */
class McpException(val error: JsonRpcError) : Exception(error.message)
