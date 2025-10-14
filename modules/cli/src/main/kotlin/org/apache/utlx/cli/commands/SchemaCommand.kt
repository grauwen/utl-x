// modules/cli/src/main/kotlin/org/apache/utlx/cli/commands/SchemaCommand.kt
package org.apache.utlx.cli.commands

import org.apache.utlx.analysis.schema.*
import org.apache.utlx.core.parser.Parser
import java.io.File

/**
 * CLI command for schema operations
 * 
 * Usage:
 *   utlx schema generate <options>
 *   utlx schema validate <options>
 *   utlx schema infer <options>
 *   utlx schema diff <options>
 */
object SchemaCommand {
    
    fun execute(args: Array<String>) {
        if (args.isEmpty()) {
            printUsage()
            return
        }
        
        when (args[0].lowercase()) {
            "generate" -> handleGenerate(args.drop(1).toTypedArray())
            "validate" -> handleValidate(args.drop(1).toTypedArray())
            "infer" -> handleInfer(args.drop(1).toTypedArray())
            "diff" -> handleDiff(args.drop(1).toTypedArray())
            "document" -> handleDocument(args.drop(1).toTypedArray())
            "help", "-h", "--help" -> printUsage()
            else -> {
                println("Unknown schema command: ${args[0]}")
                printUsage()
            }
        }
    }
    
    private fun handleGenerate(args: Array<String>) {
        var inputSchemaFile: File? = null
        var transformFile: File? = null
        var outputFile: File? = null
        var inputFormat: SchemaFormat? = null
        var outputFormat: SchemaFormat = SchemaFormat.JSON_SCHEMA
        var pretty = true
        var includeComments = true
        
        var i = 0
        while (i < args.size) {
            when (args[i]) {
                "--input-schema", "-i" -> {
                    inputSchemaFile = File(args[++i])
                }
                "--transform", "-t" -> {
                    transformFile = File(args[++i])
                }
                "--output", "-o" -> {
                    outputFile = File(args[++i])
                }
                "--input-format" -> {
                    inputFormat = SchemaFormat.valueOf(args[++i].uppercase())
                }
                "--output-format", "-f" -> {
                    outputFormat = SchemaFormat.valueOf(args[++i].uppercase())
                }
                "--no-pretty" -> pretty = false
                "--no-comments" -> includeComments = false
                else -> {
                    println("Unknown option: ${args[i]}")
                    return
                }
            }
            i++
        }
        
        // Validate required arguments
        if (transformFile == null) {
            println("Error: --transform is required")
            return
        }
        
        if (!transformFile.exists()) {
            println("Error: Transform file not found: ${transformFile.absolutePath}")
            return
        }
        
        if (inputSchemaFile != null && !inputSchemaFile.exists()) {
            println("Error: Input schema file not found: ${inputSchemaFile.absolutePath}")
            return
        }
        
        try {
            println("Generating schema...")
            println("  Transform: ${transformFile.name}")
            if (inputSchemaFile != null) {
                println("  Input schema: ${inputSchemaFile.name}")
            }
            println("  Output format: ${outputFormat.name.lowercase()}")
            println()
            
            // Parse transformation
            val parser = Parser()
            val transformContent = transformFile.readText()
            val program = parser.parse(transformContent)
            
            // Generate schema
            val schemaGen = SchemaGenerator()
            val outputSchema = if (inputSchemaFile != null) {
                // Auto-detect input format if not specified
                val detectedFormat = inputFormat ?: detectSchemaFormat(inputSchemaFile)
                val inputSchemaContent = inputSchemaFile.readText()
                
                schemaGen.generate(
                    transformation = program,
                    inputSchemaContent = inputSchemaContent,
                    inputSchemaFormat = detectedFormat,
                    outputSchemaFormat = outputFormat,
                    options = GeneratorOptions(
                        pretty = pretty,
                        includeComments = includeComments
                    )
                )
            } else {
                // Infer without input schema
                schemaGen.inferSchema(
                    transformation = program,
                    outputSchemaFormat = outputFormat,
                    options = GeneratorOptions(
                        pretty = pretty,
                        includeComments = includeComments
                    )
                )
            }
            
            // Write or print output
            if (outputFile != null) {
                outputFile.writeText(outputSchema)
                println("✓ Schema generated: ${outputFile.absolutePath}")
            } else {
                println("Generated Schema:")
                println("-".repeat(60))
                println(outputSchema)
            }
            
        } catch (e: Exception) {
            println("Error generating schema: ${e.message}")
            e.printStackTrace()
        }
    }
    
