// modules/cli/src/main/kotlin/org/apache/utlx/cli/Main.kt
package org.apache.utlx.cli

import org.apache.utlx.cli.commands.*
import kotlin.system.exitProcess

/**
 * UTL-X Command Line Interface
 * 
 * A practical CLI for transforming data between formats using UTL-X transformations.
 */
object Main {
    private const val VERSION = "1.0.0-SNAPSHOT"
    
    @JvmStatic
    fun main(args: Array<String>) {
        if (args.isEmpty()) {
            printUsage()
            exitProcess(0)
        }
        
        try {
            val command = args[0]
            val commandArgs = args.drop(1).toTypedArray()
            
            when (command.lowercase()) {
                "transform", "t" -> TransformCommand.execute(commandArgs)
                "validate", "v" -> ValidateCommand.execute(commandArgs)
                "compile", "c" -> CompileCommand.execute(commandArgs)
                "format", "f" -> FormatCommand.execute(commandArgs)
                "migrate", "m" -> MigrateCommand.execute(commandArgs)
                "functions", "fn" -> FunctionsCommand.execute(commandArgs)
                "version", "--version", "-v" -> VersionCommand.execute(commandArgs)
                "help", "--help", "-h" -> printUsage()
                else -> {
                    System.err.println("Unknown command: $command")
                    printUsage()
                    exitProcess(1)
                }
            }
        } catch (e: Exception) {
            System.err.println("Error: ${e.message}")
            if (System.getProperty("utlx.debug") == "true") {
                e.printStackTrace()
            }
            exitProcess(1)
        }
    }
    
    private fun printUsage() {
        println("""
            |UTL-X CLI v$VERSION - Universal Transformation Language Extended
            |
            |Usage: utlx <command> [options]
            |
            |Commands:
            |  transform (t)  Transform data using a UTL-X script
            |  validate  (v)  Validate a UTL-X script without executing
            |  compile   (c)  Compile a UTL-X script to bytecode
            |  format    (f)  Format/pretty-print a UTL-X script
            |  migrate   (m)  Migrate XSLT/DataWeave to UTL-X
            |  functions (fn) List available standard library functions
            |  version        Show version information
            |  help           Show this help message
            |
            |Examples:
            |  utlx transform input.xml script.utlx -o output.json
            |  utlx validate script.utlx
            |  utlx transform --input-format xml --output-format json script.utlx < input.xml
            |
            |For more information: https://github.com/grauwen/utl-x
            |Documentation: https://utlx-lang.org/docs
        """.trimMargin())
    }
}
