// modules/cli/src/main/kotlin/org/apache/utlx/cli/commands/FunctionsCommand.kt
package org.apache.utlx.cli.commands

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import org.apache.utlx.stdlib.FunctionRegistry
import org.apache.utlx.stdlib.FunctionInfo
import kotlin.system.exitProcess

/**
 * Functions command - list and explore standard library functions
 *
 * Usage:
 *   utlx functions                    - List all functions
 *   utlx functions list               - List all functions
 *   utlx functions search <query>     - Search functions
 *   utlx functions info <name>        - Show detailed function info
 *   utlx functions stats              - Show statistics
 *   utlx functions --format json      - JSON output for tools
 */
object FunctionsCommand {

    enum class OutputFormat {
        TEXT,           // Human-readable (default)
        JSON,           // Pretty-printed JSON
        JSON_COMPACT,   // Minified JSON
        YAML            // YAML format
    }

    fun execute(args: Array<String>) {
        try {
            // Parse arguments
            val (command, options) = parseArgs(args)
            val format = options["format"] as? OutputFormat ?: OutputFormat.TEXT

            // Load bundled registry
            val registry = loadBundledRegistry()

            // Execute command
            when (command) {
                "list", "" -> listFunctions(registry, format, options)
                "search" -> searchFunctions(registry, options["query"] as? String ?: "", format)
                "info" -> showInfo(registry, options["name"] as? String ?: "", format)
                "stats" -> showStats(registry, format)
                "categories" -> listCategories(registry, format)
                else -> {
                    System.err.println("Unknown subcommand: $command")
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

    private fun parseArgs(args: Array<String>): Pair<String, Map<String, Any>> {
        val options = mutableMapOf<String, Any>()
        val positional = mutableListOf<String>()

        var i = 0
        while (i < args.size) {
            when {
                args[i].startsWith("--") -> {
                    val key = args[i].substring(2)
                    when (key) {
                        "format" -> {
                            i++
                            if (i < args.size) {
                                options["format"] = when (args[i].lowercase()) {
                                    "json" -> OutputFormat.JSON
                                    "json-compact" -> OutputFormat.JSON_COMPACT
                                    "yaml" -> OutputFormat.YAML
                                    "text" -> OutputFormat.TEXT
                                    else -> throw IllegalArgumentException("Unknown format: ${args[i]}")
                                }
                            }
                        }
                        "category" -> {
                            i++
                            if (i < args.size) options["category"] = args[i]
                        }
                        "detailed" -> options["detailed"] = true
                        "help" -> {
                            printUsage()
                            exitProcess(0)
                        }
                    }
                }
                else -> positional.add(args[i])
            }
            i++
        }

        val command = positional.firstOrNull() ?: ""
        when (command) {
            "search" -> options["query"] = positional.getOrNull(1) ?: ""
            "info" -> options["name"] = positional.getOrNull(1) ?: ""
        }

        return Pair(command, options)
    }

    private fun loadBundledRegistry(): FunctionRegistry {
        // Try to load bundled registry from resources
        val stream = javaClass.getResourceAsStream("/function-registry/utlx-functions.json")
            ?: throw IllegalStateException(
                "Function registry not found. " +
                "The CLI may not be built correctly. " +
                "Try running: ./gradlew :stdlib:generateFunctionRegistry :modules:cli:build"
            )

        val mapper = ObjectMapper().registerModule(KotlinModule())
        return mapper.readValue(stream, FunctionRegistry::class.java)
    }

    private fun listFunctions(registry: FunctionRegistry, format: OutputFormat, options: Map<String, Any>) {
        val category = options["category"] as? String
        val functions = if (category != null) {
            registry.categories[category] ?: emptyList()
        } else {
            registry.functions
        }

        when (format) {
            OutputFormat.JSON -> printJson(functions, prettyPrint = true)
            OutputFormat.JSON_COMPACT -> printJson(functions, prettyPrint = false)
            OutputFormat.YAML -> printYaml(functions)
            OutputFormat.TEXT -> printTextList(functions, registry)
        }
    }

    private fun searchFunctions(registry: FunctionRegistry, query: String, format: OutputFormat) {
        if (query.isBlank()) {
            System.err.println("Error: search requires a query")
            println("Usage: utlx functions search <query>")
            exitProcess(1)
        }

        val results = registry.functions.filter { func ->
            func.name.contains(query, ignoreCase = true) ||
            func.description.contains(query, ignoreCase = true) ||
            func.tags.any { it.contains(query, ignoreCase = true) } ||
            func.category.contains(query, ignoreCase = true)
        }

        when (format) {
            OutputFormat.JSON -> printJson(results, prettyPrint = true)
            OutputFormat.JSON_COMPACT -> printJson(results, prettyPrint = false)
            OutputFormat.YAML -> printYaml(results)
            OutputFormat.TEXT -> {
                println("Functions matching '$query' (${results.size} found):")
                println("=".repeat(60))
                results.forEach { func ->
                    println("  ${func.name.padEnd(20)} - ${func.description}")
                }
                if (results.isEmpty()) {
                    println("  No functions found matching '$query'")
                }
            }
        }
    }

    private fun showInfo(registry: FunctionRegistry, name: String, format: OutputFormat) {
        if (name.isBlank()) {
            System.err.println("Error: info requires a function name")
            println("Usage: utlx functions info <function-name>")
            exitProcess(1)
        }

        val func = registry.functions.find { it.name.equals(name, ignoreCase = true) }
        if (func == null) {
            System.err.println("Function '$name' not found")
            exitProcess(1)
        }

        when (format) {
            OutputFormat.JSON -> printJson(func, prettyPrint = true)
            OutputFormat.JSON_COMPACT -> printJson(func, prettyPrint = false)
            OutputFormat.YAML -> printYaml(func)
            OutputFormat.TEXT -> printDetailedInfo(func)
        }
    }

    private fun showStats(registry: FunctionRegistry, format: OutputFormat) {
        val stats = mapOf(
            "version" to registry.version,
            "generated" to registry.generatedAt,
            "totalFunctions" to registry.totalFunctions,
            "categories" to registry.categories.mapValues { it.value.size }
        )

        when (format) {
            OutputFormat.JSON -> printJson(stats, prettyPrint = true)
            OutputFormat.JSON_COMPACT -> printJson(stats, prettyPrint = false)
            OutputFormat.YAML -> printYaml(stats)
            OutputFormat.TEXT -> {
                println("UTL-X Function Registry Statistics")
                println("=".repeat(60))
                println("Version: ${registry.version}")
                println("Generated: ${registry.generatedAt}")
                println("Total Functions: ${registry.totalFunctions}")
                println()
                println("Functions by Category:")
                registry.categories.entries.sortedByDescending { it.value.size }.forEach { (cat, funcs) ->
                    println("  ${cat.padEnd(15)} - ${funcs.size} functions")
                }
            }
        }
    }

    private fun listCategories(registry: FunctionRegistry, format: OutputFormat) {
        when (format) {
            OutputFormat.JSON -> printJson(registry.categories.keys.sorted(), prettyPrint = true)
            OutputFormat.JSON_COMPACT -> printJson(registry.categories.keys.sorted(), prettyPrint = false)
            OutputFormat.YAML -> printYaml(registry.categories.keys.sorted())
            OutputFormat.TEXT -> {
                println("Function Categories (${registry.categories.size} total):")
                println("=".repeat(60))
                registry.categories.entries.sortedBy { it.key }.forEach { (cat, funcs) ->
                    println("  ${cat.padEnd(15)} - ${funcs.size} functions")
                }
            }
        }
    }

    private fun printTextList(functions: List<FunctionInfo>, registry: FunctionRegistry) {
        println("UTL-X Standard Library Functions (${registry.totalFunctions} total)")
        println("=".repeat(60))
        println()

        registry.categories.entries.sortedBy { it.key }.forEach { (category, funcs) ->
            println("$category (${funcs.size}):")
            funcs.sortedBy { it.name }.forEach { func ->
                println("  ${func.name.padEnd(20)} - ${func.description.take(50)}")
            }
            println()
        }

        println("[Use 'utlx functions info <name>' for detailed information]")
        println("[Use 'utlx functions --format json' for machine-readable output]")
    }

    private fun printDetailedInfo(func: FunctionInfo) {
        println()
        println("${func.name} - ${func.description}")
        println("=".repeat(60))
        println("Category: ${func.category}")
        if (func.tags.isNotEmpty()) {
            println("Tags: ${func.tags.joinToString(", ")}")
        }
        println()

        println("Signature:")
        println("  ${func.signature}")
        println()

        if (func.parameters.isNotEmpty()) {
            println("Parameters:")
            func.parameters.forEach { param ->
                println("  ${param.name}: ${param.type}")
                println("    ${param.description}")
            }
            println()
        }

        val returns = func.returns
        if (returns != null) {
            println("Returns:")
            println("  ${returns.type} - ${returns.description}")
            println()
        }

        if (func.examples.isNotEmpty()) {
            println("Examples:")
            func.examples.forEach { example ->
                println("  $example")
            }
            println()
        }

        val notes = func.notes
        if (notes != null && notes.isNotBlank()) {
            println("Notes:")
            println("  $notes")
            println()
        }

        if (func.seeAlso.isNotEmpty()) {
            println("See Also: ${func.seeAlso.joinToString(", ")}")
            println()
        }

        if (func.deprecated) {
            println("⚠️  DEPRECATED")
            if (func.deprecationMessage != null) {
                println("  ${func.deprecationMessage}")
            }
            println()
        }
    }

    private fun printJson(data: Any, prettyPrint: Boolean) {
        val mapper = ObjectMapper().registerModule(KotlinModule())
        if (prettyPrint) {
            println(mapper.writerWithDefaultPrettyPrinter().writeValueAsString(data))
        } else {
            println(mapper.writeValueAsString(data))
        }
    }

    private fun printYaml(data: Any) {
        val mapper = ObjectMapper(YAMLFactory()).registerModule(KotlinModule())
        println(mapper.writeValueAsString(data))
    }

    private fun printUsage() {
        println("""
            |UTL-X Functions Command - Explore standard library functions
            |
            |Usage: utlx functions [command] [options]
            |
            |Commands:
            |  list                  List all functions (default)
            |  search <query>        Search functions by name/description/tag
            |  info <name>           Show detailed information about a function
            |  stats                 Show function statistics
            |  categories            List all function categories
            |
            |Options:
            |  --format <fmt>        Output format: text, json, json-compact, yaml
            |  --category <cat>      Filter by category (with list command)
            |  --detailed            Show detailed information (with list command)
            |  --help                Show this help message
            |
            |Examples:
            |  utlx functions                           # List all functions
            |  utlx functions search xml                # Find XML-related functions
            |  utlx functions info map                  # Show details about map function
            |  utlx functions stats                     # Show statistics
            |  utlx functions list --category Array     # List only Array functions
            |  utlx functions list --format json        # JSON output (for VS Code plugin)
            |
            |For VS Code / IDE Integration:
            |  utlx functions list --format json        # Get all functions as JSON
            |  utlx functions info map --format json    # Get function details as JSON
            |
        """.trimMargin())
    }
}
