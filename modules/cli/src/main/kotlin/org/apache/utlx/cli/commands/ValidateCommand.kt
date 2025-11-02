// modules/cli/src/main/kotlin/org/apache/utlx/cli/commands/ValidateCommand.kt
package org.apache.utlx.cli.commands

import org.apache.utlx.core.lexer.Lexer
import org.apache.utlx.core.parser.Parser
import org.apache.utlx.core.parser.ParseResult
import org.apache.utlx.core.types.TypeChecker
import org.apache.utlx.core.types.TypeCheckResult
import org.apache.utlx.core.types.StandardLibrary
import org.apache.utlx.analysis.schema.*
import org.apache.utlx.analysis.types.TypeDefinition
import org.apache.utlx.cli.CommandResult
import java.io.File

/**
 * Validate command - validates UTL-X scripts for correctness (Levels 1-3)
 *
 * Implements Option A (Separate Commands) from validation-and-analysis-study.md
 *
 * Level 1: Syntactic validation (parse errors)
 * Level 2: Semantic validation (type errors, undefined variables)
 * Level 3: Schema validation (if --schema provided)
 *
 * Usage:
 *   utlx validate <script-file> [options]
 *   utlx validate <script-file> --schema input-schema.xsd
 *   utlx validate <script-file> --schema input-schema.xsd --strict
 */
object ValidateCommand {

    data class ValidateOptions(
        val scriptFile: File,
        val schemaFile: File? = null,
        val strict: Boolean = false,
        val verbose: Boolean = false,
        val format: ValidationFormat = ValidationFormat.HUMAN,
        val noTypeCheck: Boolean = false
    )

    enum class ValidationFormat {
        HUMAN,      // Human-readable format (default)
        JSON,       // JSON format for IDE integration
        COMPACT     // Compact single-line format
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

        if (options.verbose) {
            println("UTL-X Validate")
            println("Script: ${options.scriptFile.absolutePath}")
            options.schemaFile?.let { println("Schema: ${it.absolutePath}") }
            println()
        }

        var hasErrors = false

        // Level 1: Syntactic Validation (Parse)
        if (options.verbose) {
            println("=== Level 1: Syntactic Validation ===")
        }

        val scriptContent = try {
            options.scriptFile.readText()
        } catch (e: Exception) {
            System.err.println("✗ Error reading script file: ${e.message}")
            throw IllegalStateException("Error reading script file: ${e.message}", e)
        }

        val lexer = Lexer(scriptContent)
        val tokens = try {
            lexer.tokenize()
        } catch (e: Exception) {
            System.err.println("✗ Lexer error: ${e.message}")
            throw IllegalStateException("Lexer error: ${e.message}", e)
        }

        val parser = Parser(tokens)
        val parseResult = parser.parse()

        val program = when (parseResult) {
            is ParseResult.Success -> {
                if (options.verbose) {
                    println("✓ Syntax validation passed")
                    println()
                }
                parseResult.program
            }
            is ParseResult.Failure -> {
                hasErrors = true

                // Group errors by section
                val headerErrors = parseResult.errors.filter {
                    it.section == org.apache.utlx.core.parser.ScriptSection.HEADER ||
                    it.section == org.apache.utlx.core.parser.ScriptSection.SEPARATOR
                }
                val contentErrors = parseResult.errors.filter {
                    it.section == org.apache.utlx.core.parser.ScriptSection.CONTENT
                }

                // Print header errors first (if any)
                if (headerErrors.isNotEmpty()) {
                    printErrors("Header Errors", headerErrors.map { error ->
                        ValidationError(
                            level = ErrorLevel.ERROR,
                            message = error.message,
                            location = error.location,
                            code = "HEADER_ERROR"
                        )
                    }, options.format, scriptContent)

                    if (options.verbose) {
                        println("  💡 Fix header issues first - they may prevent content parsing")
                        println()
                    }
                }

                // Print content errors (if any)
                if (contentErrors.isNotEmpty()) {
                    printErrors("Transformation Errors", contentErrors.map { error ->
                        ValidationError(
                            level = ErrorLevel.ERROR,
                            message = error.message,
                            location = error.location,
                            code = "PARSE_ERROR"
                        )
                    }, options.format, scriptContent)
                }

                // Parse errors prevent further validation - return early
                return when (options.format) {
                    ValidationFormat.HUMAN -> {
                        println("✗ Validation failed")
                        CommandResult.Failure("Validation failed", 1)
                    }
                    ValidationFormat.JSON -> {
                        println("""{"status":"failure"}""")
                        CommandResult.Failure("Validation failed", 1)
                    }
                    ValidationFormat.COMPACT -> {
                        println("FAIL")
                        CommandResult.Failure("Validation failed", 1)
                    }
                }
            }
        }

