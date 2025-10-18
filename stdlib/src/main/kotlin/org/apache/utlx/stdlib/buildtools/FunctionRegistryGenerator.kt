// stdlib/src/main/kotlin/org/apache/utlx/stdlib/buildtools/FunctionRegistryGenerator.kt
package org.apache.utlx.stdlib.buildtools

import org.apache.utlx.stdlib.StandardLibrary
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import java.io.File

/**
 * Build-time function registry generator
 * 
 * Generates JSON and YAML manifests of all UTL-X stdlib functions for external tools:
 * - VS Code plugins for autocomplete/IntelliSense  
 * - CLI help and documentation
 * - Third-party integrations
 */
object FunctionRegistryGenerator {
    
    fun generateRegistry(outputDir: File) {
        println("Generating UTL-X function registry...")
        
        val registry = StandardLibrary.exportRegistry()
        
        // Ensure output directory exists
        outputDir.mkdirs()
        
        // Generate JSON manifest
        val jsonMapper = ObjectMapper().registerModule(KotlinModule())
        val jsonFile = File(outputDir, "utlx-functions.json")
        jsonMapper.writerWithDefaultPrettyPrinter().writeValue(jsonFile, registry)
        
        // Generate YAML manifest  
        val yamlMapper = ObjectMapper(YAMLFactory()).registerModule(KotlinModule())
        val yamlFile = File(outputDir, "utlx-functions.yaml")
        yamlMapper.writeValue(yamlFile, registry)
        
        // Generate simple text list
        val txtFile = File(outputDir, "utlx-functions.txt")
        txtFile.writeText(generateTextRegistry(registry))
        
        println("Generated function registry with ${registry.totalFunctions} functions:")
        println("  - JSON: ${jsonFile.absolutePath}")
        println("  - YAML: ${yamlFile.absolutePath}") 
        println("  - TXT:  ${txtFile.absolutePath}")
        
        // Print summary by category
        println("\nFunction breakdown by category:")
        registry.categories.forEach { (category, functions) ->
            println("  $category: ${functions.size} functions")
        }
    }
    
    private fun generateTextRegistry(registry: org.apache.utlx.stdlib.FunctionRegistry): String {
        val sb = StringBuilder()
        sb.appendLine("UTL-X Standard Library Functions")
        sb.appendLine("Generated: ${registry.generatedAt}")
        sb.appendLine("Total Functions: ${registry.totalFunctions}")
        sb.appendLine("=".repeat(60))
        sb.appendLine()
        
        registry.categories.forEach { (category, functions) ->
            sb.appendLine("$category Functions (${functions.size}):")
            sb.appendLine("-".repeat(40))
            functions.forEach { func ->
                val args = when {
                    func.minArgs == func.maxArgs && func.minArgs != null -> " (${func.minArgs} args)"
                    func.minArgs != null && func.maxArgs != null -> " (${func.minArgs}-${func.maxArgs} args)"
                    func.minArgs != null -> " (${func.minArgs}+ args)"
                    func.maxArgs != null -> " (≤${func.maxArgs} args)"
                    else -> ""
                }
                sb.appendLine("  ${func.name}${args} - ${func.description}")
            }
            sb.appendLine()
        }
        
        return sb.toString()
    }
}

/**
 * Main function for running as standalone tool
 */
fun main(args: Array<String>) {
    when {
        args.isEmpty() || args[0].startsWith("-") -> {
            handleCommands(args)
        }
        else -> {
            // Generate registry to specified directory
            val outputDir = File(args[0])
            FunctionRegistryGenerator.generateRegistry(outputDir)
        }
    }
}

private fun handleCommands(args: Array<String>) {
    val command = args.getOrNull(0) ?: "--help"
    val registry = StandardLibrary.exportRegistry()
    
    when (command) {
        "--list" -> {
            println("UTL-X Standard Library Functions (${registry.totalFunctions} total):")
            println("=".repeat(60))
            registry.functions.forEach { func ->
                val args = formatArguments(func)
                println("${func.name}${args} - ${func.description}")
            }
        }
        
        "--categories" -> {
            println("UTL-X Functions by Category:")
            println("=".repeat(40))
            registry.categories.forEach { (category, functions) ->
                println("\n$category (${functions.size} functions):")
                println("-".repeat(30))
                functions.forEach { func ->
                    val args = formatArguments(func)
                    println("  ${func.name}${args}")
                }
            }
        }
        
        "--count" -> {
            println("UTL-X Function Count: ${registry.totalFunctions}")
            registry.categories.forEach { (category, functions) ->
                println("  $category: ${functions.size}")
            }
        }
        
        "--search" -> {
            val pattern = args.getOrNull(1)
            if (pattern == null) {
                println("Error: search requires a pattern")
                return
            }
            
            val matches = registry.functions.filter { func ->
                func.name.contains(pattern, ignoreCase = true) || 
                func.description.contains(pattern, ignoreCase = true)
            }
            
            println("Functions matching '$pattern' (${matches.size} found):")
            println("=".repeat(50))
            matches.forEach { func ->
                val args = formatArguments(func)
                println("${func.name}${args} - ${func.description}")
            }
        }
        
        "--info" -> {
            val functionName = args.getOrNull(1)
            if (functionName == null) {
                println("Error: info requires a function name")
                return
            }
            
            val func = registry.functions.find { it.name == functionName }
            if (func == null) {
                println("Function '$functionName' not found")
                return
            }
            
            println("Function: ${func.name}")
            println("Description: ${func.description}")
            println("Arguments: ${formatArguments(func)}")
            
            // Find category
            val category = registry.categories.entries.find { (_, functions) ->
                functions.any { it.name == functionName }
            }?.key ?: "Other"
            println("Category: $category")
        }
        
        "--help", "-h" -> {
            println("""
UTL-X Function Registry Tool

COMMANDS:
  --list                List all functions
  --categories          Show functions by category  
  --count               Show function count
  --search <pattern>    Search functions by name/description
  --info <function>     Show detailed function info
  --help                Show this help

EXAMPLES:
  kotlin -cp build/libs/*:... FunctionRegistryGeneratorKt --list
  kotlin -cp build/libs/*:... FunctionRegistryGeneratorKt --search xml
  kotlin -cp build/libs/*:... FunctionRegistryGeneratorKt --info map
            """.trimIndent())
        }
        
        else -> {
            println("Unknown command: $command")
            println("Use --help for available commands")
        }
    }
}

private fun formatArguments(func: org.apache.utlx.stdlib.FunctionInfo): String {
    return when {
        func.minArgs == func.maxArgs && func.minArgs != null -> " (${func.minArgs} args)"
        func.minArgs != null && func.maxArgs != null -> " (${func.minArgs}-${func.maxArgs} args)"
        func.minArgs != null -> " (${func.minArgs}+ args)"
        func.maxArgs != null -> " (≤${func.maxArgs} args)"
        else -> ""
    }
}