    private fun handleValidate(args: Array<String>) {
        var inputSchemaFile: File? = null
        var transformFile: File? = null
        var expectedOutputFile: File? = null
        var inputFormat: SchemaFormat? = null
        var outputFormat: SchemaFormat? = null
        var verbose = false
        
        var i = 0
        while (i < args.size) {
            when (args[i]) {
                "--input-schema", "-i" -> {
                    inputSchemaFile = File(args[++i])
                }
                "--transform", "-t" -> {
                    transformFile = File(args[++i])
                }
                "--expected-output", "-e" -> {
                    expectedOutputFile = File(args[++i])
                }
                "--input-format" -> {
                    inputFormat = SchemaFormat.valueOf(args[++i].uppercase())
                }
                "--output-format" -> {
                    outputFormat = SchemaFormat.valueOf(args[++i].uppercase())
                }
                "--verbose", "-v" -> verbose = true
                else -> {
                    println("Unknown option: ${args[i]}")
                    return
                }
            }
            i++
        }
        
        // Validate required arguments
        if (inputSchemaFile == null || transformFile == null) {
            println("Error: --input-schema and --transform are required")
            return
        }
        
        try {
            println("Validating transformation...")
            println("  Input schema: ${inputSchemaFile.name}")
            println("  Transform: ${transformFile.name}")
            if (expectedOutputFile != null) {
                println("  Expected output: ${expectedOutputFile.name}")
            }
            println()
            
            // Parse input schema
            val detectedInputFormat = inputFormat ?: detectSchemaFormat(inputSchemaFile)
            val inputSchemaContent = inputSchemaFile.readText()
            
            val parser = when (detectedInputFormat) {
                SchemaFormat.XSD -> XSDSchemaParser()
                SchemaFormat.JSON_SCHEMA -> JSONSchemaParser()
                else -> throw IllegalArgumentException("Unsupported input format")
            }
            val inputType = parser.parse(inputSchemaContent)
            
            // Parse transformation
            val utlxParser = Parser()
            val program = utlxParser.parse(transformFile.readText())
            
            // Parse expected output schema if provided
            val expectedOutputType = if (expectedOutputFile != null) {
                val detectedOutputFormat = outputFormat ?: detectSchemaFormat(expectedOutputFile)
                val outputParser = when (detectedOutputFormat) {
                    SchemaFormat.XSD -> XSDSchemaParser()
                    SchemaFormat.JSON_SCHEMA -> JSONSchemaParser()
                    else -> throw IllegalArgumentException("Unsupported output format")
                }
                outputParser.parse(expectedOutputFile.readText())
            } else {
                null
            }
            
            // Validate
            val validator = TransformValidator()
            val result = validator.validate(program, inputType, expectedOutputType)
            
            if (result.isValid) {
                println("✓ Validation successful!")
                if (verbose && result.warnings.isNotEmpty()) {
                    println("\nWarnings:")
                    result.warnings.forEach { warning ->
                        println("  ⚠ $warning")
                    }
                }
            } else {
                println("✗ Validation failed!")
                println("\nErrors:")
                result.errors.forEach { error ->
                    println("  ✗ $error")
                }
                if (result.warnings.isNotEmpty()) {
                    println("\nWarnings:")
                    result.warnings.forEach { warning ->
                        println("  ⚠ $warning")
                    }
                }
            }
            
        } catch (e: Exception) {
            println("Error validating transformation: ${e.message}")
            e.printStackTrace()
        }
    }
    
