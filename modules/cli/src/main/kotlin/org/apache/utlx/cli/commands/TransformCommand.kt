// modules/cli/src/main/kotlin/org/apache/utlx/cli/commands/TransformCommand.kt
package org.apache.utlx.cli.commands

import org.apache.utlx.core.lexer.Lexer
import org.apache.utlx.core.parser.Parser
import org.apache.utlx.core.types.TypeChecker
import org.apache.utlx.core.interpreter.Interpreter
import org.apache.utlx.core.udm.UDM
import org.apache.utlx.formats.xml.XMLParser
import org.apache.utlx.formats.xml.XMLSerializer
import org.apache.utlx.formats.json.JSONParser
import org.apache.utlx.formats.json.JSONSerializer
import org.apache.utlx.formats.csv.CSVParser
import org.apache.utlx.formats.csv.CSVSerializer
import org.apache.utlx.formats.csv.CSVDialect
import org.apache.utlx.formats.csv.RegionalFormat
import org.apache.utlx.formats.yaml.YAMLParser
import org.apache.utlx.formats.xsd.XSDParser
import org.apache.utlx.formats.xsd.XSDSerializer
import org.apache.utlx.formats.jsch.JSONSchemaParser
import org.apache.utlx.formats.jsch.JSONSchemaSerializer
import org.apache.utlx.formats.yaml.YAMLSerializer
import org.apache.utlx.formats.avro.AvroSchemaParser
import org.apache.utlx.formats.avro.AvroSchemaSerializer
import org.apache.utlx.formats.protobuf.ProtobufSchemaParser
import org.apache.utlx.formats.protobuf.ProtobufSchemaSerializer
import org.apache.utlx.stdlib.StandardLibrary
import org.apache.utlx.cli.capture.TestCaptureService
import org.apache.utlx.core.debug.DebugConfig
import java.io.File
import kotlin.system.exitProcess

/**
 * Transform command - converts data between formats using UTL-X scripts
 * 
 * Usage:
 *   utlx transform <input-file> <script-file> [options]
 *   utlx transform <script-file> [options]  # reads from stdin
 */
object TransformCommand {
    
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
    
