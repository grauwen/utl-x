// modules/daemon/src/main/kotlin/org/apache/utlx/daemon/rest/RestApiServer.kt
package org.apache.utlx.daemon.rest

import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.cors.routing.CORS
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
import org.apache.utlx.cli.service.TransformationService
import org.apache.utlx.cli.service.UDMService
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
import org.apache.utlx.stdlib.StandardLibrary
import org.slf4j.LoggerFactory
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import java.time.Instant
import java.util.UUID

/**
 * REST API server for UTL-X daemon
 * Provides HTTP endpoints for MCP server integration
 */
class RestApiServer(
    private val port: Int = 7779,
    private val host: String = "0.0.0.0",
    private val shutdownCallback: (() -> Unit)? = null
) {
    private val logger = LoggerFactory.getLogger(RestApiServer::class.java)
    private var server: NettyApplicationEngine? = null
    private val startTime = Instant.now().toEpochMilli()
    private val scope = CoroutineScope(Dispatchers.Default)

    @Volatile
    private var shutdownRequested = false

    // Services for handling REST API requests
    private val stateManager = StateManager()
    private val transformationService = TransformationService()
    private val udmService = UDMService()
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

                // Allow common HTTP methods
                allowMethod(HttpMethod.Get)
                allowMethod(HttpMethod.Post)
                allowMethod(HttpMethod.Put)
                allowMethod(HttpMethod.Delete)
                allowMethod(HttpMethod.Options)
                allowMethod(HttpMethod.Patch)
                allowMethod(HttpMethod.Head)

                // Allow common headers
                allowHeader(HttpHeaders.ContentType)
                allowHeader(HttpHeaders.Authorization)
                allowHeader(HttpHeaders.Accept)
                allowHeader(HttpHeaders.Origin)
                allowHeader(HttpHeaders.AccessControlRequestMethod)
                allowHeader(HttpHeaders.AccessControlRequestHeaders)

                // Allow custom headers for multipart metadata
                allowHeader("X-Format")
                allowHeader("X-Encoding")
                allowHeader("X-BOM")

                // Expose headers that might be needed by the client
                exposeHeader(HttpHeaders.ContentType)
                exposeHeader(HttpHeaders.ContentLength)

                // Allow credentials
                allowCredentials = false

                // Allow any headers
                allowNonSimpleContentTypes = true
            }

            install(StatusPages) {
                // Handle Ktor's BadRequestException (includes JSON parsing errors)
                exception<io.ktor.server.plugins.BadRequestException> { call, cause ->
                    logger.debug("Bad request", cause)
                    call.respond(
                        HttpStatusCode.BadRequest,
                        ErrorResponse(
                            error = "invalid_request",
                            message = cause.message ?: "Invalid request"
                        )
                    )
                }
                // Handle JSON parsing errors
                exception<kotlinx.serialization.SerializationException> { call, cause ->
                    logger.debug("JSON parsing error", cause)
                    call.respond(
                        HttpStatusCode.BadRequest,
                        ErrorResponse(
                            error = "invalid_json",
                            message = "Invalid JSON: ${cause.message}"
                        )
                    )
                }
                exception<com.fasterxml.jackson.core.JsonParseException> { call, cause ->
                    logger.debug("JSON parsing error", cause)
                    call.respond(
                        HttpStatusCode.BadRequest,
                        ErrorResponse(
                            error = "invalid_json",
                            message = "Invalid JSON: ${cause.message}"
                        )
                    )
                }
                // Handle all other exceptions
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
                // Ping endpoint - simple liveness check
                get("/api/ping") {
                    call.respond(
                        mapOf(
                            "status" to "ok",
                            "service" to "utlx-rest-server",
                            "timestamp" to System.currentTimeMillis()
                        )
                    )
                }

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

                // Functions endpoint - return standard library function registry
                get("/api/functions") {
                    logger.debug("Functions registry requested")
                    try {
                        val registry = StandardLibrary.exportRegistry()

                        // Use Jackson to serialize (FunctionRegistry uses Jackson annotations)
                        val jacksonMapper = ObjectMapper().registerModule(KotlinModule())
                        val json = jacksonMapper.writerWithDefaultPrettyPrinter().writeValueAsString(registry)

                        call.respondText(json, ContentType.Application.Json, HttpStatusCode.OK)
                    } catch (e: Exception) {
                        logger.error("Failed to export function registry", e)
                        call.respond(
                            HttpStatusCode.InternalServerError,
                            mapOf(
                                "error" to "Failed to retrieve function registry",
                                "message" to (e.message ?: "Unknown error")
                            )
                        )
                    }
                }

                // Validation endpoint
                post("/api/validate") {
                    val request = call.receive<ValidationRequest>()
                    logger.debug("Validation request: strict=${request.strict}")

                    try {
                        // Lex and parse the UTLX code
                        val lexer = Lexer(request.utlx)
                        val tokens = lexer.tokenize()
                        val parser = Parser(tokens, request.utlx)
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
                        // Build complete UTLX document from API request
                        // The API contract allows clients to send just the transformation expression,
                        // with input/output formats specified separately in the request
                        val completeUtlx = buildUtlxDocument(
                            transformation = request.utlx,
                            inputFormat = request.inputFormat,
                            outputFormat = request.outputFormat
                        )

                        // Use TransformationService for transformation
                        val (outputData, outputFormat) = transformationService.transform(
                            utlxSource = completeUtlx,
                            inputs = mapOf("input" to TransformationService.InputData(
                                content = request.input,
                                format = request.inputFormat
                            )),
                            options = TransformationService.TransformOptions(
                                verbose = false,
                                pretty = true
                            )
                        )

                        call.respond(
                            ExecutionResponse(
                                success = true,
                                output = outputData,
                                executionTimeMs = System.currentTimeMillis() - startExec
                            )
                        )
                    } catch (e: IllegalStateException) {
                        // Parse or compile errors - return as BadRequest
                        logger.debug("Parse/compile error", e)
                        call.respond(
                            HttpStatusCode.BadRequest,
                            ExecutionResponse(
                                success = false,
                                error = e.message ?: "Transformation failed",
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

                // Multipart execution endpoint (for Theia extension with encoding support)
                post("/api/execute-multipart") {
                    logger.debug("Multipart execution request received")
                    val startExec = System.currentTimeMillis()

                    try {
                        var utlxSource: String? = null
                        val inputs = mutableListOf<Triple<ByteArray, String, MultipartInputMetadata>>()

                        // Process multipart data
                        val multipart = call.receiveMultipart()
                        multipart.forEachPart { part ->
                            when (part) {
                                is PartData.FormItem -> {
                                    // Extract UTLX source code from form field
                                    if (part.name == "utlx") {
                                        utlxSource = part.value
                                        logger.debug("Received UTLX source: ${utlxSource?.length} characters")
                                    }
                                }
                                is PartData.FileItem -> {
                                    // Extract input file with metadata from headers
                                    // Use originalFileName (user-provided name) if available, otherwise fall back to form field name
                                    val inputName = part.originalFileName ?: part.name ?: "input"
                                    val format = part.headers["X-Format"] ?: "json"
                                    val encoding = part.headers["X-Encoding"] ?: "UTF-8"
                                    val hasBOM = part.headers["X-BOM"]?.toBoolean() ?: false

                                    val bytes = part.streamProvider().readBytes()
                                    val metadata = MultipartInputMetadata(inputName, format, encoding, hasBOM)

                                    inputs.add(Triple(bytes, format, metadata))
                                    logger.debug("Received input '$inputName': ${bytes.size} bytes, format=$format, encoding=$encoding, BOM=$hasBOM")
                                }
                                else -> part.dispose()
                            }
                        }

                        // Validate we have UTLX source
                        if (utlxSource == null) {
                            call.respond(
                                HttpStatusCode.BadRequest,
                                ExecutionResponse(
                                    success = false,
                                    error = "Missing 'utlx' field in multipart request",
                                    executionTimeMs = System.currentTimeMillis() - startExec
                                )
                            )
                            return@post
                        }

                        // Validate we have at least one input
                        if (inputs.isEmpty()) {
                            call.respond(
                                HttpStatusCode.BadRequest,
                                ExecutionResponse(
                                    success = false,
                                    error = "No input files provided in multipart request",
                                    executionTimeMs = System.currentTimeMillis() - startExec
                                )
                            )
                            return@post
                        }

                        // Prepare inputs for TransformationService
                        val serviceInputs = inputs.map { (bytes, format, metadata) ->
                            // Use the actual input name from metadata (user-provided name from frontend)
                            val inputName = metadata.name
                            val content = String(bytes, charset(metadata.encoding))
                            inputName to TransformationService.InputData(
                                content = content,
                                format = format
                            )
                        }.toMap()

                        // Use TransformationService for transformation
                        val (outputData, outputFormat) = transformationService.transform(
                            utlxSource = utlxSource!!,
                            inputs = serviceInputs,
                            options = TransformationService.TransformOptions(
                                verbose = false,
                                pretty = true
                            )
                        )

                        call.respond(
                            ExecutionResponse(
                                success = true,
                                output = outputData,
                                executionTimeMs = System.currentTimeMillis() - startExec
                            )
                        )
                    } catch (e: IllegalStateException) {
                        // Parse or compile errors - return as BadRequest
                        logger.debug("Parse/compile error in multipart", e)
                        call.respond(
                            HttpStatusCode.BadRequest,
                            ExecutionResponse(
                                success = false,
                                error = e.message ?: "Transformation failed",
                                executionTimeMs = System.currentTimeMillis() - startExec
                            )
                        )
                    } catch (e: Exception) {
                        logger.error("Multipart execution error", e)
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
                        val parser = Parser(tokens, request.utlx)
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

                // UDM export endpoint: Format → UDM → .udm
                post("/api/udm/export") {
                    val request = call.receive<UDMExportRequest>()
                    logger.debug("UDM export request: format=${request.format}")

                    try {
                        // Build format options from request
                        val options = UDMService.FormatOptions(
                            prettyPrint = request.prettyPrint,
                            delimiter = request.delimiter?.firstOrNull(),
                            hasHeaders = request.hasHeaders,
                            regional = request.regional,
                            arrayHints = request.arrayHints,
                            rootName = request.rootName,
                            encoding = request.encoding,
                            multiDoc = request.multiDoc,
                            draft = request.draft,
                            version = request.version,
                            namespace = request.namespace,
                            pattern = request.pattern,
                            validate = request.validate
                        )

                        // Build source info
                        val sourceInfo = buildMap<String, String> {
                            request.sourceFile?.let { put("source", it) }
                        }

                        // Call UDMService
                        val result = udmService.export(
                            content = request.content,
                            format = request.format,
                            options = options,
                            sourceInfo = sourceInfo
                        )

                        call.respond(
                            UDMExportResponse(
                                success = true,
                                udmLanguage = result.udmLanguage,
                                sourceFormat = result.sourceFormat,
                                parsedAt = result.parsedAt
                            )
                        )
                    } catch (e: IllegalArgumentException) {
                        logger.debug("Invalid UDM export request", e)
                        call.respond(
                            HttpStatusCode.BadRequest,
                            UDMExportResponse(
                                success = false,
                                error = e.message ?: "Invalid request"
                            )
                        )
                    } catch (e: Exception) {
                        logger.error("UDM export error", e)
                        call.respond(
                            HttpStatusCode.InternalServerError,
                            UDMExportResponse(
                                success = false,
                                error = "Export failed: ${e.message}"
                            )
                        )
                    }
                }

                // UDM import endpoint: .udm → UDM → Format
                post("/api/udm/import") {
                    val request = call.receive<UDMImportRequest>()
                    logger.debug("UDM import request: targetFormat=${request.targetFormat}")

                    try {
                        // Build format options from request
                        val options = UDMService.FormatOptions(
                            prettyPrint = request.prettyPrint,
                            delimiter = request.delimiter?.firstOrNull(),
                            hasHeaders = request.hasHeaders,
                            regional = request.regional,
                            arrayHints = request.arrayHints,
                            rootName = request.rootName,
                            encoding = request.encoding,
                            multiDoc = request.multiDoc,
                            draft = request.draft,
                            version = request.version,
                            namespace = request.namespace,
                            pattern = request.pattern,
                            validate = request.validate
                        )

                        // Call UDMService
                        val result = udmService.import(
                            udmLanguage = request.udmLanguage,
                            targetFormat = request.targetFormat,
                            options = options
                        )

                        call.respond(
                            UDMImportResponse(
                                success = true,
                                output = result.output,
                                targetFormat = result.targetFormat,
                                sourceInfo = result.sourceInfo
                            )
                        )
                    } catch (e: IllegalArgumentException) {
                        logger.debug("Invalid UDM import request", e)
                        call.respond(
                            HttpStatusCode.BadRequest,
                            UDMImportResponse(
                                success = false,
                                error = e.message ?: "Invalid request"
                            )
                        )
                    } catch (e: Exception) {
                        logger.error("UDM import error", e)
                        call.respond(
                            HttpStatusCode.InternalServerError,
                            UDMImportResponse(
                                success = false,
                                error = "Import failed: ${e.message}"
                            )
                        )
                    }
                }

                // UDM validate endpoint: Check .udm syntax
                post("/api/udm/validate") {
                    val request = call.receive<UDMValidateRequest>()
                    logger.debug("UDM validate request")

                    try {
                        val result = udmService.validate(request.udmLanguage)

                        call.respond(
                            UDMValidateResponse(
                                valid = result.valid,
                                errors = result.errors,
                                udmVersion = result.udmVersion,
                                sourceInfo = result.sourceInfo
                            )
                        )
                    } catch (e: Exception) {
                        logger.error("UDM validate error", e)
                        call.respond(
                            HttpStatusCode.InternalServerError,
                            UDMValidateResponse(
                                valid = false,
                                errors = listOf("Validation failed: ${e.message}")
                            )
                        )
                    }
                }

                // Shutdown endpoint
                post("/api/shutdown") {
                    try {
                        if (shutdownRequested) {
                            call.respond(
                                HttpStatusCode.OK,
                                mapOf(
                                    "status" to "shutdown_already_requested",
                                    "message" to "Shutdown is already in progress"
                                )
                            )
                            return@post
                        }

                        shutdownRequested = true
                        logger.info("Shutdown requested via REST API")

                        call.respond(
                            HttpStatusCode.OK,
                            mapOf(
                                "status" to "shutting_down",
                                "message" to "UTLXD daemon is shutting down gracefully"
                            )
                        )

                        // Schedule shutdown after response is sent
                        scope.launch {
                            kotlinx.coroutines.delay(500) // Give time for response to be sent
                            logger.info("Invoking shutdown callback...")
                            shutdownCallback?.invoke()
                        }
                    } catch (e: Exception) {
                        logger.error("Shutdown error", e)
                        call.respond(
                            HttpStatusCode.InternalServerError,
                            mapOf("error" to e.message)
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
     * Parse input data from bytes with specific encoding and BOM handling
     */
    private fun parseInputBytes(
        bytes: ByteArray,
        format: String,
        encoding: String,
        hasBOM: Boolean
    ): org.apache.utlx.core.udm.UDM {
        // Handle BOM (Byte Order Mark) if present
        val processedBytes = if (hasBOM) {
            when (encoding.uppercase()) {
                "UTF-8" -> {
                    // UTF-8 BOM is EF BB BF
                    if (bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()) {
                        bytes.copyOfRange(3, bytes.size)
                    } else bytes
                }
                "UTF-16LE" -> {
                    // UTF-16LE BOM is FF FE
                    if (bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte()) {
                        bytes.copyOfRange(2, bytes.size)
                    } else bytes
                }
                "UTF-16BE" -> {
                    // UTF-16BE BOM is FE FF
                    if (bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte()) {
                        bytes.copyOfRange(2, bytes.size)
                    } else bytes
                }
                else -> bytes
            }
        } else bytes

        // Decode bytes with specified encoding
        val charset = java.nio.charset.Charset.forName(encoding)
        val data = String(processedBytes, charset)

        // Use existing parseInput method
        return parseInput(data, format)
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

    /**
     * Build a complete UTLX document from API request components.
     * The REST API allows clients to send just the transformation expression,
     * with input/output formats specified separately. This helper builds the
     * complete UTLX document with proper headers.
     */
    private fun buildUtlxDocument(
        transformation: String,
        inputFormat: String,
        outputFormat: String
    ): String {
        // If transformation already has a header, return as-is
        if (transformation.trimStart().startsWith("%utlx")) {
            return transformation
        }

        // Strip legacy "output:" prefix if present (UTLX 0.x syntax)
        val cleanTransformation = transformation.trimStart()
            .removePrefix("output:")
            .trimStart()

        // Build complete UTLX document with header
        return """
            %utlx 1.0
            input $inputFormat
            output $outputFormat
            ---
            $cleanTransformation
        """.trimIndent()
    }
}
