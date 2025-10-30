// modules/cli/src/main/kotlin/org/apache/utlx/cli/commands/SchemaCommand.kt
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
 * Schema generation and validation commands for UTL-X CLI
 * 
 * Implements the schema analysis capabilities described in analysis_module_readme.md
 */
object SchemaCommand {
    
    fun execute(args: Array<String>) {
        if (args.isEmpty()) {
            printUsage()
            exitProcess(1)
        }
        
        val subcommand = args[0]
        val subArgs = args.drop(1).toTypedArray()
        
        try {
            when (subcommand.lowercase()) {
                "generate", "gen" -> executeGenerate(subArgs)
                "validate", "val" -> executeValidate(subArgs)
                "infer", "inf" -> executeInfer(subArgs)
                "help", "--help", "-h" -> printUsage()
                else -> {
                    System.err.println("Unknown schema subcommand: $subcommand")
                    printUsage()
                    exitProcess(1)
                }
            }
        } catch (e: Exception) {
            System.err.println("Schema command error: ${e.message}")
            if (System.getProperty("utlx.debug") == "true") {
                e.printStackTrace()
            }
            exitProcess(1)
        }
    }
    
    /**
     * Generate output schema from input schema and transformation
     */
    private fun executeGenerate(args: Array<String>) {
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
            println("Generating schema from transformation...")
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
            // Generate with input schema
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
                println("✓ Schema generated and saved to: $outputFile")
            }
        } else {
            println(outputSchema)
        }
    }
    
    /**
     * Validate transformation against expected output schema
     */
    private fun executeValidate(args: Array<String>) {
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
            throw IllegalArgumentException("Input schema file is required for validation")
        }
        if (expectedOutputFile == null) {
            throw IllegalArgumentException("Expected output schema file is required for validation")
        }
        
        if (verbose) {
            println("Validating transformation...")
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
            println("✓ Validation successful")
            if (verbose && result.warnings.isNotEmpty()) {
                println("\nWarnings:")
                result.warnings.forEach { println("  ⚠ $it") }
            }
        } else {
            println("✗ Validation failed")
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
            |UTL-X Schema Analysis Commands
            |
            |Usage: utlx schema <subcommand> [options]
            |
            |Subcommands:
            |  generate (gen)  Generate output schema from transformation and input schema
            |  validate (val)  Validate transformation against expected output schema
            |  infer    (inf)  Infer schema from transformation without input schema
            |  help            Show this help message
            |
            |Generate Examples:
            |  # From XSD to JSON Schema
            |  utlx schema generate --input-schema order.xsd --transform order-to-invoice.utlx --output-format json-schema --output invoice-schema.json
            |
            |  # From JSON Schema to XSD (not yet implemented)
            |  utlx schema generate --input-schema customer.json --transform process-customer.utlx --output-format xsd --output customer-output.xsd
            |
            |  # Infer without input schema
            |  utlx schema generate --transform data-processor.utlx --output-format json-schema
            |
            |Validate Examples:
            |  utlx schema validate --input-schema order.xsd --transform order-to-invoice.utlx --expected-output invoice-schema.json --verbose
            |
            |Infer Examples:
            |  utlx schema infer --transform data-processor.utlx --output-format json-schema --output inferred-schema.json
            |
            |Supported Schema Formats:
            |  Input:  xsd, json-schema
            |  Output: json-schema (xsd, csv-schema, yaml-schema planned)
            |
            |Global Options:
            |  --verbose, -v     Enable verbose output
            |  --output, -o      Write output to file
            |  --no-pretty       Disable pretty-printing
            |  --no-comments     Disable comments in generated schemas
            |  --no-strict       Disable strict mode validation
        """.trimMargin())
    }
}