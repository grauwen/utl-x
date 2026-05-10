package org.apache.utlx.engine

import org.apache.utlx.engine.bundle.BundleLoader
import org.apache.utlx.engine.config.EngineConfig
import org.apache.utlx.engine.config.TransformConfig
import org.apache.utlx.engine.health.HealthEndpoint
import org.apache.utlx.engine.registry.TransformationInstance
import org.apache.utlx.engine.registry.TransformationRegistry
import org.apache.utlx.engine.strategy.CompiledStrategy
import org.apache.utlx.engine.strategy.CopyStrategy
import org.apache.utlx.engine.strategy.ExecutionStrategy
import org.apache.utlx.engine.strategy.TemplateStrategy
import org.apache.utlx.engine.transport.TransportServer
import org.slf4j.LoggerFactory
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicReference

class UtlxEngine(val config: EngineConfig) {

    private val logger = LoggerFactory.getLogger(UtlxEngine::class.java)
    private val stateRef = AtomicReference(EngineState.CREATED)
    private val startTime = System.currentTimeMillis()

    val registry = TransformationRegistry()
    private var healthEndpoint: HealthEndpoint? = null
    private var transport: TransportServer? = null
    private var allTransports: List<TransportServer> = emptyList()
    var dataDir: String? = null
    val validationOverrides = org.apache.utlx.engine.admin.ValidationOverrideStore()
    var daprIntegration: org.apache.utlx.engine.admin.DaprIntegration? = null
    var bundleInfo: org.apache.utlx.engine.admin.BundleInfo = org.apache.utlx.engine.admin.BundleInfo(mode = "open")

    /** Whether the engine is in locked (production) mode — .utlar found on disk. */
    val isLocked: Boolean get() = bundleInfo.mode == "locked"

    /** Heap backpressure threshold (0.0-1.0). When heap usage exceeds this, return 503 to Dapr. */
    @Volatile var heapBackpressureThreshold: Double = 0.85

    /** Cached heap usage (0.0-1.0) — updated every 100ms by background thread. */
    @Volatile var heapUsage: Double = 0.0
        private set

    /** Check if heap is under pressure (compares cached number against threshold — one comparison). */
    fun isHeapPressure(): Boolean = heapUsage > heapBackpressureThreshold

    /** Current heap usage as percentage (for Admin API display). */
    fun heapUsagePercent(): Int = (heapUsage * 100).toInt()

    // Background thread updates heap usage every 100ms
    private val heapMonitor = Thread({
        while (!Thread.currentThread().isInterrupted) {
            try {
                val rt = Runtime.getRuntime()
                val used = rt.totalMemory() - rt.freeMemory()
                val max = rt.maxMemory()
                heapUsage = used.toDouble() / max
                Thread.sleep(100)
            } catch (_: InterruptedException) {
                break
            }
        }
    }, "heap-monitor").apply {
        isDaemon = true
        start()
    }

    val state: EngineState get() = stateRef.get()

    /**
     * Initialize from a bundle directory on disk.
     * Loads ALL transformations from the bundle (Phase 1 single-transform limit removed).
     * Used by: stdio-json mode (required), stdio-proto/grpc mode (optional pre-loading).
     */
    fun initialize(bundlePath: Path) {
        val current = stateRef.get()
        require(current == EngineState.CREATED) {
            "Cannot initialize engine in state $current (expected CREATED)"
        }
        stateRef.set(EngineState.INITIALIZING)
        logger.info("Initializing engine '{}' from bundle: {}", config.engine.name, bundlePath)

        try {
            val bundleLoader = BundleLoader()
            val discovered = bundleLoader.load(bundlePath)

            if (discovered.isEmpty()) {
                throw IllegalStateException("No transformations found in bundle: $bundlePath")
            }

            // Load ALL discovered transformations (Phase 1 limit removed)
            for ((name, source, transformConfig) in discovered) {
                logger.info("Loading transformation '{}' with strategy {}", name, transformConfig.strategy)

                val strategy = createStrategy(transformConfig)
                strategy.initialize(source, transformConfig)

                // EF02: Create validators from transform.yaml schema references
                // Single-input: first input gets inputValidator
                val inputValidator = resolveValidatorFromConfig(
                    transformConfig.inputs.firstOrNull()?.schema,
                    bundlePath
                )
                // Multi-input: each named input gets its own validator
                val inputValidators = transformConfig.inputs
                    .filter { it.schema != null }
                    .mapNotNull { slot ->
                        val validator = resolveValidatorFromConfig(slot.schema, bundlePath)
                        if (validator != null) slot.name to validator else null
                    }
                    .toMap()

                val outputValidator = resolveValidatorFromConfig(
                    transformConfig.output.schema,
                    bundlePath
                )

                val instance = TransformationInstance(
                    name = name,
                    source = source,
                    strategy = strategy,
                    config = transformConfig,
                    inputValidator = inputValidator,
                    inputValidators = inputValidators,
                    outputValidator = outputValidator
                )
                registry.register(name, instance)

                val validatorCount = (if (inputValidator != null) 1 else 0) + inputValidators.size + (if (outputValidator != null) 1 else 0)
                if (validatorCount > 0) {
                    logger.info("Transformation '{}' validators: single-input={}, multi-input={}, output={}",
                        name, inputValidator != null, inputValidators.keys, outputValidator != null)
                }
            }

            stateRef.set(EngineState.READY)
            logger.info("Engine initialized — {} transformation(s) ready", registry.size())
        } catch (e: Exception) {
            stateRef.set(EngineState.STOPPED)
            logger.error("Engine initialization failed", e)
            throw e
        }
    }

