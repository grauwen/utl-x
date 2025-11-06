// modules/server/src/main/kotlin/org/apache/utlx/server/commands/DesignCommand.kt
package org.apache.utlx.daemon.commands

import org.apache.utlx.analysis.schema.*
import org.apache.utlx.analysis.types.TypeDefinition
import org.apache.utlx.analysis.validation.TransformValidator
import org.apache.utlx.daemon.CommandResult
import org.apache.utlx.core.parser.Parser
import org.apache.utlx.core.parser.ParseResult
import org.apache.utlx.core.lexer.Lexer
import org.apache.utlx.daemon.UTLXDaemon
import org.apache.utlx.daemon.TransportType
import java.io.File

/**
 * Design-time analysis commands for UTL-X daemon server
 *
 * Implements the design-time analysis capabilities described in design-time-schema-analysis.md
 * This includes type inference, schema generation, and transformation validation at design-time.
 */
object DesignCommand {

    fun execute(args: Array<String>): CommandResult {
        if (args.isEmpty()) {
            printUsage()
            return CommandResult.Failure("Subcommand required", 1)
        }

        val subcommand = args[0]
        val subArgs = args.drop(1).toTypedArray()

        return try {
            when (subcommand.lowercase()) {
                "generate-schema", "gen" -> executeGenerateSchema(subArgs)
                "typecheck", "check" -> executeTypecheck(subArgs)
                "infer", "inf" -> executeInfer(subArgs)
                "daemon", "d" -> executeDaemon(subArgs)
                "graph", "g" -> executeGraph(subArgs)
                "help", "--help", "-h" -> {
                    printUsage()
                    CommandResult.Success
                }
                else -> {
                    System.err.println("Unknown design subcommand: $subcommand")
                    printUsage()
                    CommandResult.Failure("Unknown subcommand: $subcommand", 1)
                }
            }
        } catch (e: Exception) {
            System.err.println("Design command error: ${e.message}")
            if (System.getProperty("utlx.debug") == "true") {
                e.printStackTrace()
            }
            CommandResult.Failure(e.message ?: "Unknown error", 1)
        }
    }

