
// modules/cli/src/main/kotlin/org/apache/utlx/cli/commands/FormatCommand.kt
package org.apache.utlx.cli.commands

import java.io.File
import kotlin.system.exitProcess

object FormatCommand {
    fun execute(args: Array<String>) {
        if (args.isEmpty()) {
            printUsage()
            exitProcess(1)
        }
        
        val check = args.contains("--check")
        val verbose = args.contains("-v") || args.contains("--verbose")
        val scriptFiles = args.filter { !it.startsWith("-") }.map { File(it) }
        
        if (scriptFiles.isEmpty()) {
            System.err.println("Error: No script files provided")
            exitProcess(1)
        }
        
        var hasChanges = false
        
        for (scriptFile in scriptFiles) {
            if (!scriptFile.exists()) {
                System.err.println("✗ ${scriptFile.name}: File not found")
                continue
            }
            
            val content = scriptFile.readText()
            val formatted = formatScript(content)
            
            if (content != formatted) {
                hasChanges = true
                
                if (check) {
                    println("✗ ${scriptFile.name}: Would reformat")
                } else {
                    scriptFile.writeText(formatted)
                    println("✓ ${scriptFile.name}: Formatted")
                }
            } else {
                if (verbose) {
                    println("✓ ${scriptFile.name}: Already formatted")
                }
            }
        }
        
        if (check && hasChanges) {
            exitProcess(1)
        }
    }
    
    private fun formatScript(content: String): String {
        // Basic formatting implementation
        // TODO: Implement proper AST-based formatting
        return content.lines()
            .map { it.trimEnd() }
            .joinToString("\n")
            .replace(Regex("\\{\\s+"), "{\n  ")
            .replace(Regex("\\s+\\}"), "\n}")
    }
    
    private fun printUsage() {
        println("""
            |Format UTL-X scripts
            |
            |Usage:
            |  utlx format <script-file>... [options]
            |
            |Options:
            |  --check         Check if files are formatted (exit 1 if not)
            |  -v, --verbose   Show all files processed
            |  -h, --help      Show this help message
        """.trimMargin())
    }
}

