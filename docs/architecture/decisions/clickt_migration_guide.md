# Migrating UTL-X CLI to Clikt - Step-by-Step Guide

If you decide to migrate from manual implementation to Clikt, here's exactly how to do it.

## Step 1: Add Dependency

### modules/cli/build.gradle.kts
```kotlin
dependencies {
    // Add Clikt
    implementation("com.github.ajalt.clikt:clikt:4.2.1")
    
    // Existing dependencies
    implementation(project(":modules:core"))
    implementation(project(":formats:xml"))
    implementation(project(":formats:json"))
    implementation(project(":formats:csv"))
    
    implementation(kotlin("stdlib"))
    implementation(kotlin("reflect"))
    
    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.1")
}
```

## Step 2: Update Main.kt

### Before (Manual)
```kotlin
object Main {
    @JvmStatic
    fun main(args: Array<String>) {
        if (args.isEmpty()) {
            printUsage()
            exitProcess(0)
        }
        
        val command = args[0]
        val commandArgs = args.drop(1).toTypedArray()
        
        when (command.lowercase()) {
            "transform", "t" -> TransformCommand.execute(commandArgs)
            "validate", "v" -> ValidateCommand.execute(commandArgs)
            // ... etc
        }
    }
}
```

### After (Clikt)
```kotlin
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.options.versionOption

class UtlxCli : CliktCommand(
    name = "utlx",
    help = "UTL-X - Universal Transformation Language Extended"
) {
    init {
        versionOption("1.0.0-SNAPSHOT")
    }
    
    override fun run() = Unit
}

fun main(args: Array<String>) = UtlxCli()
    .subcommands(
        TransformCommand(),
        ValidateCommand(),
        FormatCommand(),
        CompileCommand(),
        MigrateCommand()
    )
    .main(args)
```

**Lines: 50 → 15** ✨

## Step 3: Update TransformCommand

### Before (Manual - 150 lines)
```kotlin
object TransformCommand {
    data class TransformOptions(
        val inputFile: File?,
        val scriptFile: File,
        val outputFile: File?,
        val inputFormat: String?,
        val outputFormat: String?,
        val verbose: Boolean,
        val pretty: Boolean
    )
    
    fun execute(args: Array<String>) {
        val options = parseOptions(args)
        // ... lots of manual parsing logic
    }
    
    private fun parseOptions(args: Array<String>): TransformOptions {
        var inputFile: File? = null
        var scriptFile: File? = null
        var outputFile: File? = null
        // ... 80 lines of manual parsing
    }
    
    private fun printUsage() {
        println("""
            |Transform data using UTL-X scripts
            |...
        """.trimMargin())
    }
}
```

### After (Clikt - 60 lines)
```kotlin
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.optional
import com.github.ajalt.clikt.parameters.options.*
import com.github.ajalt.clikt.parameters.types.choice
import com.github.ajalt.clikt.parameters.types.file

class TransformCommand : CliktCommand(
    name = "transform",
    help = """
        Transform data between formats using UTL-X scripts.
        
        Reads input data, applies transformation script, and outputs result.
    """.trimIndent()
) {
    // Arguments
    private val inputFile by argument("INPUT", "Input data file")
        .file(mustExist = true, canBeFile = true, canBeDir = false)
        .optional()
    
    private val scriptFile by argument("SCRIPT", "UTL-X transformation script")
        .file(mustExist = true, canBeFile = true, canBeDir = false)
    
    // Options
    private val outputFile by option("-o", "--output", help = "Output file")
        .file(canBeFile = true, canBeDir = false)
    
    private val inputFormat by option("--input-format", help = "Input format")
        .choice("xml", "json", "csv", "auto")
        .default("auto")
    
    private val outputFormat by option("--output-format", help = "Output format")
        .choice("xml", "json", "csv")
    
    private val verbose by option("-v", "--verbose", help = "Verbose output")
        .flag(default = false)
    
    private val noPretty by option("--no-pretty", help = "Disable pretty-printing")
        .flag(default = false)
    
    override fun run() {
        if (verbose) {
            echo("UTL-X Transform", err = false)
            echo("Script: ${scriptFile.absolutePath}")
            inputFile?.let { echo("Input: ${it.absolutePath}") }
        }
        
        // Read script
        val scriptContent = scriptFile.readText()
        val program = compileScript(scriptContent, verbose)
        
        // Read input
        val inputData = inputFile?.readText() ?: readStdin()
        val detectedFormat = inputFormat.ifEmpty { detectFormat(inputData, inputFile?.extension) }
        
        if (verbose) echo("Input format: $detectedFormat")
        
        // Transform
        val inputUDM = parseInput(inputData, detectedFormat)
        val interpreter = Interpreter()
        val outputUDM = interpreter.execute(program, inputUDM)
        
        // Output
        val outputFormatFinal = outputFormat ?: detectedFormat
        if (verbose) echo("Output format: $outputFormatFinal")
        
        val outputData = serializeOutput(outputUDM, outputFormatFinal, !noPretty)
        
        if (outputFile != null) {
            outputFile!!.writeText(outputData)
            if (verbose) echo("✓ Complete: ${outputFile!!.absolutePath}")
        } else {
            echo(outputData)
        }
    }
    
    // Helper methods (same as before)
    private fun compileScript(script: String, verbose: Boolean) = /* ... */
    private fun parseInput(data: String, format: String) = /* ... */
    private fun serializeOutput(udm: UDM, format: String, pretty: Boolean) = /* ... */
    private fun detectFormat(data: String, extension: String?) = /* ... */
    private fun readStdin() = /* ... */
}
```

**Benefits:**
- ✨ Automatic help generation
- ✨ Type-safe argument handling
- ✨ Built-in validation
- ✨ Better error messages
- ✨ 90 fewer lines of code

## Step 4: Update ValidateCommand

### Before (Manual - 80 lines)
```kotlin
object ValidateCommand {
    fun execute(args: Array<String>) {
        if (args.isEmpty()) {
            printUsage()
            exitProcess(1)
        }
        
        val verbose = args.contains("-v") || args.contains("--verbose")
        val strict = args.contains("--strict")
        
        val scriptFiles = args.filter { !it.startsWith("-") }.map { File(it) }
        // ... manual parsing and validation
    }
}
```

### After (Clikt - 40 lines)
```kotlin
class ValidateCommand : CliktCommand(
    name = "validate",
    help = "Validate UTL-X scripts for syntax and type errors"
) {
    private val scriptFiles by argument("SCRIPTS")
        .file(mustExist = true, canBeFile = true, canBeDir = false)
        .multiple(required = true)
    
    private val verbose by option("-v", "--verbose")
        .flag(default = false)
    
    private val strict