    /**
     * Generate output schema from input schema and transformation (design-time analysis)
     */
    private fun executeGenerateSchema(args: Array<String>): CommandResult {
        var inputSchemaFile: String? = null
        var transformFile: String? = null
        var outputFormat = "json-schema"
        var outputFile: String? = null
        var verbose = false
        var pretty = true
        var includeComments = true
        var strictMode = true

        var i = 0
        while (i < args.size) {
            when (args[i]) {
                "--input-schema", "-i" -> {
                    if (i + 1 >= args.size) throw IllegalArgumentException("--input-schema requires a file path")
                    inputSchemaFile = args[++i]
                }
                "--transform", "-t" -> {
                    if (i + 1 >= args.size) throw IllegalArgumentException("--transform requires a file path")
                    transformFile = args[++i]
                }
                "--output-format", "-f" -> {
                    if (i + 1 >= args.size) throw IllegalArgumentException("--output-format requires a format")
                    outputFormat = args[++i]
                }
                "--output", "-o" -> {
                    if (i + 1 >= args.size) throw IllegalArgumentException("--output requires a file path")
                    outputFile = args[++i]
                }
                "--verbose", "-v" -> verbose = true
                "--no-pretty" -> pretty = false
                "--no-comments" -> includeComments = false
                "--no-strict" -> strictMode = false
                else -> {
                    if (transformFile == null) {
                        transformFile = args[i]
                    } else {
                        throw IllegalArgumentException("Unknown argument: ${args[i]}")
                    }
                }
            }
            i++
        }

        if (transformFile == null) {
            throw IllegalArgumentException("Transform file is required")
        }

        if (verbose) {
            println("Generating output schema from transformation (design-time analysis)...")
            println("  Transform: $transformFile")
            inputSchemaFile?.let { println("  Input schema: $it") }
            println("  Output format: $outputFormat")
            outputFile?.let { println("  Output file: $it") }
        }

        // Parse transformation
        val transformContent = File(transformFile).readText()
        val lexer = Lexer(transformContent)
        val tokens = lexer.tokenize()
        val parser = Parser(tokens)
        val parseResult = parser.parse()

        val program = when (parseResult) {
            is ParseResult.Success -> parseResult.program
            is ParseResult.Failure -> {
                System.err.println("Failed to parse transformation:")
                parseResult.errors.forEach { error ->
                    System.err.println("  ${error.location.line}:${error.location.column} - ${error.message}")
                }
                return CommandResult.Failure("Operation failed", 1)
            }
        }

        val schemaGenerator = SchemaGenerator()
        val schemaFormat = parseSchemaFormat(outputFormat)
        val options = GeneratorOptions(
            pretty = pretty,
            includeComments = includeComments,
            strictMode = strictMode
        )

        val outputSchema = if (inputSchemaFile != null) {
            // Generate with input schema (design-time type inference)
            val inputSchemaContent = File(inputSchemaFile).readText()
            val inputSchemaFormat = detectSchemaFormat(inputSchemaFile, inputSchemaContent)

            if (verbose) {
                println("  Input schema format: ${inputSchemaFormat.name.lowercase()}")
            }

            schemaGenerator.generate(
                transformation = program,
                inputSchemaContent = inputSchemaContent,
                inputSchemaFormat = inputSchemaFormat,
                outputSchemaFormat = schemaFormat,
                options = options
            )
        } else {
            // Infer without input schema
            if (verbose) {
                println("  No input schema provided - inferring types from transformation")
            }

            schemaGenerator.inferSchema(
                transformation = program,
                outputSchemaFormat = schemaFormat,
                options = options
            )
        }

        // Output result
        if (outputFile != null) {
            File(outputFile).writeText(outputSchema)
            if (verbose) {
                println("✓ Output schema generated and saved to: $outputFile")
            }
        } else {
            println(outputSchema)
        }

        return CommandResult.Success
    }

    /**
     * Typecheck transformation against input and expected output schemas
     */
    private fun executeTypecheck(args: Array<String>): CommandResult {
        var inputSchemaFile: String? = null
        var transformFile: String? = null
        var expectedOutputFile: String? = null
        var verbose = false

        var i = 0
        while (i < args.size) {
            when (args[i]) {
                "--input-schema", "-i" -> {
                    if (i + 1 >= args.size) throw IllegalArgumentException("--input-schema requires a file path")
                    inputSchemaFile = args[++i]
                }
                "--transform", "-t" -> {
                    if (i + 1 >= args.size) throw IllegalArgumentException("--transform requires a file path")
                    transformFile = args[++i]
                }
                "--expected-output", "-e" -> {
                    if (i + 1 >= args.size) throw IllegalArgumentException("--expected-output requires a file path")
                    expectedOutputFile = args[++i]
                }
                "--verbose", "-v" -> verbose = true
                else -> {
                    if (transformFile == null) {
                        transformFile = args[i]
                    } else {
                        throw IllegalArgumentException("Unknown argument: ${args[i]}")
                    }
                }
            }
            i++
        }

        if (transformFile == null) {
            throw IllegalArgumentException("Transform file is required")
        }
        if (inputSchemaFile == null) {
            throw IllegalArgumentException("Input schema file is required for typechecking")
        }
        if (expectedOutputFile == null) {
            throw IllegalArgumentException("Expected output schema file is required for typechecking")
        }

        if (verbose) {
            println("Typechecking transformation (design-time validation)...")
            println("  Transform: $transformFile")
            println("  Input schema: $inputSchemaFile")
            println("  Expected output: $expectedOutputFile")
        }

        // Parse files
        val transformContent = File(transformFile).readText()
        val lexer = Lexer(transformContent)
        val tokens = lexer.tokenize()
        val parser = Parser(tokens)
        val parseResult = parser.parse()

        val program = when (parseResult) {
            is ParseResult.Success -> parseResult.program
            is ParseResult.Failure -> {
                System.err.println("Failed to parse transformation:")
                parseResult.errors.forEach { error ->
                    System.err.println("  ${error.location.line}:${error.location.column} - ${error.message}")
                }
                return CommandResult.Failure("Operation failed", 1)
            }
        }

        val inputSchemaContent = File(inputSchemaFile).readText()
        val inputSchemaFormat = detectSchemaFormat(inputSchemaFile, inputSchemaContent)

        val expectedOutputContent = File(expectedOutputFile).readText()
        val expectedOutputFormat = detectSchemaFormat(expectedOutputFile, expectedOutputContent)

        // Parse schemas into type definitions
        val inputType = parseSchemaToType(inputSchemaContent, inputSchemaFormat)
        val expectedOutputType = parseSchemaToType(expectedOutputContent, expectedOutputFormat)

        // Validate transformation
        val validator = TransformValidator()
        val result = validator.validate(program, inputType, expectedOutputType)

        if (result.isValid) {
            println("✓ Typecheck successful")
            if (verbose && result.warnings.isNotEmpty()) {
                println("\nWarnings:")
                result.warnings.forEach { println("  ⚠ $it") }
            }
            return CommandResult.Success
        } else {
            println("✗ Typecheck failed")
            println("\nErrors:")
            result.errors.forEach { println("  ✗ $it") }

            if (result.warnings.isNotEmpty()) {
                println("\nWarnings:")
                result.warnings.forEach { println("  ⚠ $it") }
            }
            return CommandResult.Failure("Operation failed", 1)
        }
    }

