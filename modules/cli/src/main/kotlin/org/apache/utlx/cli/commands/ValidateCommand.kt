// modules/cli/src/main/kotlin/org/apache/utlx/cli/commands/ValidateCommand.kt
package org.apache.utlx.cli.commands

import org.apache.utlx.core.lexer.Lexer
import org.apache.utlx.core.parser.Parser
import org.apache.utlx.core.types.TypeChecker
import java.io.File
import kotlin.system.exitProcess

/**
 * Validate command - checks UTL-X scripts for syntax and type errors
 */
object ValidateCommand {
    
    fun execute(args: Array<String>) {
        if (args.isEmpty()) {
            printUsage()
            exitProcess(1)
        }
        
        val verbose = args.contains("-v") || args.contains("--verbose")
        val strict = args.contains("--strict")
        
        val scriptFiles = args.filter { !it.startsWith("-") }.map { File(it) }
        
        if (scriptFiles.isEmpty()) {
            System.err.println("Error: No script files provided")
            printUsage()
            exitProcess(1)
        }
        
        var hasErrors = false
        
        for (scriptFile in scriptFiles) {
            if (!scriptFile.exists()) {
                System.err.println("✗ ${scriptFile.name}: File not found")
                hasErrors = true
                continue
            }
            
            try {
                if (verbose) {
                    println("Validating ${scriptFile.name}...")
                }
                
                val content = scriptFile.readText()
                
                // Lexical analysis
                val lexer = Lexer(content)
                val tokens = lexer.tokenize()
                
                if (verbose) {
                    println("  ✓ Lexical analysis passed (${tokens.size} tokens)")
                }
                
                // Syntax analysis
                val parser = Parser(tokens)
                val ast = parser.parse()
                
                if (verbose) {
                    println("  ✓ Syntax analysis passed")
                }
                
                // Type checking
                val typeChecker = TypeChecker()
                val warnings = typeChecker.check(ast)
                
                if (verbose) {
                    println("  ✓ Type checking passed")
                }
                
                if (warnings.isNotEmpty()) {
                    println("  ⚠ ${warnings.size} warning(s):")
                    warnings.forEach { warning ->
                        println("    - $warning")
                    }
                    
                    if (strict) {
                        hasErrors = true
                        println("✗ ${scriptFile.name}: Failed (strict mode)")
                        continue
                    }
                }
                
                println("✓ ${scriptFile.name}: Valid")
                
            } catch (e: Exception) {
                println("✗ ${scriptFile.name}: ${e.message}")
                if (verbose) {
                    e.printStackTrace()
                }
                hasErrors = true
            }
        }
        
        if (hasErrors) {
            exitProcess(1)
        }
    }
    
    private fun printUsage() {
        println("""
            |Validate UTL-X scripts
            |
            |Usage:
            |  utlx validate <script-file>... [options]
            |
            |Arguments:
            |  script-file     One or more UTL-X script files to validate
            |
            |Options:
            |  -v, --verbose   Show detailed validation information
            |  --strict        Treat warnings as errors
            |  -h, --help      Show this help message
            |
            |Examples:
            |  utlx validate script.utlx
            |  utlx validate script1.utlx script2.utlx script3.utlx
            |  utlx validate script.utlx --verbose
            |  utlx validate script.utlx --strict
        """.trimMargin())
    }
}
