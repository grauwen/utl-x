// modules/cli/src/main/kotlin/org/apache/utlx/cli/commands/TransformCommand.kt
package org.apache.utlx.cli.commands

import org.apache.utlx.cli.service.TransformationService
import org.apache.utlx.cli.capture.TestCaptureService
import org.apache.utlx.cli.CommandResult
import org.apache.utlx.core.debug.DebugConfig
import java.io.File

/**
 * Transform command - CLI wrapper for TransformationService
 * Handles file I/O and delegates transformation logic to TransformationService
 *
 * Usage:
 *   utlx transform <input-file> <script-file> [options]
 *   utlx transform <script-file> [options]  # reads from stdin
 */
object TransformCommand {

    // Create service instance
    private val transformationService = TransformationService()

    data class TransformOptions(
        val namedInputs: Map<String, File> = emptyMap(),        // Named inputs: input1=file1.xml, input2=file2.json
        val namedOutputs: Map<String, File> = emptyMap(),       // Named outputs: summary=out.json, details=out.xml
        val scriptFile: File,
        val inputFormat: String? = null,
        val outputFormat: String? = null,
        val verbose: Boolean = false,
        val pretty: Boolean = true,
        val captureEnabled: Boolean? = null,  // null = use config, true = force enable, false = force disable
        val debugLevel: DebugConfig.LogLevel? = null,  // Global debug level
        val debugComponents: Set<DebugConfig.Component> = emptySet(),  // Component-specific debug
        val strictTypes: Boolean = false  // Enforce type checking (fail on type errors)
    ) {
        // Backward compatibility properties
        val inputFile: File? get() = namedInputs["input"] ?: namedInputs.values.firstOrNull()
        val outputFile: File? get() = namedOutputs["output"] ?: namedOutputs.values.firstOrNull()
        val hasMultipleInputs: Boolean get() = namedInputs.size > 1
        val hasMultipleOutputs: Boolean get() = namedOutputs.size > 1
    }
    
    fun execute(args: Array<String>): CommandResult {
        val options = try {
            parseOptions(args)
        } catch (e: IllegalStateException) {
            // Special case: --help was requested
            if (e.message == "HELP_REQUESTED") {
                return CommandResult.Success
            }
            return CommandResult.Failure(e.message ?: "Unknown error", 1)
        } catch (e: IllegalArgumentException) {
            // Argument parsing errors (already printed to stderr)
            return CommandResult.Failure(e.message ?: "Invalid arguments", 1)
        }

        // Apply debug settings from CLI flags
        options.debugLevel?.let { level ->
            DebugConfig.setGlobalLogLevel(level)
        }
        options.debugComponents.forEach { component ->
            DebugConfig.enableComponent(component)
        }

        if (options.verbose) {
            println("UTL-X Transform")
            println("Script: ${options.scriptFile.absolutePath}")
            options.inputFile?.let { println("Input: ${it.absolutePath}") }
            options.outputFile?.let { println("Output: ${it.absolutePath}") }
        }

        // Track execution time for capture
        val startTime = System.currentTimeMillis()
        var captureSuccess = false
        var captureError: String? = null
        var captureOutputData = ""

        try {
            // Step 1: Read script file
            val scriptContent = options.scriptFile.readText()

            // Step 2: Read input files and create InputData map
            val inputs = if (options.namedInputs.isNotEmpty()) {
                // Named inputs from --input flags
                options.namedInputs.mapValues { (name, file) ->
                    val inputData = file.readText()
                    // Only use detectFormat if user explicitly didn't specify format
                    // Otherwise let TransformationService auto-detect from header
                    val inputFormat = options.inputFormat

                    if (options.verbose && inputFormat != null) {
                        println("Input '$name' format: $inputFormat (from CLI option)")
                    }

                    TransformationService.InputData(
                        content = inputData,
                        format = inputFormat  // null = auto-detect from header in TransformationService
                    )
                }
            } else {
                // No named inputs - read from stdin (backward compat)
                val inputData = readStdin()
                val inputFormat = options.inputFormat

                if (options.verbose && inputFormat != null) {
                    println("Input format: $inputFormat (from CLI option)")
                }

                mapOf("input" to TransformationService.InputData(
                    content = inputData,
                    format = inputFormat  // null = auto-detect from header in TransformationService
                ))
            }

            // Step 3: Call TransformationService
            val serviceOptions = TransformationService.TransformOptions(
                verbose = options.verbose,
                pretty = options.pretty,
                strictTypes = options.strictTypes,
                overrideOutputFormat = options.outputFormat
            )

            val (outputData, outputFormat) = transformationService.transform(scriptContent, inputs, serviceOptions)
            captureOutputData = outputData
            captureSuccess = true

            if (options.verbose) {
                println("Output format: $outputFormat")
            }

            // Write output
            val outputFilePath = options.outputFile
            if (outputFilePath != null) {
                outputFilePath.writeText(outputData)
                if (options.verbose) {
                    println("✓ Transformation complete: ${outputFilePath.absolutePath}")
                }
            } else {
                println(outputData)
            }

            // Step 4: Capture successful execution (for single input only)
            val durationMs = System.currentTimeMillis() - startTime
            if (!options.hasMultipleInputs) {
                val primaryInputData = inputs.values.firstOrNull()
                val captureInputFormat = primaryInputData?.format ?: "json"

                TestCaptureService.captureExecution(
                    transformation = scriptContent,
                    inputData = primaryInputData?.content ?: "",
                    inputFormat = captureInputFormat,
                    outputData = outputData,
                    outputFormat = outputFormat,
                    success = true,
                    error = null,
                    durationMs = durationMs,
                    scriptFile = options.scriptFile,
                    overrideEnabled = options.captureEnabled
                )
            }

            return CommandResult.Success

        } catch (e: Exception) {
            // Capture failed execution
            captureError = e.message ?: "Unknown error"
            val durationMs = System.currentTimeMillis() - startTime

            // Try to capture the failure (only for single input)
            if (!options.hasMultipleInputs) {
                try {
                    val scriptContent = options.scriptFile.readText()
                    val inputFilePath = options.inputFile
                    val inputData = if (inputFilePath != null) {
                        inputFilePath.readText()
                    } else {
                        "" // Can't capture stdin after error
                    }
                    val inputFormat = options.inputFormat ?: "json"

                    TestCaptureService.captureExecution(
                        transformation = scriptContent,
                        inputData = inputData,
                        inputFormat = inputFormat,
                        outputData = captureError,
                        outputFormat = options.outputFormat ?: inputFormat,
                    success = false,
                    error = captureError,
                    durationMs = durationMs,
                    scriptFile = options.scriptFile,
                        overrideEnabled = options.captureEnabled
                    )
                } catch (captureException: Exception) {
                    // Silently fail capture on error
                    if (options.verbose) {
                        System.err.println("  [Capture] Failed to capture error: ${captureException.message}")
                    }
                }
            }

            // Return failure with error message
            return CommandResult.Failure(e.message ?: "Transformation failed", 1)
        }
    }
    
