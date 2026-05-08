package org.apache.utlx.engine.admin

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.kotlinModule
import org.apache.utlx.engine.config.MessagingEndpoint
import org.slf4j.LoggerFactory
import java.net.HttpURLConnection
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

/**
 * EF10: Dapr integration — detection, component YAML generation, sync.
 *
 * Three modes:
 * - HTTP-only: no Dapr sidecar detected, messaging config stored but not acted on
 * - Static:    Dapr sidecar present, but no --dapr-components-dir → validates only
 * - Dynamic:   Dapr sidecar present + --dapr-components-dir → generates/deletes YAML
 */
class DaprIntegration(
    val componentsDir: Path? = null,
    val servicebusNamespace: String? = null,
    val eventhubNamespace: String? = null,
    val storageAccount: String? = null,
    private val daprPort: Int = 3500
) {
    private val logger = LoggerFactory.getLogger(DaprIntegration::class.java)
    private val mapper = ObjectMapper().apply {
        registerModule(kotlinModule())
        configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
    }

    // Sync status per transformation
    private val syncStatus = ConcurrentHashMap<String, SyncState>()

    // Cached Dapr metadata
    @Volatile var sidecarReachable: Boolean = false
        private set
    @Volatile var sidecarVersion: String? = null
        private set
    @Volatile var loadedComponents: List<DaprComponent> = emptyList()
        private set

    val mode: String
        get() = when {
            !sidecarReachable && componentsDir == null -> "http-only"
            sidecarReachable && componentsDir == null -> "static"
            componentsDir != null -> "dynamic"  // dynamic even if sidecar not yet up (it will be)
            else -> "http-only"
        }

    /**
     * Probe the Dapr sidecar metadata endpoint.
     * Call on startup and periodically.
     */
    fun probeSidecar() {
        try {
            val url = URI("http://localhost:$daprPort/v1.0/metadata").toURL()
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 2000
            conn.readTimeout = 2000
            conn.requestMethod = "GET"

            if (conn.responseCode == 200) {
                val body = conn.inputStream.bufferedReader().readText()
                val tree = mapper.readTree(body)
                sidecarReachable = true
                sidecarVersion = tree.get("runtimeVersion")?.asText()

                // Parse loaded components
                val components = mutableListOf<DaprComponent>()
                tree.get("registeredComponents")?.forEach { comp ->
                    components.add(DaprComponent(
                        name = comp.get("name")?.asText() ?: "",
                        type = comp.get("type")?.asText() ?: "",
                        version = comp.get("version")?.asText() ?: ""
                    ))
                }
                loadedComponents = components
                logger.info("Dapr sidecar detected: v{}, {} component(s) loaded", sidecarVersion, components.size)
                if (logger.isDebugEnabled) {
                    components.forEach { c ->
                        logger.debug("  Dapr component: name={} type={} version={}", c.name, c.type, c.version)
                    }
                }
            } else {
                sidecarReachable = false
                logger.info("Dapr sidecar probe returned {} — sidecar not ready", conn.responseCode)
            }
            conn.disconnect()
        } catch (e: Exception) {
            sidecarReachable = false
            logger.info("Dapr sidecar not reachable at localhost:{} — HTTP-only mode ({})", daprPort, e.message)
        }
    }

    /**
     * EF10: Startup reconciliation — auto-sync all persisted messaging configs.
     * For each loaded transformation with messaging config, sync to Dapr.
     * Also cleans up orphaned managed components in the components directory.
     */
    fun reconcileOnStartup(registry: org.apache.utlx.engine.registry.TransformationRegistry) {
        val transformations = registry.list()
        var synced = 0
        var orphansRemoved = 0

        // Sync each transformation that has messaging config
        for (tx in transformations) {
            val input = tx.config.input
            val output = tx.config.outputMessaging
            if (input != null || output != null) {
                val result = sync(tx.name, input, output)
                if (result.success) synced++
                logger.info("Reconcile '{}': {} ({})", tx.name,
                    if (result.success) "synced" else "failed", result.message)
            } else {
                syncStatus[tx.name] = SyncState(status = "no_dapr")
            }
        }

        // Clean up orphaned managed components (transformation deleted while engine was down)
        if (componentsDir != null && java.nio.file.Files.exists(componentsDir)) {
            val loadedNames = transformations.map { it.name }.toSet()
            try {
                java.nio.file.Files.list(componentsDir).use { stream ->
                    stream.filter { it.fileName.toString().endsWith(".yaml") }
                        .forEach { file ->
                            try {
                                val content = java.nio.file.Files.readString(file)
                                if (content.contains("utlxe.io/managed: \"true\"")) {
                                    // Extract transformation name from annotation
                                    val match = Regex("utlxe.io/transformation: \"([^\"]+)\"").find(content)
                                    val txName = match?.groupValues?.get(1)
                                    if (txName != null && txName !in loadedNames) {
                                        java.nio.file.Files.delete(file)
                                        orphansRemoved++
                                        logger.info("Reconcile: removed orphaned component YAML '{}' (transformation '{}' no longer exists)", file.fileName, txName)
                                    }
                                }
                            } catch (e: Exception) {
                                logger.warn("Reconcile: failed to check {}: {}", file, e.message)
                            }
                        }
                }
            } catch (e: Exception) {
                logger.warn("Reconcile: failed to scan components dir: {}", e.message)
            }
        }

        logger.info("Startup reconciliation complete: {} synced, {} orphans removed", synced, orphansRemoved)
    }

    /**
     * Get the sync state for a transformation.
     */
    fun getSyncState(transformationName: String): SyncState {
        return syncStatus.getOrDefault(transformationName, SyncState(status = "no_dapr"))
    }

    /**
     * Mark a transformation as draft (messaging config changed, not yet synced).
     */
    fun markDraft(transformationName: String, changes: List<String>) {
        syncStatus[transformationName] = SyncState(
            status = "draft",
            pendingChanges = changes
        )
    }

    /**
     * Mark a transformation as having no messaging config.
     */
    fun markNoDapr(transformationName: String) {
        syncStatus[transformationName] = SyncState(status = "no_dapr")
    }

    /**
     * Remove sync tracking for a deleted transformation.
     */
    fun removeSyncState(transformationName: String) {
        syncStatus.remove(transformationName)
    }

    /**
     * Get all sync states.
     */
    fun allSyncStates(): Map<String, SyncState> = syncStatus.toMap()

    /**
     * Sync a single transformation's messaging config to Dapr.
     * Returns sync actions taken.
     */
    fun sync(
        transformationName: String,
        inputEndpoint: MessagingEndpoint?,
        outputEndpoint: MessagingEndpoint?
    ): SyncResult {
        val actions = mutableListOf<SyncAction>()
        val warnings = mutableListOf<String>()

        try {
            when (mode) {
                "http-only" -> {
                    // No Dapr — just mark as synced (config is on disk)
                    logger.debug("Sync '{}': HTTP-only mode — no Dapr components to manage", transformationName)
                    syncStatus[transformationName] = SyncState(
                        status = if (inputEndpoint != null || outputEndpoint != null) "synced" else "no_dapr",
                        lastSynced = Instant.now()
                    )
                    return SyncResult(success = true, actions = actions,
                        message = "HTTP-only mode — config saved, no Dapr components to manage.")
                }

                "static" -> {
                    // Validate that Dapr has the expected components
                    logger.debug("Sync '{}': static mode — validating against Dapr", transformationName)
                    probeSidecar()
                    inputEndpoint?.let { ep ->
                        val componentName = ep.resourceName ?: return@let
                        val found = loadedComponents.any { it.name == componentName }
                        if (found) {
                            actions.add(SyncAction("validated", componentName, ep.daprComponentType ?: ""))
                        } else {
                            warnings.add("Dapr component '$componentName' not found — create binding YAML manually")
                        }
                    }
                    outputEndpoint?.let { ep ->
                        val componentName = if (ep.isPubSub) "utlxe-servicebus" else ep.resourceName ?: return@let
                        val found = loadedComponents.any { it.name == componentName }
                        if (found) {
                            actions.add(SyncAction("validated", componentName, ep.daprComponentType ?: ""))
                        } else {
                            warnings.add("Dapr component '$componentName' not found — create binding YAML manually")
                        }
                    }
                    syncStatus[transformationName] = SyncState(
                        status = "synced",
                        lastSynced = Instant.now()
                    )
                    val msg = if (warnings.isEmpty()) "Config validated against Dapr."
                    else "Config validated. ${warnings.size} component(s) missing — manual action required."
                    return SyncResult(success = true, actions = actions, warnings = warnings, message = msg)
                }

                "dynamic" -> {
                    // Generate/update/delete Dapr component YAML
                    logger.debug("Sync '{}': dynamic mode — generating Dapr YAML in {}", transformationName, componentsDir)
                    val dir = componentsDir!!
                    Files.createDirectories(dir)

                    // Handle input endpoint
                    inputEndpoint?.let { ep ->
                        val action = generateComponentYaml(dir, transformationName, "input", ep)
                        if (action != null) actions.add(action)
                    }

                    // Handle output endpoint
                    outputEndpoint?.let { ep ->
                        val action = generateComponentYaml(dir, transformationName, "output", ep)
                        if (action != null) actions.add(action)
                    }

                    // If both are null, remove any existing managed components
                    if (inputEndpoint == null && outputEndpoint == null) {
                        removeComponentYaml(dir, transformationName)
                        syncStatus[transformationName] = SyncState(status = "no_dapr", lastSynced = Instant.now())
                        return SyncResult(success = true, actions = actions,
                            message = "Messaging removed. Dapr components cleaned up.")
                    }

                    syncStatus[transformationName] = SyncState(
                        status = "synced",
                        lastSynced = Instant.now()
                    )
                    return SyncResult(
                        success = true,
                        actions = actions,
                        message = "${actions.size} Dapr component(s) synced. Bindings will activate within ~1 second."
                    )
                }

                else -> {
                    return SyncResult(success = false, message = "Unknown Dapr mode: $mode")
                }
            }
        } catch (e: Exception) {
            logger.error("Sync failed for '{}': {}", transformationName, e.message, e)
            syncStatus[transformationName] = SyncState(
                status = "error",
                error = e.message
            )
            return SyncResult(success = false, message = "Sync failed: ${e.message}")
        }
    }

    /**
     * Remove all Dapr component YAML for a transformation (used on DELETE).
     */
    fun removeOnDelete(transformationName: String) {
        if (componentsDir != null && Files.exists(componentsDir)) {
            removeComponentYaml(componentsDir, transformationName)
        }
        syncStatus.remove(transformationName)
    }

    /**
     * Generate a Dapr component YAML file for a messaging endpoint.
     */
    private fun generateComponentYaml(
        dir: Path,
        transformationName: String,
        role: String,  // "input" or "output"
        endpoint: MessagingEndpoint
    ): SyncAction? {
        return when {
            endpoint.queue != null -> generateQueueBinding(dir, transformationName, role, endpoint)
            endpoint.topic != null -> generatePubSubComponent(dir, transformationName, role, endpoint)
            endpoint.eventhub != null -> generateEventHubBinding(dir, transformationName, role, endpoint)
            else -> null
        }
    }

    private fun generateQueueBinding(
        dir: Path, transformationName: String, role: String, endpoint: MessagingEndpoint
    ): SyncAction {
        val componentName = endpoint.queue!!
        val filename = "binding-$componentName.yaml"
        val filePath = dir.resolve(filename)
        val existed = Files.exists(filePath)

        val yaml = """
            |# Auto-generated by UTLXe — do not edit manually
            |# Transformation: $transformationName ($role)
            |# Generated: ${Instant.now()}
            |apiVersion: dapr.io/v1alpha1
            |kind: Component
            |metadata:
            |  name: $componentName
            |  annotations:
            |    utlxe.io/managed: "true"
            |    utlxe.io/transformation: "$transformationName"
            |    utlxe.io/role: "$role"
            |spec:
            |  type: bindings.azure.servicebusqueues
            |  version: v1
            |  metadata:
            |    - name: namespaceName
            |      value: "${servicebusNamespace ?: "CONFIGURE_ME.servicebus.windows.net"}"
            |    - name: queueName
            |      value: "$componentName"
            |    - name: direction
            |      value: "input, output"
            |    - name: maxConcurrentHandlers
            |      value: "1"
        """.trimMargin()

        Files.writeString(filePath, yaml)
        logger.info("Dapr: wrote {} component YAML: {}", if (existed) "updated" else "new", filePath)
        return SyncAction(if (existed) "updated" else "created", componentName, "bindings.azure.servicebusqueues")
    }

    @Suppress("UNUSED_PARAMETER")
    private fun generatePubSubComponent(
        dir: Path, transformationName: String, role: String, endpoint: MessagingEndpoint
    ): SyncAction {
        // Pub/sub: one shared component per namespace
        val componentName = "utlxe-servicebus"
        val filename = "pubsub-$componentName.yaml"
        val filePath = dir.resolve(filename)
        val existed = Files.exists(filePath)

        if (!existed) {
            val yaml = """
                |# Auto-generated by UTLXe — do not edit manually
                |# Shared Service Bus pub/sub component
                |# Generated: ${Instant.now()}
                |apiVersion: dapr.io/v1alpha1
                |kind: Component
                |metadata:
                |  name: $componentName
                |  annotations:
                |    utlxe.io/managed: "true"
                |    utlxe.io/role: "pubsub"
                |spec:
                |  type: pubsub.azure.servicebus.topics
                |  version: v1
                |  metadata:
                |    - name: namespaceName
                |      value: "${servicebusNamespace ?: "CONFIGURE_ME.servicebus.windows.net"}"
            """.trimMargin()

            Files.writeString(filePath, yaml)
            logger.info("Dapr: wrote shared pub/sub component YAML: {}", filePath)
        }

        return SyncAction(if (existed) "ensured" else "created", componentName, "pubsub.azure.servicebus.topics")
    }

    private fun generateEventHubBinding(
        dir: Path, transformationName: String, role: String, endpoint: MessagingEndpoint
    ): SyncAction {
        val componentName = endpoint.eventhub!!
        val isPubSub = endpoint.consumerGroup != null
        val componentType = if (isPubSub) "pubsub.azure.eventhubs" else "bindings.azure.eventhubs"
        val filename = "${if (isPubSub) "pubsub" else "binding"}-$componentName.yaml"
        val filePath = dir.resolve(filename)
        val existed = Files.exists(filePath)
        val nsValue = eventhubNamespace ?: "CONFIGURE_ME"
        val cgValue = endpoint.consumerGroup ?: ""
        val saValue = storageAccount ?: "CONFIGURE_ME"

        val pubsubMetadata = if (isPubSub) """
            |    - name: consumerGroup
            |      value: "$cgValue"
            |    - name: storageAccountName
            |      value: "$saValue"
            |    - name: storageContainerName
            |      value: "eventhub-checkpoints"
        """.trimMargin() else ""

        val yaml = """
            |# Auto-generated by UTLXe — do not edit manually
            |# Transformation: $transformationName ($role)
            |# Generated: ${Instant.now()}
            |apiVersion: dapr.io/v1alpha1
            |kind: Component
            |metadata:
            |  name: $componentName
            |  annotations:
            |    utlxe.io/managed: "true"
            |    utlxe.io/transformation: "$transformationName"
            |    utlxe.io/role: "$role"
            |spec:
            |  type: $componentType
            |  version: v1
            |  metadata:
            |    - name: eventHubNamespace
            |      value: "$nsValue"
            |    - name: eventHub
            |      value: "$componentName"
        """.trimMargin() + (if (pubsubMetadata.isNotEmpty()) "\n$pubsubMetadata" else "")

        Files.writeString(filePath, yaml)
        logger.info("Dapr: wrote {} Event Hub component YAML: {}", if (existed) "updated" else "new", filePath)
        return SyncAction(if (existed) "updated" else "created", componentName, componentType)
    }

    private fun removeComponentYaml(dir: Path, transformationName: String) {
        if (!Files.exists(dir)) return
        Files.list(dir).use { stream ->
            stream.filter { it.fileName.toString().endsWith(".yaml") }
                .forEach { file ->
                    try {
                        val content = Files.readString(file)
                        if (content.contains("utlxe.io/managed: \"true\"") &&
                            content.contains("utlxe.io/transformation: \"$transformationName\"")
                        ) {
                            Files.delete(file)
                            logger.info("Dapr: removed managed component YAML: {}", file)
                        }
                    } catch (e: Exception) {
                        logger.warn("Dapr: failed to check/remove {}: {}", file, e.message)
                    }
                }
        }
    }
}

data class SyncState(
    val status: String,  // synced, draft, error, no_dapr
    val lastSynced: Instant? = null,
    val pendingChanges: List<String>? = null,
    val error: String? = null
)

data class SyncResult(
    val success: Boolean,
    val actions: List<SyncAction> = emptyList(),
    val warnings: List<String> = emptyList(),
    val message: String = ""
)

data class SyncAction(
    val action: String,  // created, updated, ensured, validated, removed
    val component: String,
    val type: String
)

data class DaprComponent(
    val name: String,
    val type: String,
    val version: String
)
