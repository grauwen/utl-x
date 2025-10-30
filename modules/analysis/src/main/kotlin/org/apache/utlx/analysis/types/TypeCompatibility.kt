// modules/analysis/src/main/kotlin/org/apache/utlx/analysis/types/TypeCompatibility.kt
package org.apache.utlx.analysis.types

/**
 * Utility object for checking type compatibility
 */
object TypeCompatibility {
    /**
     * Check if source type is compatible with target type
     */
    fun isCompatible(source: TypeDefinition, target: TypeDefinition): Boolean {
        return source.isCompatibleWith(target)
    }
}
