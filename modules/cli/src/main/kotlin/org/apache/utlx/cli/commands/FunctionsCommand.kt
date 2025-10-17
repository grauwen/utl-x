// modules/cli/src/main/kotlin/org/apache/utlx/cli/commands/FunctionsCommand.kt
package org.apache.utlx.cli.commands

import org.apache.utlx.core.interpreter.StandardLibraryRegistry

/**
 * Functions command - lists available stdlib functions
 * 
 * Usage:
 *   utlx functions [options]
 */
object FunctionsCommand {
    
    fun execute(args: Array<String>) {
        val options = parseOptions(args)
        val registry = StandardLibraryRegistry()
        
        when {
            options.module != null -> {
                showFunctionsByModule(registry, options.module)
            }
            options.search != null -> {
                searchFunctions(registry, options.search)
            }
            options.detailed -> {
                showDetailedFunctions(registry)
            }
            else -> {
                showAllFunctions(registry)
            }
        }
    }
    
    private fun showAllFunctions(registry: StandardLibraryRegistry) {
        val functions = registry.getFunctionNames().sorted()
        
        println("UTL-X Standard Library Functions (${functions.size} total)")
        println("=" * 50)
        
        val modules = functions.map { name ->
            registry.getFunctionInfo(name)?.module ?: "unknown"
        }.distinct().sorted()
        
        modules.forEach { module ->
            val moduleFunctions = functions.filter { 
                registry.getFunctionInfo(it)?.module == module 
            }
            
            println("\n$module (${moduleFunctions.size}):")
            moduleFunctions.chunked(5).forEach { chunk ->
                println("  " + chunk.joinToString(", ") { it.padEnd(16) })
            }
        }
        
        println("\nUse 'utlx functions --module <name>' to see functions in a specific module")
        println("Use 'utlx functions --detailed' to see descriptions")
    }
    
    private fun showFunctionsByModule(registry: StandardLibraryRegistry, moduleName: String) {
        val functions = registry.getFunctionsByModule(moduleName)
        
        if (functions.isEmpty()) {
            println("No functions found in module: $moduleName")
            println("\nAvailable modules:")
            val allModules = registry.getFunctionNames()
                .mapNotNull { registry.getFunctionInfo(it)?.module }
                .distinct()
                .sorted()
            allModules.forEach { println("  $it") }
            return
        }
        
        println("Functions in module '$moduleName' (${functions.size} total)")
        println("=" * 50)
        
        functions.sortedBy { it.name }.forEach { func ->
            println("${func.name.padEnd(20)} - ${func.description}")
        }
    }
    
    private fun searchFunctions(registry: StandardLibraryRegistry, searchTerm: String) {
        val allFunctions = registry.getFunctionNames()
            .mapNotNull { registry.getFunctionInfo(it) }
        
        val matches = allFunctions.filter { func ->
            func.name.contains(searchTerm, ignoreCase = true) ||
            func.description.contains(searchTerm, ignoreCase = true)
        }
        
        if (matches.isEmpty()) {
            println("No functions found matching: $searchTerm")
            return
        }
        
        println("Functions matching '$searchTerm' (${matches.size} found)")
        println("=" * 50)
        
        matches.sortedBy { it.name }.forEach { func ->
            println("${func.name.padEnd(20)} [${func.module}] - ${func.description}")
        }
    }
    
    private fun showDetailedFunctions(registry: StandardLibraryRegistry) {
        val functions = registry.getFunctionNames()
            .mapNotNull { registry.getFunctionInfo(it) }
            .sortedBy { it.name }
        
        println("UTL-X Standard Library Functions - Detailed View")
        println("=" * 60)
        
        val grouped = functions.groupBy { it.module }
        
        grouped.toSortedMap().forEach { (module, funcs) ->
            println("\n## $module Module")
            println("-" * 30)
            
            funcs.forEach { func ->
                println("**${func.name}**")
                println("  Description: ${func.description}")
                println()
            }
        }
    }
    
    data class FunctionsOptions(
        val module: String? = null,
        val search: String? = null,
        val detailed: Boolean = false,
        val help: Boolean = false
    )
    
    private fun parseOptions(args: Array<String>): FunctionsOptions {
        var module: String? = null
        var search: String? = null
        var detailed = false
        var help = false
        
        var i = 0
        while (i < args.size) {
            when (args[i]) {
                "--module", "-m" -> {
                    if (i + 1 >= args.size) {
                        println("Error: --module requires a module name")
                        printUsage()
                        kotlin.system.exitProcess(1)
                    }
                    module = args[++i]
                }
                "--search", "-s" -> {
                    if (i + 1 >= args.size) {
                        println("Error: --search requires a search term")
                        printUsage()
                        kotlin.system.exitProcess(1)
                    }
                    search = args[++i]
                }
                "--detailed", "-d" -> {
                    detailed = true
                }
                "--help", "-h" -> {
                    help = true
                }
                else -> {
                    println("Unknown option: ${args[i]}")
                    printUsage()
                    kotlin.system.exitProcess(1)
                }
            }
            i++
        }
        
        if (help) {
            printUsage()
            kotlin.system.exitProcess(0)
        }
        
        return FunctionsOptions(module, search, detailed, help)
    }
    
    private fun printUsage() {
        println("""
            |List and explore UTL-X standard library functions
            |
            |Usage:
            |  utlx functions [options]
            |
            |Options:
            |  -m, --module NAME       Show functions in specific module
            |  -s, --search TERM       Search functions by name or description
            |  -d, --detailed          Show detailed descriptions
            |  -h, --help              Show this help message
            |
            |Examples:
            |  # List all functions grouped by module
            |  utlx functions
            |
            |  # Show functions in the 'string' module
            |  utlx functions --module string
            |
            |  # Search for functions related to 'date'
            |  utlx functions --search date
            |
            |  # Show detailed descriptions
            |  utlx functions --detailed
        """.trimMargin())
    }
}

private operator fun String.times(n: Int): String = this.repeat(n)