    private fun detectFormat(data: String, extension: String?): String {
        // Try extension first
        extension?.lowercase()?.let {
            if (it in listOf("xml", "json", "csv", "yaml", "yml", "xsd", "jsch", "avro", "avsc", "proto")) {
                return when (it) {
                    "yml" -> "yaml"
                    "jsch" -> "jsch"  // JSON Schema files
                    "avsc" -> "avro"  // Avro schema files (.avsc extension)
                    "proto" -> "proto"  // Protocol Buffers schema files
                    else -> it
                }
            }
        }
        
        // Auto-detect from content
        val trimmed = data.trim()
        return when {
            trimmed.startsWith("<") -> "xml"
            trimmed.startsWith("{") || trimmed.startsWith("[") || trimmed.startsWith("\"") -> "json"
            trimmed.contains("---") || trimmed.contains(":") && !trimmed.contains(",") -> "yaml"
            trimmed.contains(",") && !trimmed.startsWith("<") -> "csv"
            else -> {
                System.err.println("Warning: Could not detect format, assuming JSON")
                "json"
            }
        }
    }
    
    private fun readStdin(): String {
        return generateSequence { readLine() }.joinToString("\n")
    }
    
    private fun parseOptions(args: Array<String>): TransformOptions {
        if (args.isEmpty()) {
            printUsage()
            throw IllegalArgumentException("No arguments provided")
        }

        val namedInputs = mutableMapOf<String, File>()
        val namedOutputs = mutableMapOf<String, File>()
        var scriptFile: File? = null
        var inputFormat: String? = null
        var outputFormat: String? = null
        var verbose = false
        var pretty = true
        var captureEnabled: Boolean? = null
        var debugLevel: DebugConfig.LogLevel? = null
        val debugComponents = mutableSetOf<DebugConfig.Component>()
        var strictTypes = false

        var i = 0
        while (i < args.size) {
            when (args[i]) {
                "-o", "--output" -> {
                    val arg = args[++i]
                    // Parse: either "file.xml" or "name=file.xml"
                    if (arg.contains("=")) {
                        val (name, path) = arg.split("=", limit = 2)
                        namedOutputs[name] = File(path)
                    } else {
                        namedOutputs["output"] = File(arg)
                    }
                }
                "-i", "--input" -> {
                    val arg = args[++i]
                    // Parse: either "file.xml" or "name=file.xml"
                    if (arg.contains("=")) {
                        val (name, path) = arg.split("=", limit = 2)
                        namedInputs[name] = File(path)
                    } else {
                        namedInputs["input"] = File(arg)
                    }
                }
                "--input-format" -> {
                    inputFormat = args[++i]
                }
                "--output-format" -> {
                    outputFormat = args[++i]
                }
                "-v", "--verbose" -> {
                    verbose = true
                }
                "--no-pretty" -> {
                    pretty = false
                }
                "--strict-types" -> {
                    strictTypes = true
                }
                "--capture" -> {
                    captureEnabled = true
                }
                "--no-capture" -> {
                    captureEnabled = false
                }
                "--debug" -> {
                    debugLevel = DebugConfig.LogLevel.DEBUG
                }
                "--debug-parser" -> {
                    debugComponents.add(DebugConfig.Component.PARSER)
                }
                "--debug-lexer" -> {
                    debugComponents.add(DebugConfig.Component.LEXER)
                }
                "--debug-interpreter" -> {
                    debugComponents.add(DebugConfig.Component.INTERPRETER)
                }
                "--debug-types" -> {
                    debugComponents.add(DebugConfig.Component.TYPE_SYSTEM)
                }
                "--debug-all" -> {
                    debugLevel = DebugConfig.LogLevel.DEBUG
                }
                "--trace" -> {
                    debugLevel = DebugConfig.LogLevel.TRACE
                }
                "-h", "--help" -> {
                    printUsage()
                    // Special case: help is a successful operation
                    throw IllegalStateException("HELP_REQUESTED")
                }
                else -> {
                    if (!args[i].startsWith("-")) {
                        if (scriptFile == null) {
                            scriptFile = File(args[i])
                        } else if (namedInputs.isEmpty()) {
                            // Positional input file (backward compat)
                            namedInputs["input"] = File(args[i])
                        }
                    } else {
                        System.err.println("Unknown option: ${args[i]}")
                        printUsage()
                        throw IllegalArgumentException("Unknown option: ${args[i]}")
                    }
                }
            }
            i++
        }

        if (scriptFile == null) {
            System.err.println("Error: Script file is required")
            printUsage()
            throw IllegalArgumentException("Script file is required")
        }

        if (!scriptFile.exists()) {
            System.err.println("Error: Script file not found: ${scriptFile.absolutePath}")
            throw IllegalArgumentException("Script file not found: ${scriptFile.absolutePath}")
        }

        // Validate all input files exist
        namedInputs.forEach { (name, file) ->
            if (!file.exists()) {
                System.err.println("Error: Input file not found: ${file.absolutePath} (input: $name)")
                throw IllegalArgumentException("Input file not found: ${file.absolutePath}")
            }
        }

        return TransformOptions(
            namedInputs = namedInputs,
            namedOutputs = namedOutputs,
            scriptFile = scriptFile,
            inputFormat = inputFormat,
            outputFormat = outputFormat,
            verbose = verbose,
            pretty = pretty,
            captureEnabled = captureEnabled,
            debugLevel = debugLevel,
            debugComponents = debugComponents,
            strictTypes = strictTypes
        )
    }
    
