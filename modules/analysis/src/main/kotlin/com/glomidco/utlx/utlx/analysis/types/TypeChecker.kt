// modules/analysis/src/main/kotlin/com/glomidco/utlx/analysis/types/TypeChecker.kt
package com.glomidco.utlx.analysis.types

/**
 * Type checker for UTL-X analysis
 * TODO: Full implementation pending
 */
object TypeChecker {
    /**
     * Check if a value of source type can be assigned to target type
     */
    fun check(source: TypeDefinition, target: TypeDefinition): Boolean {
        return source.isCompatibleWith(target)
    }
}
