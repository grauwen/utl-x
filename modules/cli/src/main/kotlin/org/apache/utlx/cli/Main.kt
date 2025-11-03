// modules/cli/src/main/kotlin/org/apache/utlx/cli/Main.kt
package org.apache.utlx.cli

import org.apache.utlx.cli.commands.*
import org.apache.utlx.core.debug.DebugConfig
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
        // Initialize debug configuration early (reads env vars and config files)
        DebugConfig.initialize()

        if (args.isEmpty()) {
            printUsage()
            exitProcess(0)
        }

        try {
            val command = args[0]
            val commandArgs = args.drop(1).toTypedArray()

            val result = when (command.lowercase()) {
                "transform", "t" -> TransformCommand.execute(commandArgs)
                "repl", "r" -> ReplCommand.execute(commandArgs)
                "design", "d" -> {
                    System.err.println("Design-time analysis features have been moved to the daemon server.")
                    System.err.println()
                    System.err.println("Please use 'utlxd' instead of 'utlx' for design commands:")
                    System.err.println("  utlxd design <subcommand> [options]")
                    System.err.println()
                    System.err.println("Available design subcommands:")
                    System.err.println("  generate-schema  Generate output schema from transformation")
                    System.err.println("  typecheck        Typecheck transformation against schemas")
                    System.err.println("  infer            Infer schema from transformation")
                    System.err.println("  daemon           Start LSP daemon for IDE integration")
                    System.err.println("  graph            Generate AST visualization")
                    System.err.println()
                    System.err.println("Examples:")
                    System.err.println("  utlxd design generate-schema -t transform.utlx -i input.xsd -f json-schema")
                    System.err.println("  utlxd design daemon --stdio")
                    System.err.println("  utlxd design graph transform.utlx -o ast.svg")
                    CommandResult.Failure("Use 'utlxd design' instead", 1)
                }
                "capture" -> CaptureCommand.execute(commandArgs)
                "validate", "v" -> ValidateCommand.execute(commandArgs)
                "lint", "l" -> LintCommand.execute(commandArgs)
                "compile", "c" -> {
                    println("Compile command not yet implemented")
                    println("Coming soon: Compile UTL-X scripts to bytecode")
                    CommandResult.Success
                }
                "format", "f" -> {
                    println("Format command not yet implemented")
                    println("Coming soon: Format/pretty-print UTL-X scripts")
                    CommandResult.Success
                }
                "migrate", "m" -> {
                    println("Migrate command not yet implemented")
                    println("Coming soon: Migrate XSLT/DataWeave to UTL-X")
                    CommandResult.Success
                }
                "functions", "fn" -> FunctionsCommand.execute(commandArgs)
                "version", "--version", "-v" -> {
                    println("UTL-X CLI v$VERSION")
                    println("Universal Transformation Language Extended")
                    CommandResult.Success
                }
                "help", "--help", "-h" -> {
                    printUsage()
                    CommandResult.Success
                }
                else -> {
                    System.err.println("Unknown command: $command")
                    printUsage()
                    CommandResult.Failure("Unknown command: $command", 1)
                }
            }

            // Handle command result - only Main.kt controls process exit
            when (result) {
                is CommandResult.Success -> exitProcess(0)
                is CommandResult.Failure -> {
                    // Error message already printed by command
                    exitProcess(result.exitCode)
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
            |  repl      (r)  Start interactive REPL session
            |  design    (d)  Design-time analysis (typecheck, generate schemas, IDE support)
            |  capture        Manage test capture settings (enable/disable/status)
            |  validate  (v)  Validate a UTL-X script for correctness (Levels 1-3)
            |  lint      (l)  Check for style issues and best practices (Level 4)
            |  compile   (c)  Compile a UTL-X script to bytecode
            |  format    (f)  Format/pretty-print a UTL-X script
            |  migrate   (m)  Migrate XSLT/DataWeave to UTL-X
            |  functions (fn) List available standard library functions
            |  version        Show version information
            |  help           Show this help message
            |
            |Examples:
            |  utlx repl
            |  utlx transform script.utlx input.xml -o output.json
            |  utlx design generate-schema --input-schema order.xsd --transform script.utlx --output-format json-schema
            |  utlx design typecheck --input-schema order.xsd --transform script.utlx --expected-output invoice-schema.json
            |  utlx capture status
            |  utlx validate script.utlx
            |  utlx transform --input-format xml --output-format json script.utlx < input.xml
            |  utlx transform data.yaml script.utlx --output-format json
            |
            |For more information: https://github.com/grauwen/utl-x
            |Documentation: https://utlx-lang.org/docs
        """.trimMargin())
    }
}
