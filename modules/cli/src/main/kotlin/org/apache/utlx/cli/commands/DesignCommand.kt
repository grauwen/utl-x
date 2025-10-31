// modules/cli/src/main/kotlin/org/apache/utlx/cli/commands/DesignCommand.kt
package org.apache.utlx.cli.commands

import org.apache.utlx.analysis.schema.*
import org.apache.utlx.analysis.types.TypeDefinition
import org.apache.utlx.analysis.validation.TransformValidator
import org.apache.utlx.core.parser.Parser
import org.apache.utlx.core.parser.ParseResult
import org.apache.utlx.core.lexer.Lexer
import java.io.File
import kotlin.system.exitProcess

/**
 * Design-time analysis commands for UTL-X CLI
 *
 * Implements the design-time analysis capabilities described in design-time-schema-analysis.md
 * This includes type inference, schema generation, and transformation validation at design-time.
 */
object DesignCommand {

    fun execute(args: Array<String>) {
        if (args.isEmpty()) {
            printUsage()
            exitProcess(1)
        }

        val subcommand = args[0]
        val subArgs = args.drop(1).toTypedArray()

        try {
            when (subcommand.lowercase()) {
                "generate-schema", "gen" -> executeGenerateSchema(subArgs)
                "typecheck", "check" -> executeTypecheck(subArgs)
                "infer", "inf" -> executeInfer(subArgs)
                "daemon", "d" -> executeDaemon(subArgs)
                "graph", "g" -> executeGraph(subArgs)
                "help", "--help", "-h" -> printUsage()
                else -> {
                    System.err.println("Unknown design subcommand: $subcommand")
                    printUsage()
                    exitProcess(1)
                }
            }
        } catch (e: Exception) {
            System.err.println("Design command error: ${e.message}")
            if (System.getProperty("utlx.debug") == "true") {
                e.printStackTrace()
            }
            exitProcess(1)
        }
    }

    /**
     * Generate output schema from input schema and transformation (design-time analysis)
     */
    private fun executeGenerateSchema(args: Array<String>) {
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
                exitProcess(1)
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
    }

    /**
     * Typecheck transformation against input and expected output schemas
     */
    private fun executeTypecheck(args: Array<String>) {
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
                exitProcess(1)
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
        } else {
            println("✗ Typecheck failed")
            println("\nErrors:")
            result.errors.forEach { println("  ✗ $it") }

            if (result.warnings.isNotEmpty()) {
                println("\nWarnings:")
                result.warnings.forEach { println("  ⚠ $it") }
            }
            exitProcess(1)
        }
    }

    /**
     * Infer schema from transformation without input schema
     */
    private fun executeInfer(args: Array<String>) {
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
                exitProcess(1)
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
    }

    /**
     * Start daemon mode for IDE integration
     *
     * Runs a long-running LSP server using JSON-RPC 2.0 protocol.
     * Transport options: STDIO (default, for IDEs) or Socket (for remote access).
     */
    private fun executeDaemon(args: Array<String>) {
        println("Daemon mode not yet implemented")
        println()
        println("The daemon will run as a long-running LSP server using JSON-RPC 2.0 protocol.")
        println()
        println("Planned transport mechanisms:")
        println("  --stdio   : JSON-RPC over standard streams (default, for IDE integration)")
        println("  --socket  : JSON-RPC over TCP socket (for remote access)")
        println()
        println("Key concepts:")
        println("  • Daemon = Long-running background process with state cache")
        println("  • LSP = Language Server Protocol (JSON-RPC 2.0)")
        println("  • STDIO/Socket = Transport mechanism (how messages are sent)")
        println()
        println("All communication uses the same LSP/JSON-RPC 2.0 protocol.")
        println("Only the transport layer differs (STDIO vs Socket).")
        println()
        println("See: docs/architecture/design-time-schema-analysis-enhanced.md")
    }

    /**
     * Generate graph representation and visualization
     */
    private fun executeGraph(args: Array<String>) {
        println("Graph generation not yet implemented")
        println("Coming soon: Generate UDM graph with nodes, edges, and visualization (DOT/Mermaid/D3)")
        println("See: docs/architecture/design-time-schema-analysis-enhanced.md")
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
