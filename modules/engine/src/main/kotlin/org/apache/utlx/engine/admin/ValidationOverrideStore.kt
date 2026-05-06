package org.apache.utlx.engine.admin

import java.util.concurrent.ConcurrentHashMap

/**
 * EF02/EF03: Runtime validation overrides — ephemeral, not persisted.
 *
 * Precedence: runtime override > transform.yaml config > .utlx header > default
 * Overrides are lost on container restart (by design — emergency lever only).
 */
class ValidationOverrideStore {

    data class Override(
        val policy: String  // "strict", "warn", "off"
    )

    private val overrides = ConcurrentHashMap<String, Override>()

    fun set(transformationName: String, policy: String) {
        overrides[transformationName] = Override(policy)
    }

    fun get(transformationName: String): Override? = overrides[transformationName]

    fun remove(transformationName: String): Boolean = overrides.remove(transformationName) != null

    /**
     * Resolve effective validation policy for a transformation.
     * Precedence: runtime override > config > default
     */
    fun effectivePolicy(transformationName: String, configPolicy: String): String {
        val override = overrides[transformationName]
        return override?.policy ?: configPolicy
    }
}
