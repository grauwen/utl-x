package org.apache.utlx.engine.registry

import org.apache.utlx.engine.config.TransformConfig
import org.apache.utlx.engine.strategy.ExecutionStrategy
import java.util.concurrent.ConcurrentHashMap

class TransformationRegistry {

    private val transformations = ConcurrentHashMap<String, TransformationInstance>()

    fun register(name: String, instance: TransformationInstance) {
        transformations[name] = instance
    }

    fun get(name: String): TransformationInstance? = transformations[name]

    fun list(): List<TransformationInstance> = transformations.values.toList()

    fun remove(name: String): TransformationInstance? = transformations.remove(name)

    fun size(): Int = transformations.size
}

data class TransformationInstance(
    val name: String,
    val source: String,
    val strategy: ExecutionStrategy,
    val config: TransformConfig
)