    private fun printUsage() {
        println("""
            |Transform data using UTL-X scripts
            |
            |Usage:
            |  utlx transform <script-file> [input-file] [options]
            |  utlx transform <script-file> [options] < input-file
            |  utlx transform <script-file> --input input1=file1.xml --input input2=file2.json [options]
            |
            |Arguments:
            |  input-file      Input data file (if not provided, reads from stdin)
            |  script-file     UTL-X transformation script (.utlx)
            |
            |Options:
            |  -o, --output FILE           Write output to FILE (default: stdout)
            |      --output name=FILE      Named output for multi-output transformations
            |  -i, --input FILE            Read input from FILE
            |      --input name=FILE       Named input for multi-input transformations
            |  --input-format FORMAT       Force input format (xml, json, csv, yaml)
            |  --output-format FORMAT      Force output format (xml, json, csv, yaml)
            |  -v, --verbose               Enable verbose output
            |  --no-pretty                 Disable pretty-printing
            |  --strict-types              Enforce type checking (fail on type errors)
            |  --capture                   Force enable test capture (overrides config)
            |  --no-capture                Force disable test capture (overrides config)
            |  -h, --help                  Show this help message
            |
            |Debug Options:
            |  --debug                     Enable DEBUG level logging for all components
            |  --debug-parser              Enable DEBUG logging for parser only
            |  --debug-lexer               Enable DEBUG logging for lexer only
            |  --debug-interpreter         Enable DEBUG logging for interpreter only
            |  --debug-types               Enable DEBUG logging for type system only
            |  --debug-all                 Enable DEBUG logging for all components (same as --debug)
            |  --trace                     Enable TRACE level logging (most verbose)
            |
            |Examples:
            |  # Single input/output (backward compatible)
            |  utlx transform script.utlx input.xml -o output.json
            |
            |  # Multiple named inputs
            |  utlx transform script.utlx --input input1=customer.xml --input input2=orders.json -o output.xml
            |
            |  # Multiple named outputs
            |  utlx transform script.utlx -i data.xml --output summary=sum.json --output details=det.xml
        """.trimMargin())
    }
}