    /**
     * Infer schema from transformation without input schema
     */
    private fun executeInfer(args: Array<String>): CommandResult {
        var transformFile: String? = null
        var outputFormat = "json-schema"
        var outputFile: String? = null
        var verbose = false
        var pretty = true

        var i = 0
        while (i < args.size) {
            when (args[i]) {
                "--transform", "-t" -> {
                    if (i + 1 >= args.size) throw IllegalArgumentException("--transform requires a file path")
                    transformFile = args[++i]
                }
                "--output-format", "-f" -> {
                    if (i + 1 >= args.size) throw IllegalArgumentException("--output-format requires a format")
                    outputFormat = args[++i]
                }
                "--output", "-o" -> {
                    if (i + 1 >= args.size) throw IllegalArgumentException("--output requires a file path")
                    outputFile = args[++i]
                }
                "--verbose", "-v" -> verbose = true
                "--no-pretty" -> pretty = false
                else -> {
                    if (transformFile == null) {
                        transformFile = args[i]
                    } else {
                        throw IllegalArgumentException("Unknown argument: ${args[i]}")
                    }
                }
            }
            i++
        }

        if (transformFile == null) {
            throw IllegalArgumentException("Transform file is required")
        }

        if (verbose) {
            println("Inferring schema from transformation...")
            println("  Transform: $transformFile")
            println("  Output format: $outputFormat")
            outputFile?.let { println("  Output file: $it") }
        }

        // Parse transformation
        val transformContent = File(transformFile).readText()
        val lexer = Lexer(transformContent)
        val tokens = lexer.tokenize()
        val parser = Parser(tokens)
        val parseResult = parser.parse()

        val program = when (parseResult) {
            is ParseResult.Success -> parseResult.program
            is ParseResult.Failure -> {
                System.err.println("Failed to parse transformation:")
                parseResult.errors.forEach { error ->
                    System.err.println("  ${error.location.line}:${error.location.column} - ${error.message}")
                }
                return CommandResult.Failure("Operation failed", 1)
            }
        }

        val schemaGenerator = SchemaGenerator()
        val schemaFormat = parseSchemaFormat(outputFormat)
        val options = GeneratorOptions(pretty = pretty)

        val outputSchema = schemaGenerator.inferSchema(
            transformation = program,
            outputSchemaFormat = schemaFormat,
            options = options
        )

        // Output result
        if (outputFile != null) {
            File(outputFile).writeText(outputSchema)
            if (verbose) {
                println("✓ Schema inferred and saved to: $outputFile")
            }
        } else {
            println(outputSchema)
        }

        return CommandResult.Success
    }

