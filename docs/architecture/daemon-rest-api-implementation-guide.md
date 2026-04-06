# Daemon REST API Implementation Guide

**Version:** 1.0
**Date:** 2025-11-03
**Status:** Implementation Specification

---

## Table of Contents

1. [What Needs to Be Done](#what-needs-to-be-done)
2. [REST API vs gRPC - Technology Choice](#rest-api-vs-grpc---technology-choice)
3. [Testing Strategy](#testing-strategy)
4. [Daemon Startup - Dual Server Mode](#daemon-startup---dual-server-mode)
5. [Implementation Order](#implementation-order)
6. [Design Impact on UTL-X](#design-impact-on-utl-x)
7. [Implementation Plan](#implementation-plan)

---

## What Needs to Be Done

### Overview

Add HTTP/REST API server to UTL-X Daemon to expose validation, execution, and schema analysis capabilities to non-LSP clients (MCP server, CI/CD tools, testing frameworks).

### Task Breakdown

#### Task 1: Add Dependencies (30 minutes)

**File:** `modules/daemon/build.gradle.kts`

```kotlin
dependencies {
    // Existing dependencies
    implementation("org.slf4j:slf4j-api:2.0.9")
    implementation("com.google.code.gson:gson:2.10.1")

    // NEW: Add Ktor for REST API
    implementation("io.ktor:ktor-server-core:2.3.7")
    implementation("io.ktor:ktor-server-netty:2.3.7")
    implementation("io.ktor:ktor-server-content-negotiation:2.3.7")
    implementation("io.ktor:ktor-serialization-kotlinx-json:2.3.7")
    implementation("io.ktor:ktor-server-cors:2.3.7")
    implementation("io.ktor:ktor-server-call-logging:2.3.7")

    // For testing
    testImplementation("io.ktor:ktor-server-tests:2.3.7")
    testImplementation("io.ktor:ktor-client-content-negotiation:2.3.7")
}
```

**Why Ktor?**
- Kotlin-native (idiomatic, type-safe)
- Lightweight and fast
- Coroutine-based (async by default)
- Easy to test
- Built-in JSON serialization

---

#### Task 2: Create REST API Server (4-6 hours)

**File:** `modules/daemon/src/main/kotlin/org/apache/utlx/daemon/api/RestApiServer.kt`

```kotlin
package org.apache.utlx.daemon.api

import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.plugins.callloging.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.http.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.apache.utlx.daemon.state.StateManager
import org.apache.utlx.core.parser.UTLXParser
import org.apache.utlx.core.runtime.TransformationExecutor
import org.apache.utlx.analysis.schema.SchemaParser
import org.apache.utlx.analysis.types.TypeInference
import org.slf4j.LoggerFactory

/**
 * REST API Server for UTL-X Daemon
 *
 * Exposes daemon capabilities via HTTP/JSON API for:
 * - MCP server (AI-assisted generation)
 * - CI/CD pipelines (validation, testing)
 * - External tools (schema analysis, execution)
 *
 * Runs alongside LSP server in the same process.
 */
class RestApiServer(
    private val port: Int = 7778,
    private val stateManager: StateManager
) {
    private val logger = LoggerFactory.getLogger(RestApiServer::class.java)
    private var server: ApplicationEngine? = null

    private val parser = UTLXParser()
    private val executor = TransformationExecutor()
    private val schemaParser = SchemaParser()
    private val typeInference = TypeInference()

    fun start() {
        logger.info("Starting REST API Server on port $port")

        server = embeddedServer(Netty, port = port) {
            install(ContentNegotiation) {
                json(Json {
                    prettyPrint = true
                    isLenient = true
                    ignoreUnknownKeys = true
                })
            }

            install(CORS) {
                anyHost() // For development - restrict in production
                allowHeader(HttpHeaders.ContentType)
                allowMethod(HttpMethod.Post)
                allowMethod(HttpMethod.Get)
            }

            install(CallLogging) {
                level = org.slf4j.event.Level.INFO
            }

            routing {
                // Health check
                get("/api/health") {
                    call.respond(HttpStatusCode.OK, HealthResponse(
                        status = "UP",
                        version = "1.0.0",
                        uptime = System.currentTimeMillis() - startTime
                    ))
                }

                // Validate UTLX transformation
                post("/api/validate") {
                    try {
                        val request = call.receive<ValidateRequest>()
                        logger.debug("Validating transformation (${request.transformation.length} chars)")

                        val parseResult = parser.parse(request.transformation)
                        val diagnostics = mutableListOf<Diagnostic>()

                        if (parseResult.errors.isNotEmpty()) {
                            parseResult.errors.forEach { error ->
                                diagnostics.add(Diagnostic(
                                    severity = "error",
                                    message = error.message,
                                    line = error.line,
                                    column = error.column
                                ))
                            }
                        }

                        call.respond(HttpStatusCode.OK, ValidateResponse(
                            valid = parseResult.errors.isEmpty(),
                            diagnostics = diagnostics
                        ))
                    } catch (e: Exception) {
                        logger.error("Validation error", e)
                        call.respond(HttpStatusCode.BadRequest, ErrorResponse(
                            error = "ValidationError",
                            message = e.message ?: "Unknown error"
                        ))
                    }
                }

                // Execute UTLX transformation
                post("/api/execute") {
                    try {
                        val request = call.receive<ExecuteRequest>()
                        logger.debug("Executing transformation")

                        val result = executor.execute(
                            transformation = request.transformation,
                            input = request.input,
                            inputFormat = request.inputFormat
                        )

                        call.respond(HttpStatusCode.OK, ExecuteResponse(
                            success = result.success,
                            output = result.output,
                            error = result.error
                        ))
                    } catch (e: Exception) {
                        logger.error("Execution error", e)
                        call.respond(HttpStatusCode.InternalServerError, ErrorResponse(
                            error = "ExecutionError",
                            message = e.message ?: "Unknown error"
                        ))
                    }
                }

                // Infer output schema
                post("/api/infer-schema") {
                    try {
                        val request = call.receive<InferSchemaRequest>()
                        logger.debug("Inferring output schema")

                        val inputSchemaObj = schemaParser.parse(
                            request.inputSchema,
                            request.inputFormat
                        )

                        val inferredSchema = typeInference.inferOutputSchema(
                            transformation = request.transformation,
                            inputSchema = inputSchemaObj
                        )

                        call.respond(HttpStatusCode.OK, InferSchemaResponse(
                            success = true,
                            schema = inferredSchema.toJsonSchema(),
                            format = "json-schema"
                        ))
                    } catch (e: Exception) {
                        logger.error("Schema inference error", e)
                        call.respond(HttpStatusCode.InternalServerError, ErrorResponse(
                            error = "InferenceError",
                            message = e.message ?: "Unknown error"
                        ))
                    }
                }

                // Parse input schema
                post("/api/parse-schema") {
                    try {
                        val request = call.receive<ParseSchemaRequest>()
                        logger.debug("Parsing schema (format: ${request.format})")

                        val parsed = schemaParser.parse(
                            request.schema,
                            request.format
                        )

                        call.respond(HttpStatusCode.OK, ParseSchemaResponse(
                            success = true,
                            parsed = parsed.toJson(),
                            elements = parsed.rootElements.map { it.name }
                        ))
                    } catch (e: Exception) {
                        logger.error("Schema parsing error", e)
                        call.respond(HttpStatusCode.BadRequest, ErrorResponse(
                            error = "ParseError",
                            message = e.message ?: "Unknown error"
                        ))
                    }
                }

                // Get standard library functions
                get("/api/stdlib") {
                    try {
                        val category = call.request.queryParameters["category"]
                        val search = call.request.queryParameters["search"]

                        val functions = loadStdlibFunctions()
                            .let { funcs ->
                                if (category != null) {
                                    funcs.filter { it.category == category }
                                } else funcs
                            }
                            .let { funcs ->
                                if (search != null) {
                                    funcs.filter {
                                        it.name.contains(search, ignoreCase = true) ||
                                        it.description.contains(search, ignoreCase = true)
                                    }
                                } else funcs
                            }

                        call.respond(HttpStatusCode.OK, StdlibResponse(
                            functions = functions,
                            count = functions.size
                        ))
                    } catch (e: Exception) {
                        logger.error("Stdlib query error", e)
                        call.respond(HttpStatusCode.InternalServerError, ErrorResponse(
                            error = "StdlibError",
                            message = e.message ?: "Unknown error"
                        ))
                    }
                }
            }
        }.start(wait = false)

        logger.info("REST API Server started successfully")
    }

    fun stop() {
        logger.info("Stopping REST API Server")
        server?.stop(
            gracePeriodMillis = 1000,
            timeoutMillis = 2000
        )
    }

    private fun loadStdlibFunctions(): List<FunctionInfo> {
        // Load from generated function registry
        val registryJson = javaClass.getResourceAsStream(
            "/stdlib/build/generated/function-registry/utlx-functions.json"
        )?.bufferedReader()?.readText()

        return if (registryJson != null) {
            Json.decodeFromString(registryJson)
        } else {
            emptyList()
        }
    }

    companion object {
        private val startTime = System.currentTimeMillis()
    }
}

// ============================================================================
// DTOs (Data Transfer Objects)
// ============================================================================

@Serializable
data class HealthResponse(
    val status: String,
    val version: String,
    val uptime: Long
)

@Serializable
data class ValidateRequest(
    val transformation: String
)

@Serializable
data class ValidateResponse(
    val valid: Boolean,
    val diagnostics: List<Diagnostic>
)

@Serializable
data class Diagnostic(
    val severity: String,  // "error", "warning", "info"
    val message: String,
    val line: Int? = null,
    val column: Int? = null
)

@Serializable
data class ExecuteRequest(
    val transformation: String,
    val input: String,
    val inputFormat: String = "json"
)

@Serializable
data class ExecuteResponse(
    val success: Boolean,
    val output: String? = null,
    val error: String? = null
)

@Serializable
data class InferSchemaRequest(
    val transformation: String,
    val inputSchema: String,
    val inputFormat: String  // "xsd", "json-schema", "avro", "protobuf"
)

@Serializable
data class InferSchemaResponse(
    val success: Boolean,
    val schema: String,
    val format: String = "json-schema"
)

@Serializable
data class ParseSchemaRequest(
    val schema: String,
    val format: String  // "xsd", "json-schema", "avro", "protobuf"
)

@Serializable
data class ParseSchemaResponse(
    val success: Boolean,
    val parsed: String,
    val elements: List<String>
)

@Serializable
data class FunctionInfo(
    val name: String,
    val category: String,
    val description: String,
    val parameters: List<Parameter>,
    val returnType: String
)

@Serializable
data class Parameter(
    val name: String,
    val type: String,
    val optional: Boolean = false
)

@Serializable
data class StdlibResponse(
    val functions: List<FunctionInfo>,
    val count: Int
)

@Serializable
data class ErrorResponse(
    val error: String,
    val message: String
)
```

---

#### Task 3: Update Daemon Server to Run Both LSP and REST API (2 hours)

**File:** `modules/daemon/src/main/kotlin/org/apache/utlx/daemon/DaemonServer.kt`

```kotlin
class UTLXDaemon(
    // LSP configuration
    private val lspTransportType: TransportType = TransportType.STDIO,
    private val lspPort: Int = 7777,

    // REST API configuration
    private val enableRestApi: Boolean = true,
    private val restApiPort: Int = 7778
) {

    private val logger = LoggerFactory.getLogger(UTLXDaemon::class.java)
    private val stateManager = StateManager()

    // LSP components
    private var lspTransport: Transport? = null
    private val completionService = CompletionService(stateManager)
    private val hoverService = HoverService(stateManager)
    private val diagnosticsPublisher = DiagnosticsPublisher(stateManager)

    // REST API components
    private var restApiServer: RestApiServer? = null

    /**
     * Start both LSP and REST API servers
     */
    fun start() {
        logger.info("=" * 60)
        logger.info("Starting UTL-X Daemon")
        logger.info("=" * 60)

        // Start LSP server (for Theia Monaco Editor)
        startLspServer()

        // Start REST API server (for MCP Server, CI/CD, tools)
        if (enableRestApi) {
            startRestApiServer()
        }

        logger.info("=" * 60)
        logger.info("UTL-X Daemon is ready")
        logger.info("  LSP:      $lspTransportType " +
                   (if (lspTransportType == TransportType.SOCKET) "port $lspPort" else ""))
        logger.info("  REST API: ${if (enableRestApi) "port $restApiPort" else "disabled"}")
        logger.info("=" * 60)
    }

    private fun startLspServer() {
        logger.info("Starting LSP Server (transport: $lspTransportType)")

        lspTransport = when (lspTransportType) {
            TransportType.STDIO -> {
                logger.info("  Transport: STDIO (parent-child process)")
                StdioTransport()
            }
            TransportType.SOCKET -> {
                logger.info("  Transport: Socket (port: $lspPort)")
                SocketTransport(lspPort)
            }
        }

        diagnosticsPublisher.setTransport(lspTransport!!)
        lspTransport!!.start { request -> handleLSPRequest(request) }

        logger.info("✓ LSP Server started")
    }

    private fun startRestApiServer() {
        logger.info("Starting REST API Server (port: $restApiPort)")

        restApiServer = RestApiServer(
            port = restApiPort,
            stateManager = stateManager
        )
        restApiServer!!.start()

        logger.info("✓ REST API Server started")
        logger.info("  Endpoints:")
        logger.info("    GET  /api/health")
        logger.info("    POST /api/validate")
        logger.info("    POST /api/execute")
        logger.info("    POST /api/infer-schema")
        logger.info("    POST /api/parse-schema")
        logger.info("    GET  /api/stdlib")
    }

    fun stop() {
        logger.info("Stopping UTL-X Daemon")
        lspTransport?.stop()
        restApiServer?.stop()
        logger.info("UTL-X Daemon stopped")
    }

    // ... existing LSP handlers ...
}
```

---

#### Task 4: Add Command-Line Arguments (1 hour)

**File:** `modules/daemon/src/main/kotlin/org/apache/utlx/daemon/Main.kt`

```kotlin
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.*
import com.github.ajalt.clikt.parameters.types.*

class DaemonCommand : CliktCommand(name = "utlx-daemon") {
    private val lspTransport by option("--lsp-transport", "-t")
        .choice("stdio", "socket")
        .default("stdio")
        .help("LSP transport type (stdio or socket)")

    private val lspPort by option("--lsp-port")
        .int()
        .default(7777)
        .help("LSP socket port (only used with --lsp-transport=socket)")

    private val restApiEnabled by option("--rest-api")
        .flag(default = true)
        .help("Enable REST API server")

    private val restApiPort by option("--rest-api-port")
        .int()
        .default(7778)
        .help("REST API server port")

    private val verbose by option("--verbose", "-v")
        .flag()
        .help("Enable verbose logging")

    override fun run() {
        // Configure logging
        if (verbose) {
            System.setProperty("org.slf4j.simpleLogger.defaultLogLevel", "DEBUG")
        }

        // Create and start daemon
        val daemon = UTLXDaemon(
            lspTransportType = if (lspTransport == "stdio")
                TransportType.STDIO else TransportType.SOCKET,
            lspPort = lspPort,
            enableRestApi = restApiEnabled,
            restApiPort = restApiPort
        )

        // Graceful shutdown
        Runtime.getRuntime().addShutdownHook(Thread {
            daemon.stop()
        })

        daemon.start()
    }
}

fun main(args: Array<String>) = DaemonCommand().main(args)
```

**Usage Examples:**

```bash
# Default: STDIO LSP + REST API on port 7778
java -jar utlx-daemon.jar

# Socket LSP + REST API
java -jar utlx-daemon.jar --lsp-transport=socket --lsp-port=7777

# LSP only (no REST API)
java -jar utlx-daemon.jar --rest-api=false

# Custom ports
java -jar utlx-daemon.jar \
  --lsp-transport=socket \
  --lsp-port=7777 \
  --rest-api-port=8080 \
  --verbose
```

---

## REST API vs gRPC - Technology Choice

### Comparison Matrix

| Criteria | REST API (Ktor) | gRPC | Winner |
|----------|----------------|------|--------|
| **Performance** | Good (HTTP/1.1 JSON) | Excellent (HTTP/2 Protobuf) | gRPC |
| **Latency** | ~5-10ms overhead | ~1-2ms overhead | gRPC |
| **Throughput** | ~1K req/s | ~10K req/s | gRPC |
| **Developer UX** | Excellent (curl, Postman, browser) | Poor (requires special tools) | REST |
| **MCP Ecosystem** | Native (MCP uses JSON-RPC over HTTP) | Not compatible | REST |
| **Browser Support** | Native | Requires gRPC-Web proxy | REST |
| **Debugging** | Easy (text-based, readable) | Hard (binary protocol) | REST |
| **Client Libraries** | Universal (any HTTP client) | Requires Protobuf codegen | REST |
| **Schema Evolution** | Flexible (JSON is lenient) | Strict (Protobuf versioning) | REST |
| **CI/CD Integration** | Trivial (curl, wget) | Requires gRPC clients | REST |
| **Implementation Time** | 2-3 days | 4-5 days (Protobuf schemas + codegen) | REST |
| **Maintenance** | Low | Medium (maintain .proto files) | REST |

### Performance Analysis

**Latency Breakdown (typical request):**

```
REST API (JSON):
  Network:        1-2ms
  JSON parsing:   1-2ms
  Processing:     3-5ms
  JSON encoding:  1-2ms
  Total:          6-11ms

gRPC (Protobuf):
  Network:        1-2ms (HTTP/2 multiplexing)
  Protobuf parse: 0.2-0.5ms
  Processing:     3-5ms
  Protobuf encode:0.2-0.5ms
  Total:          4.4-8ms

Savings: 1.6-3ms per request
```

**Is this significant for MCP use case?**

**NO**, because:
1. MCP requests are infrequent (user-initiated, not high-frequency)
2. AI generation takes 2-10 seconds (LLM latency)
3. Validation/execution takes 10-100ms (core processing)
4. 3ms network overhead is **negligible** (0.03% of total time)

**Example:** AI generation request
- LLM generation: 5000ms
- REST API overhead: 10ms (5 roundtrips × 2ms)
- gRPC overhead: 5ms (5 roundtrips × 1ms)
- **Difference: 5ms out of 5000ms = 0.1% improvement**

### Decision: REST API

**Recommendation: Use REST API (Ktor)**

**Rationale:**

1. **MCP Compatibility** ✅ Critical
   - MCP protocol is JSON-RPC over HTTP
   - REST API is native fit
   - gRPC would require a translation layer

2. **Developer Experience** ✅ Critical
   - Easy to test with curl/Postman
   - No Protobuf codegen required
   - Browser-compatible (CORS)
   - Universal client support

3. **Performance** ⚠️ Not Critical
   - 3ms latency difference is negligible
   - MCP use case is not latency-sensitive
   - Bottleneck is LLM (seconds), not network (milliseconds)

4. **Implementation Speed** ✅ Important
   - REST: 2-3 days
   - gRPC: 4-5 days (Protobuf schemas, codegen, testing)

5. **Maintenance** ✅ Important
   - REST: JSON schema is flexible
   - gRPC: Must maintain .proto files, handle versioning

**When to reconsider gRPC:**
- If UTL-X becomes a high-frequency service (>10K req/s)
- If binary payloads (large schemas) become common
- If bidirectional streaming is needed
- **None of these apply to MCP use case**

---

## Testing Strategy

### Level 1: Unit Tests (Component Testing)

**Test each REST endpoint in isolation**

**File:** `modules/daemon/src/test/kotlin/org/apache/utlx/daemon/api/RestApiServerTest.kt`

```kotlin
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlin.test.*

class RestApiServerTest {

    @Test
    fun `health endpoint returns OK`() = testApplication {
        val response = client.get("/api/health")

        assertEquals(HttpStatusCode.OK, response.status)

        val body = Json.decodeFromString<HealthResponse>(response.bodyAsText())
        assertEquals("UP", body.status)
        assertEquals("1.0.0", body.version)
    }

    @Test
    fun `validate endpoint accepts valid UTLX`() = testApplication {
        val request = ValidateRequest(
            transformation = """
                %utlx 1.0
                input json
                output json
                ---
                { "message": "hello" }
            """.trimIndent()
        )

        val response = client.post("/api/validate") {
            contentType(ContentType.Application.Json)
            setBody(Json.encodeToString(request))
        }

        assertEquals(HttpStatusCode.OK, response.status)

        val body = Json.decodeFromString<ValidateResponse>(response.bodyAsText())
        assertTrue(body.valid)
        assertTrue(body.diagnostics.isEmpty())
    }

    @Test
    fun `validate endpoint rejects invalid UTLX`() = testApplication {
        val request = ValidateRequest(
            transformation = "{ invalid syntax"
        )

        val response = client.post("/api/validate") {
            contentType(ContentType.Application.Json)
            setBody(Json.encodeToString(request))
        }

        assertEquals(HttpStatusCode.OK, response.status)

        val body = Json.decodeFromString<ValidateResponse>(response.bodyAsText())
        assertFalse(body.valid)
        assertTrue(body.diagnostics.isNotEmpty())
    }

    @Test
    fun `execute endpoint runs transformation`() = testApplication {
        val request = ExecuteRequest(
            transformation = """
                %utlx 1.0
                input json
                output json
                ---
                { "result": ${'$'}input.value * 2 }
            """.trimIndent(),
            input = """{"value": 21}""",
            inputFormat = "json"
        )

        val response = client.post("/api/execute") {
            contentType(ContentType.Application.Json)
            setBody(Json.encodeToString(request))
        }

        assertEquals(HttpStatusCode.OK, response.status)

        val body = Json.decodeFromString<ExecuteResponse>(response.bodyAsText())
        assertTrue(body.success)
        assertContains(body.output!!, "42")
    }

    @Test
    fun `parse-schema endpoint parses XSD`() = testApplication {
        val request = ParseSchemaRequest(
            schema = """
                <?xml version="1.0"?>
                <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
                  <xs:element name="Order" type="xs:string"/>
                </xs:schema>
            """.trimIndent(),
            format = "xsd"
        )

        val response = client.post("/api/parse-schema") {
            contentType(ContentType.Application.Json)
            setBody(Json.encodeToString(request))
        }

        assertEquals(HttpStatusCode.OK, response.status)

        val body = Json.decodeFromString<ParseSchemaResponse>(response.bodyAsText())
        assertTrue(body.success)
        assertContains(body.elements, "Order")
    }

    @Test
    fun `stdlib endpoint returns functions`() = testApplication {
        val response = client.get("/api/stdlib?category=string")

        assertEquals(HttpStatusCode.OK, response.status)

        val body = Json.decodeFromString<StdlibResponse>(response.bodyAsText())
        assertTrue(body.count > 0)
        assertTrue(body.functions.all { it.category == "string" })
    }
}
```

**Run unit tests:**
```bash
./gradlew :modules:daemon:test --tests "*RestApiServerTest*"
```

---

### Level 2: Integration Tests (Full Stack)

**Test with actual HTTP requests**

**File:** `modules/daemon/src/test/kotlin/org/apache/utlx/daemon/api/RestApiIntegrationTest.kt`

```kotlin
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.runBlocking
import kotlin.test.*

class RestApiIntegrationTest {

    private lateinit var daemon: UTLXDaemon
    private lateinit var httpClient: HttpClient

    @BeforeTest
    fun setup() {
        // Start daemon with REST API only
        daemon = UTLXDaemon(
            lspTransportType = TransportType.SOCKET,
            lspPort = 7777,
            enableRestApi = true,
            restApiPort = 7778
        )
        daemon.start()

        // Give server time to start
        Thread.sleep(1000)

        httpClient = HttpClient(CIO)
    }

    @AfterTest
    fun teardown() {
        httpClient.close()
        daemon.stop()
    }

    @Test
    fun `full validation flow`() = runBlocking {
        val response = httpClient.post("http://localhost:7778/api/validate") {
            contentType(ContentType.Application.Json)
            setBody("""
                {
                  "transformation": "%utlx 1.0\ninput json\noutput json\n---\n{}"
                }
            """.trimIndent())
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.bodyAsText()
        assertContains(body, "\"valid\":true")
    }

    @Test
    fun `full execution flow`() = runBlocking {
        val response = httpClient.post("http://localhost:7778/api/execute") {
            contentType(ContentType.Application.Json)
            setBody("""
                {
                  "transformation": "%utlx 1.0\ninput json\noutput json\n---\n{ \"doubled\": ${'$'}input.value * 2 }",
                  "input": "{\"value\": 10}",
                  "inputFormat": "json"
                }
            """.trimIndent())
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.bodyAsText()
        assertContains(body, "\"success\":true")
        assertContains(body, "20")
    }
}
```

**Run integration tests:**
```bash
./gradlew :modules:daemon:integrationTest
```

---

### Level 3: Manual Testing (Developer Testing)

**Test with curl commands**

```bash
# 1. Start daemon
java -jar modules/daemon/build/libs/daemon-1.0.0-SNAPSHOT.jar \
  --lsp-transport=socket \
  --rest-api-port=7778 \
  --verbose

# 2. Health check
curl http://localhost:7778/api/health

# Expected:
# {
#   "status": "UP",
#   "version": "1.0.0",
#   "uptime": 12345
# }

# 3. Validate transformation
curl -X POST http://localhost:7778/api/validate \
  -H "Content-Type: application/json" \
  -d '{
    "transformation": "%utlx 1.0\ninput json\noutput json\n---\n{\"message\": \"hello\"}"
  }'

# Expected:
# {
#   "valid": true,
#   "diagnostics": []
# }

# 4. Execute transformation
curl -X POST http://localhost:7778/api/execute \
  -H "Content-Type: application/json" \
  -d '{
    "transformation": "%utlx 1.0\ninput json\noutput json\n---\n{ \"doubled\": $input.value * 2 }",
    "input": "{\"value\": 21}",
    "inputFormat": "json"
  }'

# Expected:
# {
#   "success": true,
#   "output": "{\"doubled\":42}"
# }

# 5. Get stdlib functions
curl "http://localhost:7778/api/stdlib?category=string&search=upper"

# Expected:
# {
#   "functions": [
#     {
#       "name": "upper",
#       "category": "string",
#       "description": "Convert string to uppercase",
#       ...
#     }
#   ],
#   "count": 1
# }

# 6. Parse XSD schema
curl -X POST http://localhost:7778/api/parse-schema \
  -H "Content-Type: application/json" \
  -d '{
    "schema": "<?xml version=\"1.0\"?><xs:schema xmlns:xs=\"http://www.w3.org/2001/XMLSchema\"><xs:element name=\"Order\" type=\"xs:string\"/></xs:schema>",
    "format": "xsd"
  }'

# Expected:
# {
#   "success": true,
#   "parsed": "...",
#   "elements": ["Order"]
# }
```

---

### Level 4: MCP Integration Testing

**Test from MCP server perspective**

**File:** `mcp-server/src/test/daemon-client.test.ts`

```typescript
import axios from 'axios';

describe('Daemon REST API - MCP Integration', () => {
  const DAEMON_URL = 'http://localhost:7778';

  beforeAll(async () => {
    // Ensure daemon is running
    const response = await axios.get(`${DAEMON_URL}/api/health`);
    expect(response.data.status).toBe('UP');
  });

  test('validate_utlx tool works', async () => {
    const response = await axios.post(`${DAEMON_URL}/api/validate`, {
      transformation: '%utlx 1.0\ninput json\noutput json\n---\n{}'
    });

    expect(response.status).toBe(200);
    expect(response.data.valid).toBe(true);
  });

  test('execute_transformation tool works', async () => {
    const response = await axios.post(`${DAEMON_URL}/api/execute`, {
      transformation: '%utlx 1.0\ninput json\noutput json\n---\n{ "result": $input.x + $input.y }',
      input: '{"x": 10, "y": 32}',
      inputFormat: 'json'
    });

    expect(response.status).toBe(200);
    expect(response.data.success).toBe(true);
    expect(response.data.output).toContain('42');
  });

  test('get_stdlib_functions tool works', async () => {
    const response = await axios.get(`${DAEMON_URL}/api/stdlib`, {
      params: { category: 'array' }
    });

    expect(response.status).toBe(200);
    expect(response.data.count).toBeGreaterThan(0);
    expect(response.data.functions[0].category).toBe('array');
  });
});
```

**Run MCP integration tests:**
```bash
cd mcp-server
npm test -- daemon-client.test.ts
```

---

## Daemon Startup - Dual Server Mode

### Yes, Both LSP and REST API Start Together

**Startup Sequence:**

```
┌─────────────────────────────────────────────────────┐
│  java -jar utlx-daemon.jar --verbose                │
└──────────────────┬──────────────────────────────────┘
                   │
                   ▼
         ┌─────────────────────┐
         │  UTLXDaemon.start() │
         └──────────┬──────────┘
                    │
         ┌──────────┴──────────┐
         │                     │
         ▼                     ▼
   ┌─────────────┐     ┌──────────────┐
   │ Start LSP   │     │ Start REST   │
   │ Server      │     │ API Server   │
   └─────────────┘     └──────────────┘
         │                     │
         │   (both running     │
         │    concurrently)    │
         │                     │
         ▼                     ▼
   ┌─────────────┐     ┌──────────────┐
   │ LSP Thread  │     │ Ktor Thread  │
   │ (blocking)  │     │ Pool (async) │
   └─────────────┘     └──────────────┘
         │                     │
         └──────────┬──────────┘
                    │
         ┌──────────▼──────────┐
         │   Shared Services   │
         │  • Parser           │
         │  • Validator        │
         │  • Executor         │
         │  • Type Checker     │
         │  • State Manager    │
         └─────────────────────┘
```

### Configuration Options

**Option 1: Default (both enabled)**
```bash
java -jar utlx-daemon.jar
# LSP: STDIO
# REST API: port 7778
```

**Option 2: LSP only (Theia without MCP)**
```bash
java -jar utlx-daemon.jar --rest-api=false
# LSP: STDIO
# REST API: disabled
```

**Option 3: Socket LSP + REST API (development)**
```bash
java -jar utlx-daemon.jar \
  --lsp-transport=socket \
  --lsp-port=7777 \
  --rest-api-port=7778 \
  --verbose
# LSP: Socket port 7777
# REST API: port 7778
```

**Option 4: Custom ports (production)**
```bash
java -jar utlx-daemon.jar \
  --lsp-transport=socket \
  --lsp-port=9001 \
  --rest-api-port=9002
# LSP: Socket port 9001
# REST API: port 9002
```

### Resource Usage

**Memory:**
- LSP server: ~50MB (state manager, AST cache)
- REST API server: ~30MB (Ktor, JSON serialization)
- Shared services: ~100MB (parser, executor, schemas)
- **Total: ~180MB** (acceptable for JVM daemon)

**CPU:**
- LSP idle: 0-1% (event-driven)
- REST API idle: 0-1% (Ktor async)
- Peak (both active): 20-30% (parsing, type checking)

**Threads:**
- LSP: 1-2 threads (blocking I/O)
- REST API: 4-8 threads (Ktor coroutine pool)
- Total: ~10 threads (normal for JVM application)

---

## Implementation Order

### Question: Expand UTL-X → Create MCP → Implement Theia?

**Answer: YES**, with one adjustment:

### Recommended Order

```
Phase 1: Daemon REST API (Week 1-2)
   ↓
Phase 2: MCP Server Core (Week 3-4)
   ↓
Phase 3: MCP-LLM Integration (Week 5-6)
   ↓
Phase 4: Theia IDE (Week 7-9)
   ↓
Phase 5: Integration & Polish (Week 10-12)
```

### Detailed Breakdown

#### Phase 1: Expand UTL-X Daemon (Week 1-2)

**Goal:** Add REST API to daemon

**Tasks:**
1. ✅ Add Ktor dependencies
2. ✅ Implement RestApiServer.kt
3. ✅ Update DaemonServer.kt for dual mode
4. ✅ Add command-line arguments
5. ✅ Write unit tests
6. ✅ Write integration tests
7. ✅ Test with curl
8. ✅ Document API (OpenAPI spec)

**Deliverable:** Daemon with working REST API

**Validation:**
```bash
# Can validate, execute, infer schema via curl
curl -X POST http://localhost:7778/api/validate -d '{...}'
```

---

#### Phase 2: MCP Server Core (Week 3-4)

**Goal:** Implement MCP tools using daemon REST API

**Tasks:**
1. ✅ Set up TypeScript project (mcp-server/)
2. ✅ Implement Tool 1: get_input_schema
3. ✅ Implement Tool 2: get_stdlib_functions
4. ✅ Implement Tool 3: validate_utlx
5. ✅ Implement Tool 4: infer_output_schema
6. ✅ Implement Tool 5: execute_transformation
7. ✅ Implement Tool 6: get_examples
8. ✅ Write tests for each tool
9. ✅ Create CLI test client

**Deliverable:** MCP server with 6 working tools (no LLM yet)

**Validation:**
```bash
# Can use MCP tools via CLI
node cli-test-client.js validate --file test.utlx
```

---

#### Phase 3: MCP-LLM Integration (Week 5-6)

**Goal:** Connect MCP to Claude/GPT-4

**Tasks:**
1. ✅ Implement LLM clients (Anthropic, OpenAI)
2. ✅ Create generation pipeline
3. ✅ Implement prompt templates
4. ✅ Add validation feedback loop
5. ✅ Test with real AI generation
6. ✅ Optimize prompts

**Deliverable:** MCP server that can generate UTLX via AI

**Validation:**
```bash
# Can generate UTLX from natural language
node cli-test-client.js generate \
  --prompt "Convert XML orders to JSON invoices" \
  --input-schema order.xsd
```

---

#### Phase 4: Theia IDE (Week 7-9)

**Goal:** Build Theia extension with LSP + MCP

**Tasks:**
1. ✅ Create Theia extension project
2. ✅ Implement 3-panel layout
3. ✅ Connect Monaco to LSP (STDIO)
4. ✅ Create AI Assistant panel
5. ✅ Connect AI panel to MCP server
6. ✅ Implement design-time mode
7. ✅ Implement runtime mode
8. ✅ Add schema loading/validation

**Deliverable:** Working Theia IDE with AI assistance

**Validation:**
```bash
# Can open Theia, edit UTLX, get AI suggestions
npm run start:theia
```

---

#### Phase 5: Integration & Polish (Week 10-12)

**Goal:** End-to-end workflows, performance, UX

**Tasks:**
1. ✅ End-to-end testing
2. ✅ Performance optimization
3. ✅ Error handling improvements
4. ✅ Documentation
5. ✅ Deployment guides
6. ✅ User training materials

**Deliverable:** Production-ready system

---

### Why This Order?

**1. Daemon First (Bottom-Up)**
- Foundation for everything else
- Can test in isolation
- No dependencies on other components

**2. MCP Before Theia**
- MCP tools are standalone (can test with CLI)
- Validates daemon API design early
- Faster iteration (no UI complexity)

**3. LLM After Core MCP**
- MCP tools work without LLM (standalone mode)
- Can validate tool implementations first
- LLM is expensive to test (API costs)

**4. Theia Last (Top-Down)**
- Most complex component
- Depends on both daemon and MCP
- UI iteration is slow (build, restart, test)
- Benefits from stable backend

---

## Design Impact on UTL-X

### What Changes in Core UTL-X?

**Answer: Very Little** (by design)

### Impact Analysis

#### ✅ No Changes Required

1. **Core Language** - No changes
   - Parser remains unchanged
   - AST structure unchanged
   - Runtime unchanged

2. **Standard Library** - No changes
   - Function implementations unchanged
   - Function registry format unchanged

3. **CLI** - No changes
   - `./utlx transform` works as before
   - All existing commands work

4. **Conformance Suite** - No changes
   - Tests run as before
   - No modifications needed

#### ⚠️ Additive Changes Only

1. **Daemon Module** - Add REST API
   - **Location:** `modules/daemon/`
   - **Impact:** Additive (new files, new dependencies)
   - **Risk:** Low (optional feature, can be disabled)

2. **Build Configuration** - Add Ktor dependency
   - **Location:** `modules/daemon/build.gradle.kts`
   - **Impact:** Minimal (one dependency block)
   - **Risk:** Low (Ktor is stable, well-maintained)

3. **Command-Line Arguments** - Add REST API flags
   - **Location:** `modules/daemon/src/main/kotlin/Main.kt`
   - **Impact:** Minimal (backward compatible)
   - **Risk:** None (defaults preserve current behavior)

### Architectural Principles

**1. Separation of Concerns**
```
LSP Layer (editors)         ← Existing
REST API Layer (tools)      ← New
───────────────────────────
Core Services (shared)      ← Unchanged
```

**2. No Breaking Changes**
- Existing LSP functionality unchanged
- Existing CLI unchanged
- Existing tests pass without modification

**3. Opt-In Features**
- REST API can be disabled (`--rest-api=false`)
- Backward compatible with current deployments

**4. Single Responsibility**
- Each server (LSP, REST) has one job
- Shared services handle business logic
- No duplication

### Risk Assessment

| Risk | Likelihood | Impact | Mitigation |
|------|-----------|---------|------------|
| REST API breaks LSP | Low | High | Separate transport threads, independent handlers |
| Performance degradation | Low | Medium | Resource monitoring, load testing |
| Security vulnerabilities | Medium | High | Input validation, rate limiting, CORS |
| Memory leaks | Low | Medium | Profiling, automated tests |
| Dependency conflicts | Low | Low | Ktor is Kotlin-native, minimal deps |

### Testing Impact

**New Tests Required:**
- REST API unit tests (~20 tests)
- REST API integration tests (~10 tests)
- MCP integration tests (~15 tests)

**Existing Tests:**
- All existing tests continue to pass
- No modifications required
- Test coverage improves

### Deployment Impact

**Development:**
```bash
# Before (LSP only)
java -jar utlx-daemon.jar

# After (LSP + REST API)
java -jar utlx-daemon.jar  # same command, more features
```

**Production:**
```bash
# Can still disable REST API if not needed
java -jar utlx-daemon.jar --rest-api=false
```

**No breaking changes to existing deployments.**

---

## Summary

### What Needs to Be Done

1. ✅ Add Ktor dependency (30 minutes)
2. ✅ Implement RestApiServer.kt (4-6 hours)
3. ✅ Update DaemonServer.kt (2 hours)
4. ✅ Add CLI arguments (1 hour)
5. ✅ Write tests (4-6 hours)
6. ✅ Document API (2 hours)

**Total: 2-3 days of focused work**

### Technology Choice

**REST API (Ktor)** over gRPC

**Reasons:**
- MCP compatibility (JSON-RPC over HTTP)
- Developer experience (curl, Postman)
- Performance difference negligible (3ms vs use case needs)
- Faster implementation (2-3 days vs 4-5 days)
- Lower maintenance burden

### Testing

**4-level strategy:**
1. Unit tests (Ktor test framework)
2. Integration tests (real HTTP requests)
3. Manual testing (curl commands)
4. MCP integration tests (from MCP server)

### Dual Server Mode

**YES**, daemon runs both:
- LSP server (port 7777 or STDIO)
- REST API server (port 7778)
- Shared core services
- Can disable REST API if not needed

### Implementation Order

**Recommended:**
1. **Daemon REST API** (Week 1-2) ← You are here
2. **MCP Server Core** (Week 3-4)
3. **MCP-LLM Integration** (Week 5-6)
4. **Theia IDE** (Week 7-9)
5. **Integration & Polish** (Week 10-12)

### Impact on UTL-X

**Minimal:**
- ✅ No changes to core language
- ✅ No breaking changes
- ✅ Additive only (new REST API)
- ✅ Backward compatible
- ✅ Optional feature (can be disabled)

**Risk: Low** | **Value: High** | **Recommendation: Proceed**
