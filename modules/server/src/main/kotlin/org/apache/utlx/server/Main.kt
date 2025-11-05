// modules/server/src/main/kotlin/org/apache/utlx/server/Main.kt
package org.apache.utlx.server

import org.apache.utlx.server.commands.*
import kotlin.system.exitProcess

/**
 * UTL-X Daemon Server
 *
 * Long-running server providing LSP and REST API for IDE integration and MCP server.
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

            val result = when (command.lowercase()) {
                "start" -> StartCommand.execute(commandArgs)
                "stop" -> StopCommand.execute(commandArgs)
                "status" -> StatusCommand.execute(commandArgs)
                "design", "d" -> DesignCommand.execute(commandArgs)
                "version", "--version", "-v" -> {
                    println("UTL-X Daemon v$VERSION")
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

            // Handle command result
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
            |UTL-X Daemon v$VERSION - Universal Transformation Language Extended
            |
            |Usage: utlxd <command> [options]
            |
            |Commands:
            |  start     Start the UTL-X daemon (LSP + REST API)
            |  stop      Stop a running daemon
            |  status    Check daemon status
            |  design    Design-time analysis tools (graph, analyze, etc.)
            |  version   Show version information
            |  help      Show this help message
            |
            |For more information about a specific command, run:
            |  utlxd <command> --help
            |
            |Examples:
            |  # Start with both LSP and API - all defaults (socket transport, default ports)
            |  utlxd start --lsp [--lsp-transport socket|stdio] [--lsp-port 7777] --api [--api-transport socket|stdio] [--api-port 7779]
            |
            |  # Start with LSP only (STDIO transport)
            |  utlxd start --lsp --lsp-transport stdio
            |
            |  # Start with API only (default socket transport and port)
            |  utlxd start --api [--api-transport socket|stdio] [--api-port 7779]
            |
            |  # Check daemon status
            |  utlxd status
            |
            |  # Stop running daemon
            |  utlxd stop
        """.trimMargin())
    }
}

/**
 * Command result sealed class
 */
sealed class CommandResult {
    object Success : CommandResult()
    data class Failure(val message: String, val exitCode: Int = 1) : CommandResult()
}