    /**
     * Initialize without a bundle (for dynamic-loading transport modes).
     * The engine starts with an empty registry. Transformations are loaded dynamically
     * via LoadTransformation messages from the caller.
     * Used by: stdio-proto mode, grpc mode.
     */
    fun initializeEmpty() {
        val current = stateRef.get()
        require(current == EngineState.CREATED) {
            "Cannot initialize engine in state $current (expected CREATED)"
        }
        stateRef.set(EngineState.INITIALIZING)
        logger.info("Initializing engine '{}'", config.engine.name)
        stateRef.set(EngineState.READY)
        logger.info("Engine initialized — transformations loaded from data dir or Admin API")
    }

    /**
     * EF03: Scan a data directory for previously persisted transformations and load them.
     * Called at startup before the transport starts. Transformations are compiled and registered.
     * If the directory doesn't exist or is empty, nothing happens (engine starts empty).
     */
    fun scanDataDir(dataDirPath: java.nio.file.Path) {
        val txDir = dataDirPath.resolve("transformations")
        if (!java.nio.file.Files.exists(txDir) || !java.nio.file.Files.isDirectory(txDir)) {
            logger.info("Data dir '{}' has no transformations/ — starting empty", dataDirPath)
            return
        }

        var loaded = 0
        java.nio.file.Files.list(txDir).use { stream ->
            stream.filter { java.nio.file.Files.isDirectory(it) }
                .sorted()
                .forEach { dir ->
                    val name = dir.fileName.toString()
                    val utlxFile = dir.resolve("$name.utlx")
                    if (java.nio.file.Files.exists(utlxFile)) {
                        try {
                            val source = java.nio.file.Files.readString(utlxFile)
                            // EF10: Load transform.yaml if present (includes messaging config)
                            val configFile = dir.resolve("transform.yaml")
                            val transformConfig = if (java.nio.file.Files.exists(configFile)) {
                                try {
                                    org.apache.utlx.engine.config.TransformConfig.load(configFile)
                                } catch (e: Exception) {
                                    logger.warn("Failed to load transform.yaml for '{}': {}, using defaults", name, e.message)
                                    org.apache.utlx.engine.config.TransformConfig()
                                }
                            } else {
                                org.apache.utlx.engine.config.TransformConfig()
                            }
                            val strategy = createStrategy(
                                org.apache.utlx.engine.config.TransformConfig(strategy = transformConfig.strategy.ifEmpty { "COMPILED" })
                            )
                            strategy.initialize(source, transformConfig)
                            val instance = org.apache.utlx.engine.registry.TransformationInstance(
                                name = name,
                                source = source,
                                strategy = strategy,
                                config = transformConfig
                            )
                            registry.register(name, instance)
                            loaded++
                            val hasMessaging = transformConfig.input != null || transformConfig.outputMessaging != null
                            logger.info("Loaded persisted transformation '{}' [strategy={}, validation={}, messaging={}]",
                                name, transformConfig.strategy.ifEmpty { "COMPILED" }, transformConfig.validationPolicy, hasMessaging)
                            if (logger.isDebugEnabled && hasMessaging) {
                                logger.debug("  '{}' input={}, output={}", name, transformConfig.input, transformConfig.outputMessaging)
                            }
                        } catch (e: Exception) {
                            logger.error("Failed to load persisted transformation '{}': {}", name, e.message)
                        }
                    }
                }
        }
        logger.info("Data dir scan complete: {} transformation(s) loaded from {}", loaded, txDir)
    }

    /**
     * Start the engine with a single transport.
     * The transport's start() method blocks until shutdown.
     */
    fun start(transportServer: TransportServer) {
        start(listOf(transportServer))
    }

