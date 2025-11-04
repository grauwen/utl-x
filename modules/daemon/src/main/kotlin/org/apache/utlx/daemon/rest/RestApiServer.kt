// modules/daemon/src/main/kotlin/org/apache/utlx/daemon/rest/RestApiServer.kt
package org.apache.utlx.daemon.rest

import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import org.apache.utlx.analysis.schema.XSDSchemaParser
import org.apache.utlx.analysis.schema.JSONSchemaParser
import org.apache.utlx.analysis.schema.SchemaFormat as AnalysisSchemaFormat
import org.apache.utlx.core.lexer.Lexer
import org.apache.utlx.core.parser.Parser
import org.apache.utlx.core.parser.ParseResult
import org.apache.utlx.core.types.TypeChecker
import org.apache.utlx.core.types.TypeCheckResult
import org.apache.utlx.core.interpreter.Interpreter
import org.apache.utlx.daemon.state.StateManager
import org.apache.utlx.daemon.schema.OutputSchemaInferenceService
import org.apache.utlx.daemon.schema.InferenceResult
import org.apache.utlx.formats.xml.XMLParser
import org.apache.utlx.formats.json.JSONParser as UtlxJSONParser
import org.apache.utlx.formats.json.JSONSerializer
import org.apache.utlx.formats.xml.XMLSerializer
import org.apache.utlx.formats.csv.CSVParser
import org.apache.utlx.formats.csv.CSVSerializer
import org.apache.utlx.formats.csv.CSVDialect
import org.apache.utlx.formats.yaml.YAMLParser
import org.apache.utlx.formats.yaml.YAMLSerializer
import org.apache.utlx.formats.xsd.XSDParser
import org.apache.utlx.formats.xsd.XSDSerializer
import org.apache.utlx.formats.jsch.JSONSchemaParser as JschParser
import org.apache.utlx.formats.jsch.JSONSchemaSerializer as JschSerializer
import org.apache.utlx.formats.avro.AvroSchemaParser
import org.apache.utlx.formats.avro.AvroSchemaSerializer
import org.apache.utlx.formats.protobuf.ProtobufSchemaParser
import org.apache.utlx.formats.protobuf.ProtobufSchemaSerializer
import org.slf4j.LoggerFactory
import java.time.Instant
import java.util.UUID

/**
 * REST API server for UTL-X daemon
 * Provides HTTP endpoints for MCP server integration
 */
