package org.apache.utlx.engine.registry

import org.apache.utlx.engine.config.TransformConfig
import org.apache.utlx.engine.strategy.ExecutionStrategy
import org.apache.utlx.engine.validation.SchemaValidator
import org.slf4j.LoggerFactory
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

class TransformationRegistry {

    private val logger = LoggerFactory.getLogger(TransformationRegistry::class.java)
    private val transformations = ConcurrentHashMap<String, TransformationInstance>()

    /**
     * Register a pre-built transformation instance.
     * Used by bundle loading (init-time) and dynamic loading (stdio-proto/grpc).
     */
    fun register(name: String, instance: TransformationInstance) {
        transformations[name] = instance
        logger.info("Registered transformation '{}' [strategy={}]", name, instance.strategy.name)
    }

    /**
     * Get a transformation by ID. Returns null if not found.
     */
    fun get(name: String): TransformationInstance? = transformations[name]

    /**
     * List all registered transformations.
     */
    fun list(): List<TransformationInstance> = transformations.values.toList()

    /**
     * Remove (unload) a transformation by ID.
     * Calls shutdown on the strategy before removal.
     */
    fun unload(name: String): Boolean {
        val instance = transformations.remove(name)
        if (instance != null) {
            try {
                instance.strategy.shutdown()
            } catch (e: Exception) {
                logger.warn("Error shutting down strategy for '{}': {}", name, e.message)
            }
            logger.info("Unloaded transformation '{}'", name)
            return true
        }
        return false
    }

    /**
     * Remove a transformation by ID. Returns the removed instance or null.
     */
    fun remove(name: String): TransformationInstance? {
        val instance = transformations.remove(name)
        instance?.let {
            try { it.strategy.shutdown() } catch (_: Exception) {}
        }
        return instance
    }

    fun size(): Int = transformations.size

    /**
     * Shutdown all transformations. Called during engine stop.
     */
    fun shutdownAll() {
        transformations.forEach { (name, instance) ->
            try {
                instance.strategy.shutdown()
            } catch (e: Exception) {
                logger.warn("Error shutting down strategy for '{}': {}", name, e.message)
            }
        }
        transformations.clear()
    }
}

data class TransformationInstance(
    val name: String,
    val source: String,
    val strategy: ExecutionStrategy,
    val config: TransformConfig,
    val loadedAt: Instant = Instant.now(),
    val executionCount: AtomicLong = AtomicLong(0),
    val errorCount: AtomicLong = AtomicLong(0),
    val inputValidator: SchemaValidator? = null,
    val outputValidator: SchemaValidator? = null
) {
    fun recordExecution() = executionCount.incrementAndGet()
    fun recordError() = errorCount.incrementAndGet()
}
