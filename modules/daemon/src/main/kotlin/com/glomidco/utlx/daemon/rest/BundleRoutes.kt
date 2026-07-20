package com.glomidco.utlx.daemon.rest

import com.glomidco.utlx.bundle.BundleStore
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import org.slf4j.LoggerFactory
import java.io.File

/**
 * IF19: utlxd's stateful Bundle Management REST surface — CRUD over the workspace bundle,
 * backed by the shared [BundleStore] (modules/bundle). Shaped to mirror EF03's
 * `/admin/transformations` + `schemas` so the later "EF03 onto the shared layer" refactor is clean.
 *
 * Availability is opt-in: endpoints are live only when a workspace root is configured
 * (UTLX_WORKSPACE env or -Dutlx.workspace); otherwise they answer 503. This keeps utlxd from
 * mutating whatever directory it happens to be launched in.
 */

private val bundleLog = LoggerFactory.getLogger("com.glomidco.utlx.daemon.rest.BundleRoutes")

object BundleWorkspace {
    /** UTLX_WORKSPACE env → -Dutlx.workspace → null. Never defaults to cwd (explicit opt-in). */
    fun resolve(): File? {
        val path = System.getenv("UTLX_WORKSPACE") ?: System.getProperty("utlx.workspace") ?: return null
        val dir = File(path)
        return if (dir.isDirectory) dir else null
    }
}

@Serializable
data class BundleInfoDto(val name: String, val transformationCount: Int, val schemaCount: Int, val hasEngineConfig: Boolean)

@Serializable
data class TransformationSummaryDto(val name: String, val hasSource: Boolean, val hasConfig: Boolean, val testInputs: List<String>)

@Serializable
data class TransformationListDto(val transformations: List<TransformationSummaryDto>)

@Serializable
data class TransformationDto(val name: String, val source: String?, val config: String?, val testInputs: List<String>)

@Serializable
data class SchemaSummaryDto(val name: String, val sizeBytes: Long)

@Serializable
data class SchemaListDto(val schemas: List<SchemaSummaryDto>)

@Serializable
data class SchemaContentDto(val name: String, val content: String)

@Serializable
data class EngineConfigDto(val content: String)

@Serializable
data class PutTransformationRequest(val source: String, val config: String? = null)

@Serializable
data class PutSchemaRequest(val content: String)

@Serializable
data class DeletedDto(val deleted: Boolean)

@Serializable
data class BundleErrorDto(val error: String)

/**
 * Run [block] with the store, mapping the common failures to HTTP: no workspace → 503,
 * bad/unsafe name → 400 (BundleStore throws IllegalArgumentException), else → 500.
 */
private suspend fun ApplicationCall.withBundleStore(store: BundleStore?, block: suspend (BundleStore) -> Unit) {
    if (store == null) {
        respond(HttpStatusCode.ServiceUnavailable,
            BundleErrorDto("Bundle API unavailable: no workspace configured (set UTLX_WORKSPACE)"))
        return
    }
    try {
        block(store)
    } catch (e: BadRequestException) {
        // Ktor wraps a failed request-body deserialization (bad JSON / missing required field) here.
        respond(HttpStatusCode.BadRequest, BundleErrorDto(e.message ?: "malformed request body"))
    } catch (e: io.ktor.serialization.ContentConvertException) {
        respond(HttpStatusCode.BadRequest, BundleErrorDto("malformed or missing request body"))
    } catch (e: IllegalArgumentException) {
        respond(HttpStatusCode.BadRequest, BundleErrorDto(e.message ?: "invalid request"))
    } catch (e: Exception) {
        bundleLog.error("bundle route error", e)
        respond(HttpStatusCode.InternalServerError, BundleErrorDto(e.message ?: "internal error"))
    }
}

fun Route.registerBundleRoutes(store: BundleStore?) {
    route("/api/bundle") {

        get {
            call.withBundleStore(store) { s ->
                val i = s.info()
                call.respond(BundleInfoDto(i.name, i.transformationCount, i.schemaCount, i.hasEngineConfig))
            }
        }

        route("/transformations") {
            get {
                call.withBundleStore(store) { s ->
                    call.respond(TransformationListDto(s.listTransformations().map {
                        TransformationSummaryDto(it.name, it.hasSource, it.hasConfig, it.testInputs)
                    }))
                }
            }
            get("/{name}") {
                call.withBundleStore(store) { s ->
                    val name = call.parameters["name"]!!
                    val tx = s.getTransformation(name)
                    if (tx == null) call.respond(HttpStatusCode.NotFound, BundleErrorDto("transformation not found: $name"))
                    else call.respond(TransformationDto(tx.name, tx.source, tx.config, tx.testInputs))
                }
            }
            put("/{name}") {
                call.withBundleStore(store) { s ->
                    val name = call.parameters["name"]!!
                    val body = call.receive<PutTransformationRequest>()
                    s.putTransformation(name, body.source, body.config)
                    call.respond(HttpStatusCode.OK, TransformationSummaryDto(name, true, body.config != null, emptyList()))
                }
            }
            delete("/{name}") {
                call.withBundleStore(store) { s ->
                    val name = call.parameters["name"]!!
                    if (s.deleteTransformation(name)) call.respond(DeletedDto(true))
                    else call.respond(HttpStatusCode.NotFound, BundleErrorDto("transformation not found: $name"))
                }
            }
        }

        route("/schemas") {
            get {
                call.withBundleStore(store) { s ->
                    call.respond(SchemaListDto(s.listSchemas().map { SchemaSummaryDto(it.name, it.sizeBytes) }))
                }
            }
            get("/{name}") {
                call.withBundleStore(store) { s ->
                    val name = call.parameters["name"]!!
                    val content = s.getSchema(name)
                    if (content == null) call.respond(HttpStatusCode.NotFound, BundleErrorDto("schema not found: $name"))
                    else call.respond(SchemaContentDto(name, content))
                }
            }
            put("/{name}") {
                call.withBundleStore(store) { s ->
                    val name = call.parameters["name"]!!
                    val body = call.receive<PutSchemaRequest>()
                    s.putSchema(name, body.content)
                    call.respond(HttpStatusCode.OK, SchemaSummaryDto(name, body.content.length.toLong()))
                }
            }
            delete("/{name}") {
                call.withBundleStore(store) { s ->
                    val name = call.parameters["name"]!!
                    if (s.deleteSchema(name)) call.respond(DeletedDto(true))
                    else call.respond(HttpStatusCode.NotFound, BundleErrorDto("schema not found: $name"))
                }
            }
        }

        get("/engine-config") {
            call.withBundleStore(store) { s ->
                val c = s.getEngineConfig()
                if (c == null) call.respond(HttpStatusCode.NotFound, BundleErrorDto("no engine.yaml"))
                else call.respond(EngineConfigDto(c))
            }
        }
        put("/engine-config") {
            call.withBundleStore(store) { s ->
                val body = call.receive<EngineConfigDto>()
                s.putEngineConfig(body.content)
                call.respond(HttpStatusCode.OK, EngineConfigDto(body.content))
            }
        }
    }
}