    /**
     * Start daemon mode for IDE integration
     *
     * Runs a long-running LSP server using JSON-RPC 2.0 protocol.
     * Transport options: STDIO (default, for IDEs) or Socket (for remote access).
     */
    private fun executeDaemon(args: Array<String>): CommandResult {
        var transportType = TransportType.STDIO
        var port = 7777
        var verbose = false

        var i = 0
        while (i < args.size) {
            when (args[i]) {
                "--stdio" -> {
                    transportType = TransportType.STDIO
                }
                "--socket" -> {
                    transportType = TransportType.SOCKET
                    // Check if next arg is a port number
                    if (i + 1 < args.size && args[i + 1].toIntOrNull() != null) {
                        port = args[++i].toInt()
                    }
                }
                "--port" -> {
                    if (i + 1 >= args.size) throw IllegalArgumentException("--port requires a port number")
                    port = args[++i].toIntOrNull()
                        ?: throw IllegalArgumentException("Invalid port number: ${args[i]}")
                    transportType = TransportType.SOCKET
                }
                "--verbose", "-v" -> {
                    verbose = true
                }
                "--help", "-h" -> {
                    printDaemonUsage()
                    return CommandResult.Success
                }
                else -> {
                    throw IllegalArgumentException("Unknown daemon argument: ${args[i]}")
                }
            }
            i++
        }

        if (verbose) {
            println("Starting UTL-X Daemon (Language Server)")
            println("  Protocol: LSP (JSON-RPC 2.0)")
            println("  Transport: ${transportType.name}")
            if (transportType == TransportType.SOCKET) {
                println("  Port: $port")
            }
            println()
            println("The daemon provides:")
            println("  • Design-time type inference")
            println("  • Path autocomplete")
            println("  • Schema validation")
            println("  • Real-time error checking")
            println()
            if (transportType == TransportType.STDIO) {
                println("Ready to accept LSP messages on stdin...")
            } else {
                println("Listening for LSP clients on port $port...")
            }
            println()
        }

        // Start daemon - this blocks until shutdown
        val daemon = UTLXDaemon(transportType, port)

        // Register shutdown hook
        Runtime.getRuntime().addShutdownHook(Thread {
            if (verbose) {
                System.err.println("Shutting down daemon...")
            }
            daemon.stop()
        })

        try {
            daemon.start()
            return CommandResult.Success
        } catch (e: Exception) {
            System.err.println("Daemon error: ${e.message}")
            if (verbose || System.getProperty("utlx.debug") == "true") {
                e.printStackTrace()
            }
            return CommandResult.Failure("Operation failed", 1)
        }
    }

    private fun printDaemonUsage() {
        println("""
            |UTL-X Daemon Mode (LSP Server)
            |
            |Usage: utlx design daemon [options]
            |
            |The daemon runs as a long-running Language Server Protocol (LSP) server
            |for IDE integration. It provides real-time autocomplete, type checking,
            |and schema validation.
            |
            |Options:
            |  --stdio           Use standard I/O transport (default, for IDE plugins)
            |  --socket [PORT]   Use TCP socket transport (for remote access)
            |  --port PORT       Specify port for socket transport (default: 7777)
            |  --verbose, -v     Enable verbose logging
            |  --help, -h        Show this help message
            |
            |Examples:
            |  # Start daemon for IDE integration (STDIO)
            |  utlx design daemon --stdio
            |
            |  # Start daemon on TCP socket for remote access
            |  utlx design daemon --socket 7777
            |
            |  # Start with verbose logging
            |  utlx design daemon --stdio --verbose
            |
            |Architecture:
            |  • Daemon = Long-running background process with state cache
            |  • LSP = Language Server Protocol (JSON-RPC 2.0)
            |  • STDIO/Socket = Transport mechanism (physical channel)
            |
            |All communication uses the same LSP/JSON-RPC 2.0 protocol.
            |Only the transport layer differs.
            |
            |See: docs/architecture/design-time-schema-analysis-enhanced.md
        """.trimMargin())
    }

