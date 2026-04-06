// modules/cli/src/main/kotlin/org/apache/utlx/cli/commands/TransformCommand.kt
package org.apache.utlx.cli.commands

import org.apache.utlx.cli.service.TransformationService
import org.apache.utlx.cli.capture.TestCaptureService
import org.apache.utlx.core.debug.DebugConfig
import java.io.File
import kotlin.system.exitProcess

/**
 * Transform command - CLI wrapper for TransformationService
 * Handles file I/O and delegates transformation logic to TransformationService
 *
 * Usage:
 *   utlx transform <input-file> <script-file> [options]
 *   utlx transform <script-file> [options]  # reads from stdin
 *   cat data.xml | utlx                     # identity mode: auto-detect input, smart flip output
 *   cat data.xml | utlx --to json           # identity mode with explicit output format
 */
object TransformCommand {

    // Create service instance
    private val transformationService = TransformationService()

    data class TransformOptions(
        val namedInputs: Map<String, File> = emptyMap(),        // Named inputs: input1=file1.xml, input2=file2.json
        val namedOutputs: Map<String, File> = emptyMap(),       // Named outputs: summary=out.json, details=out.xml
        val scriptFile: File? = null,                           // null = identity mode (passthrough)
        val inputFormat: String? = null,
        val outputFormat: String? = null,
        val verbose: Boolean = false,
        val pretty: Boolean = true,
        val captureEnabled: Boolean? = null,  // null = use config, true = force enable, false = force disable
        val debugLevel: DebugConfig.LogLevel? = null,  // Global debug level
        val debugComponents: Set<DebugConfig.Component> = emptySet(),  // Component-specific debug
        val strictTypes: Boolean = false,  // Enforce type checking (fail on type errors)
        val identityMode: Boolean = false  // true = no script, passthrough with smart format flip
    ) {
        // Backward compatibility properties
        val inputFile: File? get() = namedInputs["input"] ?: namedInputs.values.firstOrNull()
        val outputFile: File? get() = namedOutputs["output"] ?: namedOutputs.values.firstOrNull()
        val hasMultipleInputs: Boolean get() = namedInputs.size > 1
        val hasMultipleOutputs: Boolean get() = namedOutputs.size > 1
    }

    /**
     * Detect format from data content (same logic as TransformationService auto-detect)
     */
    private fun detectFormatFromContent(data: String): String {
        val trimmed = data.trim()
        return when {
            trimmed.startsWith("<") -> "xml"
            trimmed.startsWith("{") || trimmed.startsWith("[") -> "json"
            trimmed.startsWith("---") || trimmed.contains(":\n") || trimmed.contains(": ") -> "yaml"
            trimmed.contains(",") && trimmed.lines().size > 1 -> {
                val lines = trimmed.lines().filter { it.isNotBlank() }
                val firstLineCommas = lines.firstOrNull()?.count { it == ',' } ?: 0
                if (firstLineCommas > 0 && lines.take(3).all { it.count { c -> c == ',' } == firstLineCommas }) {
                    "csv"
                } else {
                    "json"
                }
            }
            else -> "json"
        }
    }

    /**
     * Smart format flip: choose the most useful output format based on detected input.
     * XML↔JSON is the #1 use case; everything else defaults to JSON.
     */
    private fun inferOutputFormat(detectedInputFormat: String): String {
        return when (detectedInputFormat) {
            "xml"  -> "json"
            "json" -> "xml"
            else   -> "json"
        }
    }

    fun execute(args: Array<String>, identityMode: Boolean = false) {
        val options = parseOptions(args, allowIdentityMode = identityMode)

        // Apply debug settings from CLI flags
        options.debugLevel?.let { level ->
            DebugConfig.setGlobalLogLevel(level)
        }
        options.debugComponents.forEach { component ->
            DebugConfig.enableComponent(component)
        }

        if (options.verbose) {
            if (options.identityMode) {
                println("UTL-X Identity Transform (passthrough with format conversion)")
            } else {
                println("UTL-X Transform")
                println("Script: ${options.scriptFile!!.absolutePath}")
            }
            options.inputFile?.let { println("Input: ${it.absolutePath}") }
            options.outputFile?.let { println("Output: ${it.absolutePath}") }
        }

        // Track execution time for capture
        val startTime = System.currentTimeMillis()
        var captureSuccess = false
        var captureError: String? = null
        var captureOutputData = ""

        try {
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

            // Step 1: Determine script content (file or synthesized identity)
            val scriptContent: String
            val effectiveOutputFormat: String?

            if (options.identityMode) {
                // Identity mode: synthesize a passthrough script with smart format flip
                val primaryInput = inputs.values.first()
                val detectedInputFormat = options.inputFormat
                    ?: detectFormatFromContent(primaryInput.content)

                // User-specified output format wins; otherwise smart flip
                effectiveOutputFormat = options.outputFormat
                    ?: inferOutputFormat(detectedInputFormat)

                if (options.verbose) {
                    println("Detected input format: $detectedInputFormat")
                    println("Output format: $effectiveOutputFormat" +
                        if (options.outputFormat != null) " (explicit)" else " (smart flip)")
                }

                scriptContent = """%utlx 1.0
input auto
output $effectiveOutputFormat
---
${"$"}input"""
            } else {
                // Normal mode: read script from file
                scriptContent = options.scriptFile!!.readText()
                effectiveOutputFormat = options.outputFormat
            }

            // Step 3: Call TransformationService
            val serviceOptions = TransformationService.TransformOptions(
                verbose = options.verbose,
                pretty = options.pretty,
                strictTypes = options.strictTypes,
                overrideOutputFormat = effectiveOutputFormat
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

            // Step 4: Capture successful execution (for single input only, skip identity mode)
            val durationMs = System.currentTimeMillis() - startTime
            if (!options.hasMultipleInputs && !options.identityMode) {
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
                    scriptFile = options.scriptFile!!,
                    overrideEnabled = options.captureEnabled
                )
            }

        } catch (e: Exception) {
            // Capture failed execution
            captureError = e.message ?: "Unknown error"
            val durationMs = System.currentTimeMillis() - startTime

            // Try to capture the failure (only for single input, skip identity mode)
            if (!options.hasMultipleInputs && !options.identityMode && options.scriptFile != null) {
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
                        scriptFile = options.scriptFile!!,
                        overrideEnabled = options.captureEnabled
                    )
                } catch (captureException: Exception) {
                    // Silently fail capture on error
                    if (options.verbose) {
                        System.err.println("  [Capture] Failed to capture error: ${captureException.message}")
                    }
                }
            }