        // Level 2: Semantic Validation (Type Checking)
        if (!options.noTypeCheck) {
            if (options.verbose) {
                println("=== Level 2: Semantic Validation ===")
            }

            val stdlib = StandardLibrary()
            val typeChecker = TypeChecker(stdlib)
            val typeCheckResult = typeChecker.check(program)

            when (typeCheckResult) {
                is TypeCheckResult.Success -> {
                    if (options.verbose) {
                        println("✓ Semantic validation passed")
                        println("  Inferred type: ${typeCheckResult.type}")
                        println()
                    }
                }
                is TypeCheckResult.Failure -> {
                    // Type errors are currently treated as warnings unless --strict
                    if (options.strict) {
                        hasErrors = true
                        printErrors("Type Errors", typeCheckResult.errors.map { error ->
                            ValidationError(
                                level = ErrorLevel.ERROR,
                                message = error.message,
                                location = error.location,
                                code = "TYPE_ERROR"
                            )
                        }, options.format, scriptContent)
                    } else {
                        // Print as warnings
                        printErrors("Type Warnings", typeCheckResult.errors.map { error ->
                            ValidationError(
                                level = ErrorLevel.WARNING,
                                message = error.message,
                                location = error.location,
                                code = "TYPE_WARNING"
                            )
                        }, options.format, scriptContent)

                        if (options.verbose) {
                            println()
                            println("  (Use --strict to treat type warnings as errors)")
                            println()
                        }
                    }
                }
            }
        } else if (options.verbose) {
            println("=== Level 2: Semantic Validation ===")
            println("⊘ Skipped (--no-typecheck)")
            println()
        }

        // Level 3: Schema Validation (if schema provided)
        if (options.schemaFile != null) {
            if (options.verbose) {
                println("=== Level 3: Schema Validation ===")
            }

            try {
                val schemaContent = options.schemaFile.readText()
                val schemaFormat = detectSchemaFormat(options.schemaFile.absolutePath, schemaContent)

                if (options.verbose) {
                    println("  Schema format: ${schemaFormat.name.lowercase()}")
                }

                // Parse schema to TypeDefinition
                val inputType = parseSchemaToType(schemaContent, schemaFormat)

                // Validate transformation against schema by inferring output type
                try {
                    val typeInference = org.apache.utlx.analysis.types.AdvancedTypeInference()
                    val outputType = typeInference.inferOutputType(program, inputType)

                    if (options.verbose) {
                        println("✓ Schema validation passed")
                        println("  Inferred output type: $outputType")
                        println()
                    }
                } catch (e: Exception) {
                    hasErrors = true
                    printErrors("Schema Errors", listOf(
                        ValidationError(
                            level = ErrorLevel.ERROR,
                            message = "Schema validation failed: ${e.message}",
                            location = program.location,
                            code = "SCHEMA_ERROR"
                        )
                    ), options.format, scriptContent)
                }
            } catch (e: Exception) {
                hasErrors = true
                System.err.println("✗ Schema validation error: ${e.message}")
                if (options.verbose) {
                    e.printStackTrace()
                }
            }
        } else if (options.verbose) {
            println("=== Level 3: Schema Validation ===")
            println("⊘ Skipped (no --schema provided)")
            println()
        }

        // Final result
        return if (!hasErrors) {
            when (options.format) {
                ValidationFormat.HUMAN -> println("✓ Validation passed")
                ValidationFormat.JSON -> println("""{"status":"success","errors":[],"warnings":[]}""")
                ValidationFormat.COMPACT -> println("PASS")
            }
            CommandResult.Success
        } else {
            when (options.format) {
                ValidationFormat.HUMAN -> println("✗ Validation failed")
                ValidationFormat.JSON -> println("""{"status":"failure"}""")
                ValidationFormat.COMPACT -> println("FAIL")
            }
            CommandResult.Failure("Validation failed", 1)
        }
    }