    private fun handleInfer(args: Array<String>) {
        var transformFile: File? = null
        var outputFile: File? = null
        var outputFormat: SchemaFormat = SchemaFormat.JSON_SCHEMA
        var pretty = true
        
        var i = 0
        while (i < args.size) {
            when (args[i]) {
                "--transform", "-t" -> {
                    transformFile = File(args[++i])
                }
                "--output", "-o" -> {
                    outputFile = File(args[++i])
                }
                "--output-format", "-f" -> {
                    outputFormat = SchemaFormat.valueOf(args[++i].uppercase())
                }
                "--no-pretty" -> pretty = false
                else -> {
                    println("Unknown option: ${args[i]}")
                    return
                }
            }
            i++
        }
        
        if (transformFile == null) {
            println("Error: --transform is required")
            return
        }
        
        try {
            println("Inferring output schema from transformation...")
            println("  Transform: ${transformFile.name}")
            println("  Output format: ${outputFormat.name.lowercase()}")
            println()
            
            // Parse transformation
            val parser = Parser()
            val program = parser.parse(transformFile.readText())
            
            // Infer schema
            val schemaGen = SchemaGenerator()
            val outputSchema = schemaGen.inferSchema(
                transformation = program,
                outputSchemaFormat = outputFormat,
                options = GeneratorOptions(pretty = pretty)
            )
            
            // Write or print output
            if (outputFile != null) {
                outputFile.writeText(outputSchema)
                println("✓ Schema inferred: ${outputFile.absolutePath}")
            } else {
                println("Inferred Schema:")
                println("-".repeat(60))
                println(outputSchema)
            }
            
            println("\nNote: Schema inferred without input constraints may be less precise.")
            
        } catch (e: Exception) {
            println("Error inferring schema: ${e.message}")
            e.printStackTrace()
        }
    }
    
    private fun handleDiff(args: Array<String>) {
        var oldSchemaFile: File? = null
        var newSchemaFile: File? = null
        var outputFile: File? = null
        var format: SchemaFormat? = null
        
        var i = 0
        while (i < args.size) {
            when (args[i]) {
                "--old-schema" -> {
                    oldSchemaFile = File(args[++i])
                }
                "--new-schema" -> {
                    newSchemaFile = File(args[++i])
                }
                "--output", "-o" -> {
                    outputFile = File(args[++i])
                }
                "--format" -> {
                    format = SchemaFormat.valueOf(args[++i].uppercase())
                }
                else -> {
                    println("Unknown option: ${args[i]}")
                    return
                }
            }
            i++
        }
        
        if (oldSchemaFile == null || newSchemaFile == null) {
            println("Error: --old-schema and --new-schema are required")
            return
        }
        
        try {
            println("Comparing schemas...")
            println("  Old: ${oldSchemaFile.name}")
            println("  New: ${newSchemaFile.name}")
            println()
            
            // Parse both schemas
            val detectedFormat = format ?: detectSchemaFormat(oldSchemaFile)
            val parser = when (detectedFormat) {
                SchemaFormat.XSD -> XSDSchemaParser()
                SchemaFormat.JSON_SCHEMA -> JSONSchemaParser()
                else -> throw IllegalArgumentException("Unsupported format")
            }
            
            val oldType = parser.parse(oldSchemaFile.readText())
            val newType = parser.parse(newSchemaFile.readText())
            
            // Compare
            val differ = SchemaDiffer()
            val diff = differ.diff(oldType, newType)
            
            // Generate report
            val report = buildString {
                appendLine("Schema Comparison Report")
                appendLine("=".repeat(60))
                appendLine()
                
                if (diff.breakingChanges.isEmpty() && diff.additions.isEmpty() && diff.removals.isEmpty()) {
                    appendLine("✓ No changes detected")
                } else {
                    if (diff.breakingChanges.isNotEmpty()) {
                        appendLine("Breaking Changes:")
                        diff.breakingChanges.forEach { change ->
                            appendLine("  ✗ $change")
                        }
                        appendLine()
                    }
                    
                    if (diff.removals.isNotEmpty()) {
                        appendLine("Removals:")
                        diff.removals.forEach { removal ->
                            appendLine("  - $removal")
                        }
                        appendLine()
                    }
                    
                    if (diff.additions.isNotEmpty()) {
                        appendLine("Additions:")
                        diff.additions.forEach { addition ->
                            appendLine("  + $addition")
                        }
                        appendLine()
                    }
                }
            }
            
            // Write or print report
            if (outputFile != null) {
                outputFile.writeText(report)
                println("✓ Diff report generated: ${outputFile.absolutePath}")
            } else {
                println(report)
            }
            
        } catch (e: Exception) {
            println("Error comparing schemas: ${e.message}")
            e.printStackTrace()
        }
    }
    
