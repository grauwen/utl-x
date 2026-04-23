package org.apache.utlx.engine

import org.apache.utlx.engine.bundle.BundleLoader
import org.apache.utlx.engine.config.EngineConfig
import org.apache.utlx.engine.config.TransformConfig
import org.apache.utlx.engine.health.HealthEndpoint
import org.apache.utlx.engine.registry.TransformationInstance
import org.apache.utlx.engine.registry.TransformationRegistry
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

                val instance = TransformationInstance(
                    name = name,
                    source = source,
                    strategy = strategy,
                    config = transformConfig
                )
                registry.register(name, instance)
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
        logger.info("Initializing engine '{}' (no bundle — dynamic loading mode)", config.engine.name)
        stateRef.set(EngineState.READY)
        logger.info("Engine initialized — awaiting LoadTransformation messages")
    }

    /**
     * Start the engine with the given transport.
     * The transport's start() method blocks until shutdown.
     */
    fun start(transportServer: TransportServer) {
        val current = stateRef.get()
        require(current == EngineState.READY) {
            "Cannot start engine in state $current (expected READY)"
        }

        this.transport = transportServer
        logger.info("Starting engine '{}' with transport {}", config.engine.name, transportServer.javaClass.simpleName)

        // Start health endpoint
        try {
            healthEndpoint = HealthEndpoint(this).also { it.start() }
        } catch (e: Exception) {
            logger.warn("Failed to start health endpoint: {}", e.message)
        }

        stateRef.set(EngineState.RUNNING)
        logger.info("Engine '{}' is RUNNING", config.engine.name)

        // Transport.start() blocks until shutdown
        transportServer.start(registry)
    }

    fun stop() {
        val current = stateRef.get()
        if (current == EngineState.STOPPED) {
            return
        }

        logger.info("Stopping engine '{}'...", config.engine.name)
        stateRef.set(EngineState.DRAINING)

        // Stop transport
        transport?.stop()

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
            "TEMPLATE" -> TemplateStrategy()
            "COPY" -> CopyStrategy()
            "AUTO" -> {
                // AUTO: use COPY if schema-driven, TEMPLATE otherwise
                // For now, default to TEMPLATE until schema detection is smarter
                TemplateStrategy()
            }
            else -> {
                logger.warn("Strategy '{}' not yet implemented, falling back to TEMPLATE", config.strategy)
                TemplateStrategy()
            }
        }
    }
}
