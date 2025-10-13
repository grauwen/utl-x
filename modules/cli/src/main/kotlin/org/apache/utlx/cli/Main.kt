package org.apache.utlx.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.versionOption
import kotlin.system.exitProcess

/**
 * Main entry point for UTL-X CLI
 */
fun main(args: Array<String>) {
    try {
        UTLX()
            .subcommands(
                TransformCommand(),
                ValidateCommand(),
                CompileCommand(),
                FormatCommand(),
                MigrateCommand(),
                VersionCommand()
            )
            .main(args)
    } catch (e: Exception) {
        System.err.println("Error: ${e.message}")
        if (System.getProperty("utlx.debug") == "true") {
            e.printStackTrace()
        }
        exitProcess(1)
    }
}

/**
 * Root CLI command
 */
class UTLX : CliktCommand(
    name = "utlx",
    help = """
        Universal Transformation Language Extended (UTL-X)
        
        A format-agnostic functional transformation language for XML, JSON, CSV, YAML and more.
        
        Examples:
          utlx transform input.xml transform.utlx -o output.json
          utlx validate transform.utlx
          utlx compile transform.utlx -o transform.class
    """.trimIndent()
) {
    
    private val verbose by option("--verbose", "-v", help = "Enable verbose output").flag()
    private val debug by option("--debug", "-d", help = "Enable debug mode").flag()
    
    init {
        versionOption("1.0.0-SNAPSHOT")
    }
    
    override fun run() {
        if (verbose) {
            System.setProperty("utlx.verbose", "true")
        }
        if (debug) {
            System.setProperty("utlx.debug", "true")
        }
    }
}
