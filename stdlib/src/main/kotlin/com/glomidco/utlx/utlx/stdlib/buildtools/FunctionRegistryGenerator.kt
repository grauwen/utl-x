// stdlib/src/main/kotlin/com/glomidco/utlx/stdlib/buildtools/FunctionRegistryGenerator.kt
package com.glomidco.utlx.stdlib.buildtools

import com.glomidco.utlx.stdlib.StandardLibrary
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
    
    private fun generateTextRegistry(registry: com.glomidco.utlx.stdlib.FunctionRegistry): String {
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
                // Was a five-branch `when` (duplicated in formatArguments() below) with cases for
                // "minArgs only" -> " (N+ args)", "maxArgs only" -> " (<=N args)" and a final
                // `else -> ""` for neither being known. All three were unreachable and were removed
                // in 1.4.0: FunctionInfo declares `minArgs: Int = -1` / `maxArgs: Int = -1`, not
                // Int?, so `!= null` is always true. The nullable shape the branches were written
                // for no longer exists; -1 is the "unknown" marker now, and it prints verbatim.
                // Renamed from `args` at the same time -- it shadowed the top-level `args` parameter.
                val arity = if (func.minArgs == func.maxArgs) " (${func.minArgs} args)"
                            else " (${func.minArgs}-${func.maxArgs} args)"
                val aliasMarker = if (func.isAlias && func.aliasOf != null) {
                    " [alias → ${func.aliasOf}]"
                } else ""
                sb.appendLine("  ${func.name}${arity}${aliasMarker} - ${func.description}")
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
                val arity = formatArguments(func)
                val aliasMarker = if (func.isAlias && func.aliasOf != null) " [alias → ${func.aliasOf}]" else ""
                println("${func.name}${arity}${aliasMarker} - ${func.description}")
            }
        }
        
        "--categories" -> {
            println("UTL-X Functions by Category:")
            println("=".repeat(40))
            registry.categories.forEach { (category, functions) ->
                println("\n$category (${functions.size} functions):")
                println("-".repeat(30))
                functions.forEach { func ->
                    val arity = formatArguments(func)
                    println("  ${func.name}${arity}")
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
                func.description.contains(pattern, ignoreCase = true) ||
                (func.isAlias && func.aliasOf?.contains(pattern, ignoreCase = true) == true)
            }

            println("Functions matching '$pattern' (${matches.size} found):")
            println("=".repeat(50))
            matches.forEach { func ->
                val arity = formatArguments(func)
                val aliasMarker = if (func.isAlias && func.aliasOf != null) " [alias → ${func.aliasOf}]" else ""
                println("${func.name}${arity}${aliasMarker} - ${func.description}")
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

private fun formatArguments(func: com.glomidco.utlx.stdlib.FunctionInfo): String {
    // Removed in 1.4.0, same as the inline copy in generateTextReport():
    //     func.minArgs != null -> " (${'$'}{func.minArgs}+ args)"      // open-ended arity
    //     func.maxArgs != null -> " (<=${'$'}{func.maxArgs} args)"     // capped arity
    //     else -> ""                                                   // arity unknown
    // All three were dead, because FunctionInfo holds `minArgs: Int = -1` / `maxArgs: Int = -1`
    // rather than Int?, so the null tests were always true and the first two branches always won.
    //
    // Worth knowing: -1 IS the "unknown" marker in the current model (avg, count, max, min and sum
    // carry it), and this renders it literally -- "(-1 args)". That is unchanged by the removal,
    // but it is the display wart those `else` branches were presumably meant to avoid, and it is
    // now the only place to fix it.
    return if (func.minArgs == func.maxArgs) " (${func.minArgs} args)"
           else " (${func.minArgs}-${func.maxArgs} args)"
}