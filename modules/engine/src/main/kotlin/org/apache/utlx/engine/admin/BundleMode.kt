package org.apache.utlx.engine.admin

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.kotlinModule
import com.fasterxml.jackson.module.kotlin.readValue
import org.apache.utlx.engine.UtlxEngine
import org.apache.utlx.engine.config.TransformConfig
import org.apache.utlx.engine.proto.LoadTransformationRequest
import org.apache.utlx.engine.registry.TransformationInstance
import org.apache.utlx.engine.transport.TransportHandlers
import org.slf4j.LoggerFactory
import java.io.ByteArrayInputStream
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.time.Instant
import java.util.zip.ZipInputStream

private val logger = LoggerFactory.getLogger("BundleMode")

/**
 * EF09: Production Bundle Mode.
 *
 * Detects whether a .utlar file exists on the data volume.
 * If present → locked mode (Admin API read-only except operational endpoints).
 * If absent → open mode (full Admin API access).
 */
data class BundleInfo(
    val mode: String,               // "open" or "locked"
    val bundleVersion: String? = null,
    val bundleChecksum: String? = null,
    val bundleCreated: String? = null,
    val utlarPath: Path? = null
)

/**
 * Scan the data directory for a .utlar file.
 * Returns BundleInfo with mode and manifest data.
 */
fun detectBundleMode(dataDirPath: Path?): BundleInfo {
    if (dataDirPath == null) return BundleInfo(mode = "open")

    val utlarFile = dataDirPath.resolve("bundle.utlar")
    if (!Files.exists(utlarFile)) {
        logger.info("No bundle.utlar found in {} — open mode", dataDirPath)
        return BundleInfo(mode = "open")
    }

    logger.info("Found bundle.utlar in {} — locked mode", dataDirPath)

    // Read manifest from the .utlar ZIP
    try {
        val zipBytes = Files.readAllBytes(utlarFile)
        val checksum = "sha256:" + sha256Hex(zipBytes)
        val manifest = readManifestFromUtlar(zipBytes)

        return BundleInfo(
            mode = "locked",
            bundleVersion = manifest?.get("version")?.toString(),
            bundleChecksum = checksum,
            bundleCreated = manifest?.get("created")?.toString(),
            utlarPath = utlarFile
        )
    } catch (e: Exception) {
        logger.warn("Failed to read manifest from bundle.utlar: {}", e.message)
        return BundleInfo(
            mode = "locked",
            bundleChecksum = null,
            utlarPath = utlarFile
        )
    }
}

/**
 * Load all transformations from a .utlar file into the engine.
 * Returns the number of transformations loaded.
 */
fun loadUtlar(utlarPath: Path, engine: UtlxEngine): Int {
    val zipBytes = Files.readAllBytes(utlarPath)
    val jsonMapper = ObjectMapper().apply {
        registerModule(kotlinModule())
        configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
    }
    val yamlMapper = ObjectMapper(YAMLFactory()).apply {
        registerModule(kotlinModule())
        configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
    }

    // Two-pass: first collect configs and schemas, then load .utlx sources
    val configMap = mutableMapOf<String, TransformConfig>()
    val sourceMap = mutableMapOf<String, String>()
    val schemas = mutableMapOf<String, ByteArray>()

    ZipInputStream(ByteArrayInputStream(zipBytes)).use { zis ->
        var entry = zis.nextEntry
        while (entry != null) {
            val path = entry.name
            when {
                !entry.isDirectory && path.startsWith("transformations/") && path.endsWith(".utlx") -> {
                    val parts = path.removePrefix("transformations/").split("/")
                    if (parts.size == 2) {
                        sourceMap[parts[0]] = zis.readBytes().toString(Charsets.UTF_8)
                    }
                }
                !entry.isDirectory && path.startsWith("transformations/") && path.endsWith("transform.yaml") -> {
                    val parts = path.removePrefix("transformations/").split("/")
                    if (parts.size == 2) {
                        try {
                            configMap[parts[0]] = yamlMapper.readValue(zis.readBytes())
                        } catch (e: Exception) {
                            logger.warn("Failed to parse transform.yaml for '{}': {}", parts[0], e.message)
                        }
                    }
                }
                !entry.isDirectory && path.startsWith("schemas/") -> {
                    val filename = path.removePrefix("schemas/")
                    schemas[filename] = zis.readBytes()
                }
            }
            zis.closeEntry()
            entry = zis.nextEntry
        }
    }

    // Load schemas into SchemaStore (if engine has one via data dir)
    // Schemas are available for validator resolution during transformation loading

    logger.info("Bundle contents: {} transformation(s), {} schema(s), {} config(s)",
        sourceMap.size, schemas.size, configMap.size)
    if (logger.isDebugEnabled) {
        sourceMap.keys.forEach { logger.debug("  .utlx: {}", it) }
        schemas.keys.forEach { logger.debug("  schema: {}", it) }
        configMap.keys.forEach { logger.debug("  transform.yaml: {}", it) }
    }

    // Load transformations
    var loaded = 0
    for ((name, source) in sourceMap) {
        try {
            val config = configMap[name] ?: TransformConfig()
            val strategy = config.strategy.ifEmpty { "COMPILED" }

            val loadReq = LoadTransformationRequest.newBuilder()
                .setTransformationId(name)
                .setUtlxSource(source)
                .setStrategy(strategy)
                .build()
            val loadResp = TransportHandlers.handleLoadTransformation(loadReq, engine)

            if (loadResp.success) {
                // Apply transform config (including messaging) to the registered instance
                val instance = engine.registry.get(name)
                if (instance != null && config != TransformConfig()) {
                    val updated = TransformationInstance(
                        name = instance.name, source = instance.source,
                        strategy = instance.strategy, config = config,
                        inputValidator = instance.inputValidator,
                        inputValidators = instance.inputValidators,
                        outputValidator = instance.outputValidator
                    )
                    engine.registry.register(name, updated)
                }
                loaded++
                logger.info("Loaded '{}' from .utlar [strategy={}]", name, strategy)
            } else {
                logger.error("Failed to load '{}' from .utlar: {}", name, loadResp.error)
            }
        } catch (e: Exception) {
            logger.error("Failed to load '{}' from .utlar: {}", name, e.message)
        }
    }

    logger.info("Loaded {} transformation(s) and {} schema(s) from .utlar", loaded, schemas.size)
    return loaded
}

private fun readManifestFromUtlar(zipBytes: ByteArray): Map<String, Any>? {
    val mapper = ObjectMapper().apply {
        registerModule(kotlinModule())
        configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
    }
    ZipInputStream(ByteArrayInputStream(zipBytes)).use { zis ->
        var entry = zis.nextEntry
        while (entry != null) {
            if (entry.name == "manifest.json") {
                return mapper.readValue(zis.readBytes())
            }
            zis.closeEntry()
            entry = zis.nextEntry
        }
    }
    return null
}

private fun sha256Hex(bytes: ByteArray): String {
    val digest = MessageDigest.getInstance("SHA-256")
    return digest.digest(bytes).joinToString("") { "%02x".format(it) }
}