class RestApiServer(
    private val port: Int = 7779,
    private val host: String = "0.0.0.0"
) {
    private val logger = LoggerFactory.getLogger(RestApiServer::class.java)
    private var server: NettyApplicationEngine? = null
    private val startTime = Instant.now().toEpochMilli()
    private val scope = CoroutineScope(Dispatchers.Default)

    // Services for handling REST API requests
    private val stateManager = StateManager()
    private val outputSchemaService = OutputSchemaInferenceService(stateManager)

    /**
     * Start the REST API server
     */
    fun start() {
        logger.info("Starting REST API server on $host:$port")

        server = embeddedServer(Netty, port = port, host = host) {
            install(ContentNegotiation) {
                json(Json {
                    prettyPrint = true
                    isLenient = true
                    ignoreUnknownKeys = true
                })
            }

            install(CORS) {
                anyHost()
                allowMethod(HttpMethod.Get)
                allowMethod(HttpMethod.Post)
                allowMethod(HttpMethod.Put)
                allowMethod(HttpMethod.Delete)
                allowMethod(HttpMethod.Options)
                allowHeader(HttpHeaders.ContentType)
                allowHeader(HttpHeaders.Authorization)
            }

            install(StatusPages) {
                exception<Throwable> { call, cause ->
                    logger.error("Unhandled exception", cause)
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        ErrorResponse(
                            error = "internal_error",
                            message = cause.message ?: "Unknown error"
                        )
                    )
                }
            }

            routing {
                // Health check endpoint
                get("/api/health") {
                    call.respond(
                        HealthResponse(
                            status = "ok",
                            version = "1.0.0-SNAPSHOT",
                            uptime = System.currentTimeMillis() - startTime
                        )
                    )
                }

                // Validation endpoint
                post("/api/validate") {
                    val request = call.receive<ValidationRequest>()
                    logger.debug("Validation request: strict=${request.strict}")

                    try {
                        // Lex and parse the UTLX code
                        val lexer = Lexer(request.utlx)
                        val tokens = lexer.tokenize()
                        val parser = Parser(tokens)
                        val parseResult = parser.parse()

                        val diagnostics = mutableListOf<Diagnostic>()

                        when (parseResult) {
                            is ParseResult.Success -> {
                                logger.debug("Parse successful, running type checker")

                                // Run type checker
                                val stdlib = org.apache.utlx.core.types.StandardLibrary()
                                val typeChecker = TypeChecker(stdlib)
                                val typeCheckResult = typeChecker.check(parseResult.program)

                                when (typeCheckResult) {
                                    is TypeCheckResult.Success -> {
                                        logger.debug("Type check successful")
                                        // No errors - valid!
                                    }
                                    is TypeCheckResult.Failure -> {
                                        // Add type errors as diagnostics
                                        typeCheckResult.errors.forEach { error ->
                                            diagnostics.add(
                                                Diagnostic(
                                                    severity = if (request.strict) "error" else "warning",
                                                    message = error.message,
                                                    line = error.location?.line,
                                                    column = error.location?.column,
                                                    source = "type-checker"
                                                )
                                            )
                                        }
                                    }
                                }
                            }
                            is ParseResult.Failure -> {
                                // Add parse errors as diagnostics
                                parseResult.errors.forEach { error ->
                                    diagnostics.add(
                                        Diagnostic(
                                            severity = "error",
                                            message = error.message,
                                            line = error.location?.line,
                                            column = error.location?.column,
                                            source = "parser"
                                        )
                                    )
                                }
                            }
                        }

                        // Valid if no errors (or only warnings in non-strict mode)
                        val valid = if (request.strict) {
                            diagnostics.isEmpty()
                        } else {
                            diagnostics.none { it.severity == "error" }
                        }

                        call.respond(
                            ValidationResponse(
                                valid = valid,
                                diagnostics = diagnostics
                            )
                        )
                    } catch (e: Exception) {
                        logger.error("Validation error", e)
                        call.respond(
                            HttpStatusCode.InternalServerError,
                            ValidationResponse(
                                valid = false,
                                diagnostics = listOf(
                                    Diagnostic(
                                        severity = "error",
                                        message = "Validation failed: ${e.message}",
                                        source = "validator"
                                    )
                                )
                            )
                        )
                    }
                }

                // Execution endpoint
                post("/api/execute") {
                    val request = call.receive<ExecutionRequest>()
                    logger.debug("Execution request: inputFormat=${request.inputFormat}, outputFormat=${request.outputFormat}")

                    val startExec = System.currentTimeMillis()

                    try {
                        // 1. Compile the UTLX script
                        val lexer = Lexer(request.utlx)
                        val tokens = lexer.tokenize()
                        val parser = Parser(tokens)
                        val parseResult = parser.parse()

                        val program = when (parseResult) {
                            is ParseResult.Success -> parseResult.program
                            is ParseResult.Failure -> {
                                val errors = parseResult.errors.joinToString("\n") { error ->
                                    "  ${error.message} at ${error.location}"
                                }
                                call.respond(
                                    HttpStatusCode.BadRequest,
                                    ExecutionResponse(
                                        success = false,
                                        error = "Parse errors:\n$errors",
                                        executionTimeMs = System.currentTimeMillis() - startExec
                                    )
                                )
                                return@post
                            }
                        }

                        // 2. Parse input data
                        val inputUDM = parseInput(request.input, request.inputFormat)

                        // 3. Execute transformation
                        val interpreter = Interpreter()
                        val result = interpreter.execute(program, mapOf("input" to inputUDM))
                        val outputUDM = result.toUDM()

                        // 4. Serialize output
                        val outputData = serializeOutput(outputUDM, request.outputFormat, pretty = true)

                        call.respond(
                            ExecutionResponse(
                                success = true,
                                output = outputData,
                                executionTimeMs = System.currentTimeMillis() - startExec
                            )
                        )
                    } catch (e: Exception) {
                        logger.error("Execution error", e)
                        call.respond(
                            HttpStatusCode.InternalServerError,
                            ExecutionResponse(
                                success = false,
                                error = "Execution failed: ${e.message}",
                                executionTimeMs = System.currentTimeMillis() - startExec
                            )
                        )
                    }
                }

                // Schema inference endpoint
                post("/api/infer-schema") {
                    val request = call.receive<InferSchemaRequest>()
                    logger.debug("Schema inference request: format=${request.format}")

                    try {
                        // 1. Parse the UTLX script
                        val lexer = Lexer(request.utlx)
                        val tokens = lexer.tokenize()
                        val parser = Parser(tokens)
                        val parseResult = parser.parse()

                        val program = when (parseResult) {
                            is ParseResult.Success -> parseResult.program
                            is ParseResult.Failure -> {
                                val errors = parseResult.errors.joinToString("\n") { error ->
                                    "  ${error.message} at ${error.location}"
                                }
                                call.respond(
                                    HttpStatusCode.BadRequest,
                                    InferSchemaResponse(
                                        success = false,
                                        schemaFormat = request.format,
                                        error = "Parse errors:\n$errors"
                                    )
                                )
                                return@post
                            }
                        }

                        // 2. Create a temporary document URI for state management
                        val tempUri = "temp://${UUID.randomUUID()}.utlx"
                        stateManager.openDocument(tempUri, request.utlx, 1)
                        stateManager.setAst(tempUri, program)

                        // 3. If input schema provided, parse and set it as type environment
                        if (request.inputSchema != null) {
                            try {
                                val typeDef = when (request.format.lowercase()) {
                                    "json-schema" -> {
                                        val schemaParser = JSONSchemaParser()
                                        schemaParser.parse(request.inputSchema, AnalysisSchemaFormat.JSON_SCHEMA)
                                    }
                                    "xsd" -> {
                                        val schemaParser = XSDSchemaParser()
                                        schemaParser.parse(request.inputSchema, AnalysisSchemaFormat.XSD)
                                    }
                                    else -> {
                                        call.respond(
                                            HttpStatusCode.BadRequest,
                                            InferSchemaResponse(
                                                success = false,
                                                schemaFormat = request.format,
                                                error = "Unsupported schema format: ${request.format}"
                                            )
                                        )
                                        return@post
                                    }
                                }

                                val typeContext = org.apache.utlx.analysis.types.TypeContext(inputType = typeDef)
                                typeContext.bind("input", typeDef)
                                stateManager.setTypeEnvironment(tempUri, typeContext)
                            } catch (e: Exception) {
                                logger.warn("Failed to parse input schema, proceeding without it", e)
                            }
                        }

                        // 4. Use OutputSchemaInferenceService to infer output schema
                        val inferenceResult = outputSchemaService.inferOutputSchemaWithValidation(tempUri, pretty = true)

                        // 5. Clean up temporary document
                        stateManager.closeDocument(tempUri)

                        when (inferenceResult) {
                            is InferenceResult.Success -> {
                                call.respond(
                                    InferSchemaResponse(
                                        success = true,
                                        schema = inferenceResult.schema,
                                        schemaFormat = "json-schema",
                                        confidence = 1.0
                                    )
                                )
                            }
                            is InferenceResult.Failure -> {
                                call.respond(
                                    HttpStatusCode.InternalServerError,
                                    InferSchemaResponse(
                                        success = false,
                                        schemaFormat = request.format,
                                        error = inferenceResult.error
                                    )
                                )
                            }
                        }
                    } catch (e: Exception) {
                        logger.error("Schema inference error", e)
                        call.respond(
                            HttpStatusCode.InternalServerError,
                            InferSchemaResponse(
                                success = false,
                                schemaFormat = request.format,
                                error = "Schema inference failed: ${e.message}"
                            )
                        )
                    }
                }

                // Schema parsing endpoint
                post("/api/parse-schema") {
                    val request = call.receive<ParseSchemaRequest>()
                    logger.debug("Parse schema request: format=${request.format}")

                    try {
                        // Parse schema based on format
                        val typeDef = when (request.format.lowercase()) {
                            "xsd" -> {
                                val schemaParser = XSDSchemaParser()
                                schemaParser.parse(request.schema, AnalysisSchemaFormat.XSD)
                            }
                            "json-schema", "jsonschema" -> {
                                val schemaParser = JSONSchemaParser()
                                schemaParser.parse(request.schema, AnalysisSchemaFormat.JSON_SCHEMA)
                            }
                            else -> {
                                call.respond(
                                    HttpStatusCode.BadRequest,
                                    ParseSchemaResponse(
                                        success = false,
                                        error = "Unsupported schema format: ${request.format}. Supported formats: xsd, json-schema"
                                    )
                                )
                                return@post
                            }
                        }

                        // Convert TypeDefinition to a normalized JSON representation
                        val normalized = serializeTypeDefinition(typeDef)

                        call.respond(
                            ParseSchemaResponse(
                                success = true,
                                normalized = normalized
                            )
                        )
                    } catch (e: Exception) {
                        logger.error("Schema parsing error", e)
                        call.respond(
                            HttpStatusCode.InternalServerError,
                            ParseSchemaResponse(
                                success = false,
                                error = "Schema parsing failed: ${e.message}"
                            )
                        )
                    }
                }
            }
        }

        scope.launch {
            server?.start(wait = false)
            logger.info("REST API server started successfully on $host:$port")
        }
    }

    /**
     * Stop the REST API server
     */
    fun stop() {
        logger.info("Stopping REST API server")
        server?.stop(1000, 2000)
        logger.info("REST API server stopped")
    }

    /**
     * Parse input data based on format
     */
    private fun parseInput(data: String, format: String): org.apache.utlx.core.udm.UDM {
        return when (format.lowercase()) {
            "xml" -> XMLParser(data).parse()
            "json" -> UtlxJSONParser(data).parse()
            "csv" -> {
                val dialect = CSVDialect(delimiter = ',')
                CSVParser(data, dialect).parse(hasHeaders = true)
            }
            "yaml", "yml" -> YAMLParser().parse(data)
            "xsd" -> {
                XSDParser(data).parse()
            }
            "jsch", "json-schema", "jsonschema" -> {
                JschParser(data).parse()
            }
            "avro", "avsc" -> {
                AvroSchemaParser().parse(data)
            }
            "proto", "protobuf" -> {
                ProtobufSchemaParser().parse(data)
            }
            else -> throw IllegalArgumentException("Unsupported input format: $format. Supported formats: xml, json, csv, yaml, xsd, jsch, avro, proto")
        }
    }

    /**
     * Serialize output data based on format
     */
    private fun serializeOutput(
        udm: org.apache.utlx.core.udm.UDM,
        format: String,
        pretty: Boolean
    ): String {
        return when (format.lowercase()) {
            "xml" -> XMLSerializer(prettyPrint = pretty).serialize(udm)
            "json" -> JSONSerializer(pretty).serialize(udm)
            "csv" -> {
                val dialect = CSVDialect(delimiter = ',')
                CSVSerializer(dialect, includeHeaders = true).serialize(udm)
            }
            "yaml", "yml" -> YAMLSerializer().serialize(udm)
            "xsd" -> {
                XSDSerializer(
                    prettyPrint = pretty
                ).serialize(udm)
            }
            "jsch", "json-schema", "jsonschema" -> {
                JschSerializer(
                    prettyPrint = pretty
                ).serialize(udm)
            }
            "avro", "avsc" -> {
                AvroSchemaSerializer(
                    prettyPrint = pretty
                ).serialize(udm)
            }
            "proto", "protobuf" -> {
                ProtobufSchemaSerializer().serialize(udm)
            }
            else -> throw IllegalArgumentException("Unsupported output format: $format. Supported formats: xml, json, csv, yaml, xsd, jsch, avro, proto")
        }
    }

    /**
     * Serialize TypeDefinition to JSON for normalized schema representation
     */
    private fun serializeTypeDefinition(typeDef: org.apache.utlx.analysis.types.TypeDefinition): String {
        // Create a simple JSON representation of the type definition
        // This is a normalized format that can be used by the MCP server
        val jsonObj = mutableMapOf<String, Any>()

        when (typeDef) {
            is org.apache.utlx.analysis.types.TypeDefinition.Scalar -> {
                jsonObj["type"] = "scalar"
                jsonObj["kind"] = typeDef.kind.name.lowercase()
            }
            is org.apache.utlx.analysis.types.TypeDefinition.Object -> {
                jsonObj["type"] = "object"
                jsonObj["properties"] = typeDef.properties.mapValues { (_, prop) ->
                    mapOf(
                        "type" to describeType(prop.type),
                        "nullable" to prop.nullable
                    )
                }
                jsonObj["required"] = typeDef.required.toList()
            }
            is org.apache.utlx.analysis.types.TypeDefinition.Array -> {
                jsonObj["type"] = "array"
                jsonObj["elementType"] = describeType(typeDef.elementType)
            }
            is org.apache.utlx.analysis.types.TypeDefinition.Union -> {
                jsonObj["type"] = "union"
                jsonObj["types"] = typeDef.types.map { describeType(it) }
            }
            is org.apache.utlx.analysis.types.TypeDefinition.Any -> {
                jsonObj["type"] = "any"
            }
            is org.apache.utlx.analysis.types.TypeDefinition.Unknown -> {
                jsonObj["type"] = "unknown"
            }
            is org.apache.utlx.analysis.types.TypeDefinition.Never -> {
                jsonObj["type"] = "never"
            }
        }

        // Convert to JSON string
        return Json.encodeToString(
            kotlinx.serialization.json.JsonObject.serializer(),
            kotlinx.serialization.json.JsonObject(
                jsonObj.mapValues { (_, value) ->
                    when (value) {
                        is String -> kotlinx.serialization.json.JsonPrimitive(value)
                        is Boolean -> kotlinx.serialization.json.JsonPrimitive(value)
                        is Map<*, *> -> kotlinx.serialization.json.JsonObject(
                            (value as Map<String, Any>).mapValues { (_, v) ->
                                kotlinx.serialization.json.JsonPrimitive(v.toString())
                            }
                        )
                        is List<*> -> kotlinx.serialization.json.JsonArray(
                            (value as List<Any>).map { kotlinx.serialization.json.JsonPrimitive(it.toString()) }
                        )
                        else -> kotlinx.serialization.json.JsonPrimitive(value.toString())
                    }
                }
            )
        )
    }

    /**
     * Describe a type as a string
     */
    private fun describeType(typeDef: org.apache.utlx.analysis.types.TypeDefinition): String {
        return when (typeDef) {
            is org.apache.utlx.analysis.types.TypeDefinition.Scalar -> typeDef.kind.name.lowercase()
            is org.apache.utlx.analysis.types.TypeDefinition.Object -> "object"
            is org.apache.utlx.analysis.types.TypeDefinition.Array -> "array<${describeType(typeDef.elementType)}>"
            is org.apache.utlx.analysis.types.TypeDefinition.Union -> typeDef.types.joinToString(" | ") { describeType(it) }
            is org.apache.utlx.analysis.types.TypeDefinition.Any -> "any"
            is org.apache.utlx.analysis.types.TypeDefinition.Unknown -> "unknown"
            is org.apache.utlx.analysis.types.TypeDefinition.Never -> "never"
        }
    }
}
