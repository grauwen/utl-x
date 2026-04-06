package org.apache.utlx.engine

import org.apache.utlx.engine.bundle.BundleLoader
import org.apache.utlx.engine.config.EngineConfig
import org.apache.utlx.engine.config.TransformConfig
import org.apache.utlx.engine.health.HealthEndpoint
import org.apache.utlx.engine.pipe.*
import org.apache.utlx.engine.registry.TransformationInstance
import org.apache.utlx.engine.registry.TransformationRegistry
import org.apache.utlx.engine.strategy.ExecutionResult
import org.apache.utlx.engine.strategy.ExecutionStrategy
import org.apache.utlx.engine.strategy.TemplateStrategy
import org.slf4j.LoggerFactory
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicReference

class UtlxEngine(val config: EngineConfig) {

    private val logger = LoggerFactory.getLogger(UtlxEngine::class.java)
    private val stateRef = AtomicReference(EngineState.CREATED)
    private val startTime = System.currentTimeMillis()

    val registry = TransformationRegistry()
    private var healthEndpoint: HealthEndpoint? = null
    private var inputPipe: InputPipe? = null
    private var outputPipe: OutputPipe? = null

    val state: EngineState get() = stateRef.get()

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

            // Phase 1: load only the first transformation
            val (name, source, transformConfig) = discovered.first()
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

            if (discovered.size > 1) {
                logger.warn(
                    "Phase 1: only loaded first transformation '{}'. {} additional transformations ignored.",
                    name, discovered.size - 1
                )
            }

            stateRef.set(EngineState.READY)
            logger.info("Engine initialized — {} transformation(s) ready", registry.list().size)
        } catch (e: Exception) {
            stateRef.set(EngineState.STOPPED)
            logger.error("Engine initialization failed", e)
            throw e
        }
    }

    fun start() {
        val current = stateRef.get()
        require(current == EngineState.READY) {
            "Cannot start engine in state $current (expected READY)"
        }

        logger.info("Starting engine '{}'", config.engine.name)

        // Start health endpoint
        try {
            healthEndpoint = HealthEndpoint(this).also { it.start() }
        } catch (e: Exception) {
            logger.warn("Failed to start health endpoint: {}", e.message)
        }

        // Phase 1: stdio pipes
        inputPipe = StdioInputPipe()
        outputPipe = StdioOutputPipe()

        stateRef.set(EngineState.RUNNING)
        logger.info("Engine '{}' is RUNNING", config.engine.name)

        processMessages()
    }

    private fun processMessages() {
        val transformation = registry.list().firstOrNull()
            ?: throw IllegalStateException("No transformations registered")

        val input = inputPipe ?: throw IllegalStateException("Input pipe not connected")
        val output = outputPipe ?: throw IllegalStateException("Output pipe not connected")

        logger.info("Processing messages via stdin/stdout using transformation '{}'", transformation.name)

        while (state == EngineState.RUNNING) {
            val message = input.tryRead() ?: break

            try {
                val result = transformation.strategy.execute(
                    String(message.payload, Charsets.UTF_8)
                )
                val outputMessage = Message(
                    correlationId = message.correlationId,
                    payload = result.output.toByteArray(Charsets.UTF_8),
                    contentType = "application/json",
                    headers = message.headers
                )
                output.write(outputMessage)
            } catch (e: Exception) {
                logger.error("Error processing message: {}", e.message, e)
                val errorMessage = Message(
                    correlationId = message.correlationId,
                    payload = """{"error": "${e.message?.replace("\"", "\\\"") ?: "unknown"}"}""".toByteArray(Charsets.UTF_8),
                    contentType = "application/json"
                )
                output.write(errorMessage)
            }
        }
    }

    fun stop() {
        val current = stateRef.get()
        if (current == EngineState.STOPPED) {
            return
        }

        logger.info("Stopping engine '{}'...", config.engine.name)
        stateRef.set(EngineState.DRAINING)

        // Close pipes
        inputPipe?.close()
        outputPipe?.close()

        // Shutdown strategies
        registry.list().forEach { instance ->
            try {
                instance.strategy.shutdown()
            } catch (e: Exception) {
                logger.warn("Error shutting down strategy for '{}': {}", instance.name, e.message)
            }
        }

        // Stop health endpoint
        healthEndpoint?.stop()

        stateRef.set(EngineState.STOPPED)
        logger.info("Engine '{}' STOPPED", config.engine.name)
    }

    fun uptimeMs(): Long = System.currentTimeMillis() - startTime

    private fun createStrategy(config: TransformConfig): ExecutionStrategy {
        return when (config.strategy.uppercase()) {
            "TEMPLATE" -> TemplateStrategy()
            // Phase 2+: COPY, COMPILED, AUTO
            else -> {
                logger.warn("Strategy '{}' not yet implemented, falling back to TEMPLATE", config.strategy)
                TemplateStrategy()
            }
        }
    }
}