    /**
     * Generate graph representation and visualization
     */
    private fun executeGraph(args: Array<String>): CommandResult {
        var transformFile: String? = null
        var outputFile: String? = null
        var layout = "TB"
        var verbose = false
        var format = "dot"  // dot, svg, png (requires graphviz installed)

        var i = 0
        while (i < args.size) {
            when (args[i]) {
                "--transform", "-t" -> {
                    if (i + 1 >= args.size) throw IllegalArgumentException("--transform requires a file path")
                    transformFile = args[++i]
                }
                "--output", "-o" -> {
                    if (i + 1 >= args.size) throw IllegalArgumentException("--output requires a file path")
                    outputFile = args[++i]
                }
                "--layout", "-l" -> {
                    if (i + 1 >= args.size) throw IllegalArgumentException("--layout requires a layout type")
                    layout = args[++i]
                    if (layout !in listOf("TB", "LR", "BT", "RL")) {
                        throw IllegalArgumentException("Invalid layout: $layout. Use TB, LR, BT, or RL")
                    }
                }
                "--format", "-f" -> {
                    if (i + 1 >= args.size) throw IllegalArgumentException("--format requires a format type")
                    format = args[++i]
                }
                "--verbose", "-v" -> verbose = true
                "--help", "-h" -> {
                    printGraphUsage()
                    return CommandResult.Success
                }
                else -> {
                    if (transformFile == null) {
                        transformFile = args[i]
                    } else {
                        throw IllegalArgumentException("Unknown argument: ${args[i]}")
                    }
                }
            }
            i++
        }

        if (transformFile == null) {
            throw IllegalArgumentException("Transform file is required")
        }

        if (verbose) {
            println("Generating AST visualization...")
            println("  Transform: $transformFile")
            println("  Layout: $layout")
            println("  Format: $format")
            outputFile?.let { println("  Output: $it") }
        }

        // Parse transformation to get AST
        val transformContent = File(transformFile).readText()
        val lexer = Lexer(transformContent)
        val tokens = lexer.tokenize()
        val parser = Parser(tokens)
        val parseResult = parser.parse()

        val program = when (parseResult) {
            is ParseResult.Success -> parseResult.program
            is ParseResult.Failure -> {
                System.err.println("Failed to parse transformation:")
                parseResult.errors.forEach { error ->
                    System.err.println("  ${error.location.line}:${error.location.column} - ${error.message}")
                }
                return CommandResult.Failure("Operation failed", 1)
            }
        }

        // Generate DOT graph
        val visualizer = org.apache.utlx.analysis.visualization.GraphvizASTVisualizer()
        val options = org.apache.utlx.analysis.visualization.VisualizationOptions(layout = layout)
        val dotGraph = visualizer.visualize(program, options)

        // Handle output
        when (format.lowercase()) {
            "dot" -> {
                // Output DOT file directly
                if (outputFile != null) {
                    File(outputFile).writeText(dotGraph)
                    if (verbose) {
                        println("✓ DOT graph saved to: $outputFile")
                        println()
                        println("To render to SVG, run:")
                        println("  dot -Tsvg $outputFile -o ${outputFile.replace(".dot", ".svg")}")
                    }
                } else {
                    println(dotGraph)
                }
            }
            "svg", "png", "pdf" -> {
                // Try to run graphviz to generate output format
                val tempDotFile = kotlin.io.path.createTempFile("utlx-ast", ".dot").toFile()
                try {
                    tempDotFile.writeText(dotGraph)

                    val actualOutputFile = outputFile ?: "ast.$format"
                    val cmd = listOf("dot", "-T$format", tempDotFile.absolutePath, "-o", actualOutputFile)

                    if (verbose) {
                        println("Running: ${cmd.joinToString(" ")}")
                    }

                    val process = ProcessBuilder(cmd)
                        .redirectErrorStream(true)
                        .start()

                    val exitCode = process.waitFor()

                    if (exitCode == 0) {
                        if (verbose) {
                            println("✓ $format visualization saved to: $actualOutputFile")
                        }
                    } else {
                        System.err.println("Error running Graphviz dot command (exit code: $exitCode)")
                        System.err.println("Make sure Graphviz is installed: https://graphviz.org/download/")
                        System.err.println()
                        System.err.println("Falling back to DOT output:")
                        println(dotGraph)
                        if (verbose) {
                            println()
                            println("You can manually convert to $format with:")
                            println("  dot -T$format <input.dot> -o <output.$format>")
                        }
                    }
                } finally {
                    tempDotFile.delete()
                }
            }
            else -> {
                throw IllegalArgumentException("Unsupported format: $format. Use dot, svg, png, or pdf")
            }
        }

        return CommandResult.Success
    }

