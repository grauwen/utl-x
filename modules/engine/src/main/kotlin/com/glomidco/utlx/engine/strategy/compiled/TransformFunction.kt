package com.glomidco.utlx.engine.strategy.compiled

import com.glomidco.utlx.core.udm.UDM

/**
 * Interface for compiled UTL-X transformations.
 * Generated at init-time by the ASTCompiler.
 * Each .utlx expression compiles to a class implementing this interface.
 */
interface TransformFunction {
    /**
     * Execute the compiled transformation.
     * @param inputs Named input UDMs (e.g., "input" → parsed JSON UDM)
     * @return The output UDM
     */
    fun execute(inputs: Map<String, UDM>): UDM
}
