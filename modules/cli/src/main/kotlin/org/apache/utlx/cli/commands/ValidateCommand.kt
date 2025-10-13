package org.apache.utlx.cli.commands

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.types.file
import org.apache.utlx.core.parser.Parser
import org.apache.utlx.core.types.TypeChecker

/**
 * Validate command - Check UTL-X syntax and types
 */
class ValidateCommand : CliktCommand(
    name = "validate",
    help = "Validate UTL-X transformation syntax and types"
) {
    
    private val transform by argument(
        name = "TRANSFORM",
        help = "UTL-X transformation file to validate"
    ).file(mustExist = true, mustBeReadable = true)
    
    override fun run() {
        echo("Validating: ${transform.absolutePath}")
        
        try {
            val source = transform.readText()
            
            // Parse
            echo("  Parsing...")
            val parser = Parser(source)
            val ast = parser.parse()
            echo("  ✓ Syntax valid")
            
            // Type check
            echo("  Type checking...")
            val typeChecker = TypeChecker()
            typeChecker.check(ast)
            echo("  ✓ Types valid")
            
            echo("\n✓ Validation successful")
            
        } catch (e: Exception) {
            echo("\n✗ Validation failed: ${e.message}", err = true)
            throw e
        }
    }
}