    private fun printGraphUsage() {
        println("""
            |UTL-X AST Visualization
            |
            |Usage: utlx design graph [options] <transform-file>
            |
            |Generate a visual representation of the transformation's Abstract Syntax Tree (AST)
            |in Graphviz DOT format. This is useful for understanding transformation structure,
            |debugging complex logic, and creating documentation.
            |
            |Options:
            |  --transform, -t FILE   Transformation file to visualize (can also be positional)
            |  --output, -o FILE      Output file path (stdout if not specified)
            |  --layout, -l LAYOUT    Graph layout direction: TB, LR, BT, RL (default: TB)
            |                           TB = Top to Bottom
            |                           LR = Left to Right
            |                           BT = Bottom to Top
            |                           RL = Right to Left
            |  --format, -f FORMAT    Output format: dot, svg, png, pdf (default: dot)
            |                           svg/png/pdf require Graphviz installed
            |  --verbose, -v          Enable verbose output
            |  --help, -h             Show this help message
            |
            |Examples:
            |  # Generate DOT file
            |  utlx design graph transform.utlx -o ast.dot
            |
            |  # Generate SVG directly (requires Graphviz)
            |  utlx design graph transform.utlx -f svg -o ast.svg
            |
            |  # Left-to-right layout
            |  utlx design graph transform.utlx -l LR -o ast.dot
            |
            |  # Output DOT to stdout
            |  utlx design graph transform.utlx
            |
            |  # Generate PNG (requires Graphviz)
            |  utlx design graph transform.utlx -f png -o ast.png
            |
            |Manual Rendering:
            |  If you have Graphviz installed, you can convert DOT files manually:
            |    dot -Tsvg ast.dot -o ast.svg
            |    dot -Tpng ast.dot -o ast.png
            |    dot -Tpdf ast.dot -o ast.pdf
            |
            |Install Graphviz:
            |  macOS:   brew install graphviz
            |  Ubuntu:  sudo apt-get install graphviz
            |  Windows: choco install graphviz
            |  Web:     https://graphviz.org/download/
        """.trimMargin())
    }

    private fun parseSchemaFormat(format: String): SchemaFormat {
        return when (format.lowercase()) {
            "xsd", "xml-schema" -> SchemaFormat.XSD
            "json-schema", "json" -> SchemaFormat.JSON_SCHEMA
            "csv-schema", "csv" -> SchemaFormat.CSV_SCHEMA
            "yaml-schema", "yaml" -> SchemaFormat.YAML_SCHEMA
            else -> throw IllegalArgumentException("Unsupported schema format: $format. Supported: xsd, json-schema")
        }
    }