    private fun handleDocument(args: Array<String>) {
        println("Schema documentation generation not yet implemented")
        // Would generate HTML/Markdown documentation from schemas and transformations
    }
    
    private fun detectSchemaFormat(file: File): SchemaFormat {
        return when (file.extension.lowercase()) {
            "xsd" -> SchemaFormat.XSD
            "json" -> SchemaFormat.JSON_SCHEMA
            "csv" -> SchemaFormat.CSV_SCHEMA
            "yaml", "yml" -> SchemaFormat.YAML_SCHEMA
            else -> throw IllegalArgumentException("Cannot detect schema format for: ${file.name}")
        }
    }
    
    private fun printUsage() {
        println("""
            UTL-X Schema Commands
            
            Usage: utlx schema <command> [options]
            
            Commands:
              generate      Generate output schema from transformation and input schema
              validate      Validate transformation against schemas
              infer         Infer output schema from transformation (without input schema)
              diff          Compare two schemas and show differences
              document      Generate documentation from schemas (coming soon)
            
            Examples:
            
              # Generate JSON Schema from XSD + transformation
              utlx schema generate \
                --input-schema order.xsd \
                --transform order-to-invoice.utlx \
                --output-format json-schema \
                --output invoice-schema.json
            
              # Validate transformation
              utlx schema validate \
                --input-schema order.xsd \
                --transform order-to-invoice.utlx \
                --expected-output invoice-schema.json
            
              # Infer schema without input
              utlx schema infer \
                --transform process-data.utlx \
                --output-format xsd \
                --output output-schema.xsd
            
              # Compare schemas
              utlx schema diff \
                --old-schema invoice-v1.json \
                --new-schema invoice-v2.json \
                --output diff-report.txt
            
            For more information: https://utlx.dev/docs/schema
        """.trimIndent())
    }
}

// Supporting classes (simplified examples)
data class ValidationResult(
    val isValid: Boolean,
    val errors: List<String> = emptyList(),
    val warnings: List<String> = emptyList()
)

class TransformValidator {
    fun validate(
        program: org.apache.utlx.core.ast.Program,
        inputType: TypeDefinition,
        expectedOutputType: TypeDefinition?
    ): ValidationResult {
        // Implementation would check:
        // 1. All paths referenced in transformation exist in input schema
        // 2. Type operations are valid (e.g., can't add string + number)
        // 3. Output matches expected schema if provided
        
        return ValidationResult(isValid = true)
    }
}

data class SchemaDiff(
    val breakingChanges: List<String>,
    val additions: List<String>,
    val removals: List<String>
)

class SchemaDiffer {
    fun diff(oldType: TypeDefinition, newType: TypeDefinition): SchemaDiff {
        // Implementation would compare types and categorize changes
        return SchemaDiff(
            breakingChanges = emptyList(),
            additions = emptyList(),
            removals = emptyList()
        )
    }
}