    private fun parseOptions(args: Array<String>): ValidateOptions {
        if (args.isEmpty()) {
            printUsage()
            throw IllegalArgumentException("No arguments provided")
        }

        var scriptFile: File? = null
        var schemaFile: File? = null
        var strict = false
        var verbose = false
        var format = ValidationFormat.HUMAN
        var noTypeCheck = false

        var i = 0
        while (i < args.size) {
            when (args[i]) {
                "--schema", "-s" -> {
                    if (i + 1 >= args.size) {
                        System.err.println("Error: --schema requires a file path")
                        printUsage()
                        throw IllegalArgumentException("--schema requires a file path")
                    }
                    schemaFile = File(args[++i])
                }
                "--strict" -> {
                    strict = true
                }
                "-v", "--verbose" -> {
                    verbose = true
                }
                "--format", "-f" -> {
                    if (i + 1 >= args.size) {
                        System.err.println("Error: --format requires a format (human, json, compact)")
                        printUsage()
                        throw IllegalArgumentException("--format requires a format (human, json, compact)")
                    }
                    format = when (args[++i].lowercase()) {
                        "human" -> ValidationFormat.HUMAN
                        "json" -> ValidationFormat.JSON
                        "compact" -> ValidationFormat.COMPACT
                        else -> {
                            System.err.println("Error: Invalid format. Use: human, json, compact")
                            throw IllegalArgumentException("Invalid format. Use: human, json, compact")
                        }
                    }
                }
                "--no-typecheck" -> {
                    noTypeCheck = true
                }
                "-h", "--help" -> {
                    printUsage()
                    throw IllegalStateException("HELP_REQUESTED")
                }
                else -> {
                    if (!args[i].startsWith("-")) {
                        if (scriptFile == null) {
                            scriptFile = File(args[i])
                        } else {
                            System.err.println("Error: Unknown argument: ${args[i]}")
                            printUsage()
                            throw IllegalArgumentException("Unknown argument: ${args[i]}")
                        }
                    } else {
                        System.err.println("Error: Unknown option: ${args[i]}")
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

        if (schemaFile != null && !schemaFile.exists()) {
            System.err.println("Error: Schema file not found: ${schemaFile.absolutePath}")
            throw IllegalArgumentException("Schema file not found: ${schemaFile.absolutePath}")
        }

        return ValidateOptions(
            scriptFile = scriptFile,
            schemaFile = schemaFile,
            strict = strict,
            verbose = verbose,
            format = format,
            noTypeCheck = noTypeCheck
        )
    }

    private fun printUsage() {
        println("""
            |Validate UTL-X scripts for correctness
            |
            |Usage:
            |  utlx validate <script-file> [options]
            |
            |Validation Levels:
            |  Level 1: Syntactic validation (parse errors)
            |  Level 2: Semantic validation (type errors, undefined variables)
            |  Level 3: Schema validation (if --schema provided)
            |
            |Arguments:
            |  script-file         UTL-X transformation script to validate
            |
            |Options:
            |  --schema, -s FILE   Input schema file for Level 3 validation (XSD, JSON Schema)
            |  --strict            Treat type warnings as errors (fail on type issues)
            |  --no-typecheck      Skip Level 2 semantic validation (syntax only)
            |  --format, -f FORMAT Output format: human (default), json, compact
            |  -v, --verbose       Enable verbose output
            |  -h, --help          Show this help message
            |
            |Exit Codes:
            |  0   Validation passed
            |  1   Validation failed (errors found)
            |
            |Examples:
            |  # Basic validation (syntax + semantics)
            |  utlx validate transform.utlx
            |
            |  # Validate with input schema (all 3 levels)
            |  utlx validate transform.utlx --schema order.xsd
            |
            |  # Strict mode (treat type warnings as errors)
            |  utlx validate transform.utlx --strict
            |
            |  # Syntax-only validation (no type checking)
            |  utlx validate transform.utlx --no-typecheck
            |
            |  # JSON output for IDE integration
            |  utlx validate transform.utlx --format json
            |
            |  # Verbose mode with schema
            |  utlx validate transform.utlx --schema input.xsd --verbose
            |
            |Supported Schema Formats:
            |  - XSD (XML Schema Definition) - .xsd
            |  - JSON Schema - .json
            |
            |See also:
            |  utlx lint       - Check for style issues and best practices (Level 4)
            |  utlx design     - Design-time analysis and schema generation
        """.trimMargin())
    }

    private fun detectSchemaFormat(filePath: String, content: String): SchemaFormat {
        return when {
            filePath.endsWith(".xsd") -> SchemaFormat.XSD
            filePath.endsWith(".json") -> SchemaFormat.JSON_SCHEMA
            filePath.endsWith(".yaml") || filePath.endsWith(".yml") -> SchemaFormat.YAML_SCHEMA
            content.trimStart().startsWith("<?xml") && content.contains("schema") -> SchemaFormat.XSD
            content.trimStart().startsWith("{") && content.contains("\"\$schema\"") -> SchemaFormat.JSON_SCHEMA
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

    private fun printErrors(
        category: String,
        errors: List<ValidationError>,
        format: ValidationFormat,
        scriptContent: String? = null
    ) {
        if (errors.isEmpty()) return

        when (format) {
            ValidationFormat.HUMAN -> {
                println()
                println("$category:")
                errors.forEach { error ->
                    val symbol = when (error.level) {
                        ErrorLevel.ERROR -> "✗"
                        ErrorLevel.WARNING -> "⚠"
                        ErrorLevel.INFO -> "ℹ"
                    }
                    println("  $symbol ${error.location.line}:${error.location.column} - ${error.message}")

                    // Show code context if available
                    if (scriptContent != null) {
                        val context = extractCodeContext(scriptContent, error.location.line, error.location.column)
                        if (context.isNotEmpty()) {
                            context.forEach { line ->
                                println("    $line")
                            }
                        }
                    }
                }
                println()
            }
            ValidationFormat.JSON -> {
                // JSON format for IDE integration
                val errorsJson = errors.joinToString(",") { error ->
                    """{
                        |  "level":"${error.level.name.lowercase()}",
                        |  "message":"${error.message.replace("\"", "\\\"")}",
                        |  "line":${error.location.line},
                        |  "column":${error.location.column},
                        |  "code":"${error.code}"
                        |}""".trimMargin()
                }
                println("""{"$category":[$errorsJson]}""")
            }
            ValidationFormat.COMPACT -> {
                errors.forEach { error ->
                    val level = error.level.name.first()
                    println("$level:${error.location.line}:${error.location.column}:${error.message}")
                }
            }
        }
    }

    /**
     * Extract code context around an error location for display
     */
    private fun extractCodeContext(
        scriptContent: String,
        errorLine: Int,
        errorColumn: Int,
        contextLines: Int = 2
    ): List<String> {
        val lines = scriptContent.lines()
        if (errorLine < 1 || errorLine > lines.size) {
            return emptyList()
        }

        val result = mutableListOf<String>()

        // Add separator
        result.add("|")

        // Calculate range of lines to show
        val startLine = maxOf(1, errorLine - contextLines)
        val endLine = minOf(lines.size, errorLine + contextLines)

        // Show context lines with line numbers
        for (lineNum in startLine..endLine) {
            val lineContent = lines[lineNum - 1]
            val lineNumStr = lineNum.toString().padStart(3, ' ')

            if (lineNum == errorLine) {
                // Show the error line
                result.add("$lineNumStr | $lineContent")

                // Add error indicator (^) pointing to the column
                val padding = " ".repeat(lineNumStr.length + 3 + maxOf(0, errorColumn - 1))
                result.add("$padding^")
            } else {
                // Show context line
                result.add("$lineNumStr | $lineContent")
            }
        }

        return result
    }

    /**
     * Validation error with location and severity
     */
    data class ValidationError(
        val level: ErrorLevel,
        val message: String,
        val location: org.apache.utlx.core.ast.Location,
        val code: String
    )

    enum class ErrorLevel {
        ERROR,
        WARNING,
        INFO
    }
}
