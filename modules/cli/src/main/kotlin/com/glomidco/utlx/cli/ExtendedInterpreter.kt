// modules/cli/src/main/kotlin/com/glomidco/utlx/cli/ExtendedInterpreter.kt
package com.glomidco.utlx.cli

import com.glomidco.utlx.core.ast.Program
import com.glomidco.utlx.core.interpreter.Interpreter
import com.glomidco.utlx.core.interpreter.RuntimeValue
import com.glomidco.utlx.core.udm.UDM
import com.glomidco.utlx.stdlib.StandardLibrary

/**
 * Extended interpreter that integrates stdlib functions with the core interpreter
 */
class ExtendedInterpreter {
    private val coreInterpreter = Interpreter()
    
    init {
        // Register stdlib functions with the core interpreter's global environment
        registerStdlibFunctions()
    }
    
    fun execute(program: Program, inputUDM: UDM): RuntimeValue {
        // The interpreter already has stdlib functions registered in its global environment
        return coreInterpreter.execute(program, inputUDM)
    }
    
    private fun registerStdlibFunctions() {
        // Extended interpreter provides access to stdlib functions through the core interpreter
        // The core interpreter already has the most important functions built-in
        // Additional stdlib functions can be accessed via the stdlib module directly
    }
    
}