            // Re-throw the original error
            throw e
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

    /**
     * Parse options - supports both normal mode (with script file) and identity mode (no script).
     * @param allowIdentityMode if true, missing script file triggers identity mode instead of error.
     *                          Set to true when invoked from Main.kt's implicit routing.
     */
    fun parseOptions(args: Array<String>, allowIdentityMode: Boolean = false): TransformOptions {
        // Identity mode: no args at all means passthrough (read stdin, smart flip output)
        if (args.isEmpty() && allowIdentityMode) {
            return TransformOptions(identityMode = true)
        }
        if (args.isEmpty()) {
            printUsage()
            exitProcess(1)
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
                "--input-format", "--from" -> {
                    inputFormat = args[++i]
                }
                "--output-format", "--to" -> {
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
                    exitProcess(0)
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
                        exitProcess(1)
                    }
                }
            }
            i++
        }

        // If no script file: identity mode (when allowed) or error
        if (scriptFile == null) {
            if (allowIdentityMode) {
                return TransformOptions(
                    namedInputs = namedInputs,
                    namedOutputs = namedOutputs,
                    scriptFile = null,
                    inputFormat = inputFormat,
                    outputFormat = outputFormat,
                    verbose = verbose,
                    pretty = pretty,
                    captureEnabled = captureEnabled,
                    debugLevel = debugLevel,
                    debugComponents = debugComponents,
                    strictTypes = strictTypes,
                    identityMode = true
                )
            }
            System.err.println("Error: Script file is required")
            printUsage()
            exitProcess(1)
        }

        if (!scriptFile.exists()) {
            System.err.println("Error: Script file not found: ${scriptFile.absolutePath}")
            exitProcess(1)
        }

        // Validate all input files exist
        namedInputs.forEach { (name, file) ->
            if (!file.exists()) {
                System.err.println("Error: Input file not found: ${file.absolutePath} (input: $name)")
                exitProcess(1)
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
            |Identity mode (no script, format conversion):
            |  cat data.xml | utlx                        Auto-detect XML, output JSON (smart flip)
            |  cat data.json | utlx                       Auto-detect JSON, output XML (smart flip)
            |  cat data.csv | utlx                        Auto-detect CSV, output JSON
            |  cat data.xml | utlx --to yaml              Override smart flip with explicit format
            |  cat data.csv | utlx --from csv --to xml    Explicit input and output formats
            |
            |Arguments:
            |  input-file      Input data file (if not provided, reads from stdin)
            |  script-file     UTL-X transformation script (.utlx)
            |                  If omitted, identity transform is used (passthrough with format conversion)
            |
            |Options:
            |  -o, --output FILE           Write output to FILE (default: stdout)
            |      --output name=FILE      Named output for multi-output transformations
            |  -i, --input FILE            Read input from FILE
            |      --input name=FILE       Named input for multi-input transformations
            |  --input-format FORMAT       Force input format (xml, json, csv, yaml)
            |  --from FORMAT               Alias for --input-format
            |  --output-format FORMAT      Force output format (xml, json, csv, yaml)
            |  --to FORMAT                 Alias for --output-format
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
            |Identity Mode (Smart Format Flip):
            |  When no script file is provided, utlx performs a passthrough (identity) transform.
            |  The output format is automatically chosen as the "opposite" of the detected input:
            |    XML  -> JSON     (most common conversion)
            |    JSON -> XML      (most common conversion)
            |    CSV  -> JSON     (JSON is universal interchange)
            |    YAML -> JSON     (JSON is universal interchange)
            |  Use --to to override the smart default.
            |
            |Examples:
            |  # Identity mode: format conversion without a script
            |  cat data.xml | utlx                                    # XML to JSON
            |  cat data.json | utlx                                   # JSON to XML
            |  cat data.csv | utlx --to yaml                          # CSV to YAML
            |
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