    /**
     * EF07: Start the engine with multiple transports.
     * All transports share the same registry, metrics, and health state.
     * The last transport blocks the main thread; others run on daemon threads.
     */
    fun start(transports: List<TransportServer>) {
        require(transports.isNotEmpty()) { "At least one transport is required" }
        val current = stateRef.get()
        require(current == EngineState.READY) {
            "Cannot start engine in state $current (expected READY)"
        }

        this.transport = transports.first()
        this.allTransports = transports
        logger.info("Starting engine '{}' with {} transport(s): {}",
            config.engine.name, transports.size, transports.joinToString { it.name })

        // Start health endpoint
        try {
            healthEndpoint = HealthEndpoint(this, dataDir = dataDir).also { it.start() }
        } catch (e: Exception) {
            logger.warn("Failed to start health endpoint: {}", e.message)
        }

        stateRef.set(EngineState.RUNNING)
        logger.info("Engine '{}' is RUNNING", config.engine.name)

        // Start all transports except the last on background threads
        for (bg in transports.dropLast(1)) {
            Thread({
                try {
                    logger.info("Starting background transport: {}", bg.name)
                    bg.start(registry)
                } catch (e: Exception) {
                    logger.error("Background transport {} failed: {}", bg.name, e.message, e)
                }
            }, "transport-${bg.name}").apply {
                isDaemon = true
                start()
            }
        }

        // Last transport blocks the main thread
        transports.last().start(registry)
    }

    fun stop() {
        val current = stateRef.get()
        if (current == EngineState.STOPPED) {
            return
        }

        logger.info("Stopping engine '{}'...", config.engine.name)
        stateRef.set(EngineState.DRAINING)

        // Stop all transports (EF07: parallel transports)
        allTransports.forEach { t ->
            try { t.stop() } catch (e: Exception) {
                logger.warn("Error stopping transport {}: {}", t.name, e.message)
            }
        }
        // Fallback: stop single transport if allTransports not set
        if (allTransports.isEmpty()) transport?.stop()

        // Shutdown all strategies
        registry.shutdownAll()

        // Stop health endpoint
        healthEndpoint?.stop()

        stateRef.set(EngineState.STOPPED)
        logger.info("Engine '{}' STOPPED", config.engine.name)
    }

    fun uptimeMs(): Long = System.currentTimeMillis() - startTime

    /**
     * Create an execution strategy by name. Used by both bundle loading and dynamic loading.
     */
    fun createStrategy(config: TransformConfig): ExecutionStrategy {
        return when (config.strategy.uppercase()) {
            "TEMPLATE", "INTERPRETED" -> TemplateStrategy()
            "COPY" -> CopyStrategy()
            "COMPILED" -> CompiledStrategy()
            "AUTO" -> {
                val hasSchema = config.inputs.any { it.schema != null }
                if (hasSchema) {
                    logger.info("AUTO strategy: schema available → COPY")
                    CopyStrategy()
                } else {
                    logger.info("AUTO strategy: no schema → TEMPLATE")
                    TemplateStrategy()
                }
            }
            else -> {
                logger.warn("Strategy '{}' not yet implemented, falling back to TEMPLATE", config.strategy)
                TemplateStrategy()
            }
        }
    }

    /**
     * EF02: Resolve a schema validator from a transform.yaml schema reference.
     * The schema path is relative to the bundle directory.
     * Returns null if no schema configured or file not found.
     */
    private fun resolveValidatorFromConfig(
        schemaRef: String?,
        bundlePath: java.nio.file.Path
    ): org.apache.utlx.engine.validation.SchemaValidator? {
        if (schemaRef.isNullOrBlank()) return null
        try {
            // Try relative to bundle, then absolute
            val schemaFile = bundlePath.resolve(schemaRef)
            if (!java.nio.file.Files.exists(schemaFile)) {
                logger.warn("Schema file not found: {} (relative to {})", schemaRef, bundlePath)
                return null
            }
            val content = java.nio.file.Files.readString(schemaFile)
            val format = when {
                schemaRef.endsWith(".xsd") -> "xsd"
                schemaRef.endsWith(".json") -> "json-schema"
                schemaRef.endsWith(".avsc") -> "avro"
                schemaRef.endsWith(".proto") -> "protobuf"
                schemaRef.endsWith(".yaml") || schemaRef.endsWith(".yml") -> "yaml"
                else -> "json-schema"
            }
            return org.apache.utlx.engine.validation.SchemaValidatorFactory.create(content, format)
        } catch (e: Exception) {
            logger.warn("Failed to create validator from schema '{}': {}", schemaRef, e.message)
            return null
        }
    }
}