    fun execute(args: Array<String>) {
        val options = parseOptions(args)

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
            // Read and compile the UTL-X script
            val scriptContent = options.scriptFile.readText()
            val program = compileScript(scriptContent, options)

            // Read and parse all input data files
            val namedInputsUDM = if (options.namedInputs.isNotEmpty()) {
                // Named inputs from --input flags
                options.namedInputs.mapValues { (name, file) ->
                    val inputData = file.readText()
                    val inputFormat = options.inputFormat ?: detectFormat(inputData, file.extension)

                    // Get format options from script header for this specific input
                    val inputSpec = program.header.inputs.find { it.first == name }?.second
                    val inputOptions = inputSpec?.options ?: emptyMap()

                    if (options.verbose) {
                        println("Input '$name' format: $inputFormat (from ${file.name})")
                    }

                    parseInput(inputData, inputFormat, inputOptions)
                }
            } else {
                // No named inputs - read from stdin (backward compat)
                val inputData = readStdin()

                // Priority: 1) CLI option, 2) script header, 3) auto-detect
                val scriptFormat = program.header.inputFormat.type.name.lowercase()
                val inputFormat = options.inputFormat
                    ?: if (scriptFormat != "auto") scriptFormat else detectFormat(inputData, null)

                if (options.verbose) {
                    println("Input format: $inputFormat (from stdin)")
                }

                val inputOptions = program.header.inputFormat.options
                val inputUDM = parseInput(inputData, inputFormat, inputOptions)
                mapOf("input" to inputUDM)
            }

            // Execute transformation using core Interpreter with dynamic stdlib loading
            val interpreter = Interpreter()

            val result = interpreter.execute(program, namedInputsUDM)
            val outputUDM = result.toUDM()

            // Detect or use specified output format
            // Priority: 1) CLI option, 2) script header, 3) default json
            val outputFormat = options.outputFormat ?: program.header.outputFormat.type.name.lowercase()

            if (options.verbose) {
                println("Output format: $outputFormat")
            }

            // Serialize output
            val outputData = serializeOutput(outputUDM, outputFormat, program.header.outputFormat, options.pretty)
            captureOutputData = outputData
            captureSuccess = true

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

            // Capture successful execution (for single input only)
            val durationMs = System.currentTimeMillis() - startTime
            if (!options.hasMultipleInputs) {
                val primaryInput = namedInputsUDM.values.firstOrNull()
                val captureInputFormat = options.inputFormat ?: detectFormat("", options.inputFile?.extension)

                TestCaptureService.captureExecution(
                    transformation = scriptContent,
                    inputData = options.inputFile?.readText() ?: "",
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

            // Re-throw the original error
            throw e
        }
    }
    
    private fun compileScript(script: String, options: TransformOptions): org.apache.utlx.core.ast.Program {
        try {
            if (options.verbose) println("Lexing...")
            val lexer = Lexer(script)
            val tokens = lexer.tokenize()

            if (options.verbose) println("Parsing...")
            val parser = Parser(tokens)
            val parseResult = parser.parse()

            when (parseResult) {
                is org.apache.utlx.core.parser.ParseResult.Success -> {
                    if (options.verbose) println("✓ Parsing successful")

                    // Type checking
                    if (options.verbose) println("Type checking...")
                    val stdlib = org.apache.utlx.core.types.StandardLibrary()
                    val typeChecker = TypeChecker(stdlib)
                    val typeCheckResult = typeChecker.check(parseResult.program)

                    when (typeCheckResult) {
                        is org.apache.utlx.core.types.TypeCheckResult.Success -> {
                            if (options.verbose) println("✓ Type checking successful (inferred type: ${typeCheckResult.type})")
                            return parseResult.program
                        }
                        is org.apache.utlx.core.types.TypeCheckResult.Failure -> {
                            if (options.strictTypes) {
                                // Strict mode: type errors cause failure
                                System.err.println("Type errors:")
                                typeCheckResult.errors.forEach { error ->
                                    System.err.println("  ${error.message} at ${error.location}")
                                    if (error.expected != null && error.actual != null) {
                                        System.err.println("    Expected: ${error.expected}")
                                        System.err.println("    Actual: ${error.actual}")
                                    }
                                }
                                exitProcess(1)
                            } else {
                                // Default: print type errors as warnings (verbose mode only)
                                // Type checking is opt-in via type annotations
                                if (options.verbose) {
                                    System.err.println("Type warnings:")
                                    typeCheckResult.errors.forEach { error ->
                                        System.err.println("  ${error.message} at ${error.location}")
                                    }
                                }
                                return parseResult.program
                            }
                        }
                    }
                }
                is org.apache.utlx.core.parser.ParseResult.Failure -> {
                    System.err.println("Parse errors:")
                    parseResult.errors.forEach { error ->
                        System.err.println("  ${error.message} at ${error.location}")
                    }
                    exitProcess(1)
                }
            }
        } catch (e: Exception) {
            System.err.println("Error compiling script: ${e.message}")
            if (options.verbose) {
                e.printStackTrace()
            }
            exitProcess(1)
        }
    }
    
    private fun parseInput(data: String, format: String, options: Map<String, Any> = emptyMap()): UDM {
        return try {
            when (format.lowercase()) {
                "xml" -> {
                    // Extract array hints from options (elements that should always be arrays)
                    val arrayHints = (options["arrays"] as? List<*>)
                        ?.mapNotNull { it as? String }
                        ?.toSet()
                        ?: emptySet()
                    XMLParser(data, arrayHints).parse()
                }
                "json" -> {
                    JSONParser(data).parse()
                }
                "csv" -> {
                    // Extract CSV options
                    val delimiter = (options["delimiter"] as? String)?.firstOrNull() ?: ','
                    val headers = (options["headers"] as? Boolean) ?: true

                    val dialect = CSVDialect(delimiter = delimiter)
                    CSVParser(data, dialect).parse(hasHeaders = headers)
                }
                "yaml", "yml" -> {
                    YAMLParser().parse(data)
                }
                "xsd" -> {
                    // Extract array hints from options (same as XML)
                    val arrayHints = (options["arrays"] as? List<*>)
                        ?.mapNotNull { it as? String }
                        ?.toSet()
                        ?: emptySet()
                    val result = XSDParser(data, arrayHints).parse()
                    result
                }
                "jsch" -> {
                    JSONSchemaParser(data).parse()
                }
                "avro" -> {
                    AvroSchemaParser().parse(data)
                }
                "proto" -> {
                    ProtobufSchemaParser().parse(data)
                }
                else -> throw IllegalArgumentException("Unsupported input format: $format")
            }
        } catch (e: Exception) {
            System.err.println("Error parsing input: ${e.message}")
            throw e
        }
    }
    
    private fun serializeOutput(udm: UDM, format: String, formatSpec: org.apache.utlx.core.ast.FormatSpec, pretty: Boolean): String {
        return try {
            when (format.lowercase()) {
                "xml" -> {
                    // Get encoding option from FormatSpec
                    val encoding = formatSpec.options["encoding"] as? String
                    XMLSerializer(prettyPrint = pretty, outputEncoding = encoding).serialize(udm)
                }
                "json" -> JSONSerializer(pretty).serialize(udm)
                "csv" -> {
                    // Extract CSV output options
                    val delimiter = (formatSpec.options["delimiter"] as? String)?.firstOrNull() ?: ','
                    val headers = (formatSpec.options["headers"] as? Boolean) ?: true
                    val bom = (formatSpec.options["bom"] as? Boolean) ?: false

                    // Extract regional formatting options
                    val regionalFormatStr = formatSpec.options["regionalFormat"] as? String
                    val regionalFormat = when (regionalFormatStr?.lowercase()) {
                        "usa", "us" -> RegionalFormat.USA
                        "european", "eu", "europe" -> RegionalFormat.EUROPEAN
                        "french", "fr" -> RegionalFormat.FRENCH
                        "swiss", "ch" -> RegionalFormat.SWISS
                        null, "none", "" -> RegionalFormat.NONE
                        else -> {
                            System.err.println("Warning: Unknown regionalFormat '$regionalFormatStr', using NONE")
                            RegionalFormat.NONE
                        }
                    }

                    val decimals = (formatSpec.options["decimals"] as? Number)?.toInt() ?: 2
                    val useThousands = (formatSpec.options["useThousands"] as? Boolean) ?: true

                    val dialect = CSVDialect(delimiter = delimiter)
                    CSVSerializer(
                        dialect = dialect,
                        includeHeaders = headers,
                        includeBOM = bom,
                        regionalFormat = regionalFormat,
                        decimals = decimals,
                        useThousands = useThousands
                    ).serialize(udm)
                }
                "yaml", "yml" -> YAMLSerializer().serialize(udm)
                "xsd" -> {
                    // XSD output with pattern enforcement and documentation injection
                    val pattern = (formatSpec.options["pattern"] as? String)?.let {
                        XSDSerializer.XSDPattern.valueOf(it.uppercase().replace("-", "_"))
                    }
                    val version = formatSpec.options["version"] as? String ?: "1.0"
                    val addDoc = formatSpec.options["addDocumentation"] as? Boolean ?: true
                    val elemFormDefault = formatSpec.options["elementFormDefault"] as? String ?: "qualified"

                    XSDSerializer(
                        pattern = pattern,
                        version = version,
                        addDocumentation = addDoc,
                        elementFormDefault = elemFormDefault,
                        prettyPrint = pretty
                    ).serialize(udm)
                }
                "jsch" -> {
                    // JSON Schema output with automatic $schema and description injection
                    val draft = formatSpec.options["draft"] as? String ?: "2020-12"
                    val addDesc = formatSpec.options["addDescriptions"] as? Boolean ?: true
                    val strict = formatSpec.options["strict"] as? Boolean ?: true

                    JSONSchemaSerializer(
                        draft = draft,
                        addDescriptions = addDesc,
                        prettyPrint = pretty,
                        strict = strict
                    ).serialize(udm)
                }
                "avro" -> {
                    // Avro schema output with optional validation
                    val namespace = formatSpec.options["namespace"] as? String
                    val validate = formatSpec.options["validate"] as? Boolean ?: true

                    AvroSchemaSerializer(
                        namespace = namespace,
                        prettyPrint = pretty,
                        validate = validate
                    ).serialize(udm)
                }
                "proto" -> {
                    // Protocol Buffers schema output (proto3)
                    ProtobufSchemaSerializer().serialize(udm)
                }
                else -> throw IllegalArgumentException("Unsupported output format: $format")
            }
        } catch (e: Exception) {
            System.err.println("Error serializing output: ${e.message}")
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
    
    private fun parseOptions(args: Array<String>): TransformOptions {
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

        if (scriptFile == null) {
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