    private fun detectSchemaFormat(filePath: String, content: String): SchemaFormat {
        // Detect by file extension first
        return when {
            filePath.endsWith(".xsd") -> SchemaFormat.XSD
            filePath.endsWith(".json") -> SchemaFormat.JSON_SCHEMA
            filePath.endsWith(".yaml") || filePath.endsWith(".yml") -> SchemaFormat.YAML_SCHEMA
            filePath.endsWith(".csv") -> SchemaFormat.CSV_SCHEMA

            // Detect by content
            content.trimStart().startsWith("<?xml") && content.contains("schema") -> SchemaFormat.XSD
            content.trimStart().startsWith("{") && content.contains("\"\$schema\"") -> SchemaFormat.JSON_SCHEMA
            content.contains("---") || content.contains(":") -> SchemaFormat.YAML_SCHEMA

            else -> SchemaFormat.JSON_SCHEMA // Default
        }
    }

    private fun parseSchemaToType(content: String, format: SchemaFormat): TypeDefinition {
        return when (format) {
            SchemaFormat.XSD -> XSDSchemaParser().parse(content, format)
            SchemaFormat.JSON_SCHEMA -> JSONSchemaParser().parse(content, format)
            SchemaFormat.CSV_SCHEMA -> throw NotImplementedError("CSV schema parsing not yet implemented")
            SchemaFormat.YAML_SCHEMA -> throw NotImplementedError("YAML schema parsing not yet implemented")
        }
    }

    private fun printUsage() {
        println("""
            |UTL-X Design-Time Analysis Commands
            |
            |Usage: utlx design <subcommand> [options]
            |
            |Design-time analysis enables static type checking, schema generation, and IDE integration
            |without executing transformations. Analyze metadata (XSD/JSON Schema) to infer output schemas.
            |
            |Subcommands:
            |  generate-schema (gen)  Generate output schema from input schema and transformation
            |  typecheck (check)      Typecheck transformation against input/output schemas
            |  infer (inf)            Infer schema from transformation without input schema
            |  daemon (d)             Start daemon mode for IDE integration (LSP-style)
            |  graph (g)              Generate graph representation and visualization
            |  help                   Show this help message
            |
            |Generate-Schema Examples:
            |  # XSD input → UTLX transformation → JSON Schema output (design-time)
            |  utlx design generate-schema --input-schema order.xsd --transform order-to-invoice.utlx \
            |    --output-format json-schema --output invoice-schema.json
            |
            |  # JSON Schema input → UTLX transformation → XSD output (design-time)
            |  utlx design generate-schema --input-schema customer.json --transform process-customer.utlx \
            |    --output-format xsd --output customer-output.xsd
            |
            |  # Infer output schema without input schema
            |  utlx design generate-schema --transform data-processor.utlx --output-format json-schema
            |
            |Typecheck Examples:
            |  # Validate transformation produces expected output schema
            |  utlx design typecheck --input-schema order.xsd --transform order-to-invoice.utlx \
            |    --expected-output invoice-schema.json --verbose
            |
            |Infer Examples:
            |  utlx design infer --transform data-processor.utlx --output-format json-schema \
            |    --output inferred-schema.json
            |
            |Daemon Examples (coming soon):
            |  # Start daemon for IDE autocomplete and type checking
            |  utlx design daemon --stdio
            |  utlx design daemon --port 7777
            |
            |Graph Examples (coming soon):
            |  # Generate DOT visualization
            |  utlx design graph --input-schema order.xsd --visualize dot --output graph.dot
            |
            |Supported Schema Formats:
            |  Input:  xsd, json-schema (avro planned)
            |  Output: json-schema, xsd (csv-schema, yaml-schema planned)
            |
            |Global Options:
            |  --verbose, -v     Enable verbose output
            |  --output, -o      Write output to file
            |  --no-pretty       Disable pretty-printing
            |  --no-comments     Disable comments in generated schemas
            |  --no-strict       Disable strict mode validation
            |
            |Documentation:
            |  Design-time analysis: docs/architecture/design-time-schema-analysis.md
            |  Daemon & IDE support: docs/architecture/design-time-schema-analysis-enhanced.md
        """.trimMargin())
    }
}
