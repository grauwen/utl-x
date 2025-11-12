package org.apache.utlx.cli.commands

import org.apache.utlx.core.udm.*
import org.apache.utlx.core.parser.Parser
import org.apache.utlx.core.parser.ParseResult
import org.apache.utlx.core.lexer.Lexer
import org.apache.utlx.core.interpreter.Interpreter
import org.apache.utlx.cli.CommandResult
import java.io.File

/**
 * UDM Command - work with UDM Language files
 *
 * Subcommands:
 *   export   - Parse input file and export to .udm format
 *   import   - Load UDM from .udm file
 *   validate - Validate .udm file syntax
 *   format   - Pretty-print/reformat .udm file
 *
 * Usage:
 *   utlx udm export <input-file> <output.udm> [options]
 *   utlx udm import <input.udm>
 *   utlx udm validate <file.udm>
 *   utlx udm format <file.udm> [options]
 */
object UDMCommand {

    fun execute(args: Array<String>): CommandResult {
        if (args.isEmpty()) {
            printUsage()
            return CommandResult.Failure("No subcommand specified", 1)
        }

        val subcommand = args[0]
        val subArgs = args.drop(1).toTypedArray()

        return when (subcommand) {
            "export" -> executeExport(subArgs)
            "import" -> executeImport(subArgs)
            "validate" -> executeValidate(subArgs)
            "format" -> executeFormat(subArgs)
            "-h", "--help" -> {
                printUsage()
                CommandResult.Success
            }
            else -> {
                System.err.println("Error: Unknown subcommand: $subcommand")
                printUsage()
                CommandResult.Failure("Unknown subcommand", 1)
            }
        }
    }

    // ==================== EXPORT ====================

    data class ExportOptions(
        val inputFile: File,
        val outputFile: File,
        val scriptFile: File? = null,
        val inputName: String = "input",
        val prettyPrint: Boolean = true,
        val verbose: Boolean = false
    )

    private fun executeExport(args: Array<String>): CommandResult {
        val options = try {
            parseExportOptions(args)
        } catch (e: IllegalStateException) {
            if (e.message == "HELP_REQUESTED") {
                return CommandResult.Success
            }
            return CommandResult.Failure(e.message ?: "Unknown error", 1)
        } catch (e: IllegalArgumentException) {
            return CommandResult.Failure(e.message ?: "Invalid arguments", 1)
        }

        if (options.verbose) {
            println("UTL-X UDM Export")
            println("Input: ${options.inputFile.absolutePath}")
            println("Output: ${options.outputFile.absolutePath}")
            options.scriptFile?.let { println("Script: ${it.absolutePath}") }
            println()
        }

        try {
            // If script is provided, execute transformation first
            val udm = if (options.scriptFile != null) {
                // Parse and execute transformation
                val scriptContent = options.scriptFile.readText()
                val lexer = Lexer(scriptContent)
                val tokens = lexer.tokenize()
                val parser = Parser(tokens, scriptContent)

                val program = when (val parseResult = parser.parse()) {
                    is ParseResult.Success -> parseResult.program
                    is ParseResult.Failure -> {
                        System.err.println("✗ Script parse error:")
                        parseResult.errors.forEach { error ->
                            System.err.println("  ${error.location.line}:${error.location.column} - ${error.message}")
                        }
                        return CommandResult.Failure("Script parse failed", 1)
                    }
                }

                // Load and parse input
                val inputContent = options.inputFile.readText()
                val inputFormat = detectInputFormat(options.inputFile.name)
                val inputUDM = parseInputToUDM(inputContent, inputFormat)

                // Execute transformation
                val interpreter = Interpreter()
                val result = interpreter.execute(program, mapOf(options.inputName to inputUDM))

                // Convert result to UDM
                runtimeValueToUDM(result)
            } else {
                // Just parse input file to UDM
                val inputContent = options.inputFile.readText()
                val inputFormat = detectInputFormat(options.inputFile.name)
                parseInputToUDM(inputContent, inputFormat)
            }

            // Serialize to UDM Language
            val sourceInfo = mapOf(
                "source" to options.inputFile.name,
                "parsed-at" to java.time.Instant.now().toString()
            )
            val udmString = udm.toUDMLanguage(
                prettyPrint = options.prettyPrint,
                sourceInfo = sourceInfo
            )

            // Write to output file
            options.outputFile.writeText(udmString)

            if (options.verbose) {
                println("✓ Successfully exported to ${options.outputFile.name}")
                println("  UDM type: ${udm::class.simpleName}")
            } else {
                println("✓ Exported to ${options.outputFile.absolutePath}")
            }

            return CommandResult.Success
        } catch (e: Exception) {
            System.err.println("✗ Export failed: ${e.message}")
            if (options.verbose) {
                e.printStackTrace()
            }
            return CommandResult.Failure("Export failed", 1)
        }
    }

    private fun parseExportOptions(args: Array<String>): ExportOptions {
        if (args.isEmpty()) {
            printExportUsage()
            throw IllegalArgumentException("No arguments provided")
        }

        var inputFile: File? = null
        var outputFile: File? = null
        var scriptFile: File? = null
        var inputName = "input"
        var prettyPrint = true
        var verbose = false

        var i = 0
        while (i < args.size) {
            when (args[i]) {
                "--script", "-s" -> {
                    if (i + 1 >= args.size) {
                        System.err.println("Error: --script requires a file path")
                        printExportUsage()
                        throw IllegalArgumentException("--script requires a file path")
                    }
                    scriptFile = File(args[++i])
                }
                "--input-name", "-n" -> {
                    if (i + 1 >= args.size) {
                        System.err.println("Error: --input-name requires a name")
                        printExportUsage()
                        throw IllegalArgumentException("--input-name requires a name")
                    }
                    inputName = args[++i]
                }
                "--compact", "-c" -> {
                    prettyPrint = false
                }
                "-v", "--verbose" -> {
                    verbose = true
                }
                "-h", "--help" -> {
                    printExportUsage()
                    throw IllegalStateException("HELP_REQUESTED")
                }
                else -> {
                    if (!args[i].startsWith("-")) {
                        if (inputFile == null) {
                            inputFile = File(args[i])
                        } else if (outputFile == null) {
                            outputFile = File(args[i])
                        } else {
                            System.err.println("Error: Unexpected argument: ${args[i]}")
                            printExportUsage()
                            throw IllegalArgumentException("Unexpected argument: ${args[i]}")
                        }
                    } else {
                        System.err.println("Error: Unknown option: ${args[i]}")
                        printExportUsage()
                        throw IllegalArgumentException("Unknown option: ${args[i]}")
                    }
                }
            }
            i++
        }

        if (inputFile == null || outputFile == null) {
            System.err.println("Error: Both input and output files are required")
            printExportUsage()
            throw IllegalArgumentException("Both input and output files are required")
        }

        if (!inputFile.exists()) {
            System.err.println("Error: Input file not found: ${inputFile.absolutePath}")
            throw IllegalArgumentException("Input file not found")
        }

        if (scriptFile != null && !scriptFile.exists()) {
            System.err.println("Error: Script file not found: ${scriptFile.absolutePath}")
            throw IllegalArgumentException("Script file not found")
        }

        return ExportOptions(
            inputFile = inputFile,
            outputFile = outputFile,
            scriptFile = scriptFile,
            inputName = inputName,
            prettyPrint = prettyPrint,
            verbose = verbose
        )
    }

    private fun printExportUsage() {
        println("""
            |Export input data to UDM Language format
            |
            |Usage:
            |  utlx udm export <input-file> <output.udm> [options]
            |
            |Arguments:
            |  input-file          Input data file (JSON, XML, CSV, YAML)
            |  output.udm          Output .udm file
            |
            |Options:
            |  --script, -s FILE   UTL-X script to transform before export
            |  --input-name, -n NAME  Input name for script (default: "input")
            |  --compact, -c       Compact output (no pretty-printing)
            |  -v, --verbose       Enable verbose output
            |  -h, --help          Show this help message
            |
            |Examples:
            |  # Export JSON to UDM
            |  utlx udm export data.json data.udm
            |
            |  # Transform and export
            |  utlx udm export input.json output.udm --script transform.utlx
            |
            |  # Compact output
            |  utlx udm export data.json data.udm --compact
        """.trimMargin())
    }

    // ==================== IMPORT ====================

    data class ImportOptions(
        val inputFile: File,
        val verbose: Boolean = false,
        val showStructure: Boolean = false
    )

    private fun executeImport(args: Array<String>): CommandResult {
        val options = try {
            parseImportOptions(args)
        } catch (e: IllegalStateException) {
            if (e.message == "HELP_REQUESTED") {
                return CommandResult.Success
            }
            return CommandResult.Failure(e.message ?: "Unknown error", 1)
        } catch (e: IllegalArgumentException) {
            return CommandResult.Failure(e.message ?: "Invalid arguments", 1)
        }

        if (options.verbose) {
            println("UTL-X UDM Import")
            println("Input: ${options.inputFile.absolutePath}")
            println()
        }

        try {
            val udmString = options.inputFile.readText()
            val udm = UDMLanguageParser.parse(udmString)

            if (options.showStructure) {
                println("UDM Structure:")
                println(describeUDM(udm, indent = 0))
            } else {
                println("✓ Successfully imported ${options.inputFile.name}")
                println("  UDM type: ${udm::class.simpleName}")
            }

            return CommandResult.Success
        } catch (e: UDMParseException) {
            System.err.println("✗ Parse error: ${e.message}")
            return CommandResult.Failure("Parse failed", 1)
        } catch (e: Exception) {
            System.err.println("✗ Import failed: ${e.message}")
            if (options.verbose) {
                e.printStackTrace()
            }
            return CommandResult.Failure("Import failed", 1)
        }
    }

    private fun parseImportOptions(args: Array<String>): ImportOptions {
        if (args.isEmpty()) {
            printImportUsage()
            throw IllegalArgumentException("No arguments provided")
        }

        var inputFile: File? = null
        var verbose = false
        var showStructure = false

        var i = 0
        while (i < args.size) {
            when (args[i]) {
                "--structure", "-t" -> {
                    showStructure = true
                }
                "-v", "--verbose" -> {
                    verbose = true
                }
                "-h", "--help" -> {
                    printImportUsage()
                    throw IllegalStateException("HELP_REQUESTED")
                }
                else -> {
                    if (!args[i].startsWith("-")) {
                        if (inputFile == null) {
                            inputFile = File(args[i])
                        } else {
                            System.err.println("Error: Unexpected argument: ${args[i]}")
                            printImportUsage()
                            throw IllegalArgumentException("Unexpected argument: ${args[i]}")
                        }
                    } else {
                        System.err.println("Error: Unknown option: ${args[i]}")
                        printImportUsage()
                        throw IllegalArgumentException("Unknown option: ${args[i]}")
                    }
                }
            }
            i++
        }

        if (inputFile == null) {
            System.err.println("Error: Input file is required")
            printImportUsage()
            throw IllegalArgumentException("Input file is required")
        }

        if (!inputFile.exists()) {
            System.err.println("Error: Input file not found: ${inputFile.absolutePath}")
            throw IllegalArgumentException("Input file not found")
        }

        return ImportOptions(
            inputFile = inputFile,
            verbose = verbose,
            showStructure = showStructure
        )
    }

    private fun printImportUsage() {
        println("""
            |Import and validate UDM Language file
            |
            |Usage:
            |  utlx udm import <input.udm> [options]
            |
            |Arguments:
            |  input.udm           Input .udm file to import
            |
            |Options:
            |  --structure, -t     Show UDM structure
            |  -v, --verbose       Enable verbose output
            |  -h, --help          Show this help message
            |
            |Examples:
            |  # Import and validate
            |  utlx udm import data.udm
            |
            |  # Show structure
            |  utlx udm import data.udm --structure
        """.trimMargin())
    }

    // ==================== VALIDATE ====================

    data class ValidateOptions(
        val inputFile: File,
        val verbose: Boolean = false
    )

    private fun executeValidate(args: Array<String>): CommandResult {
        val options = try {
            parseValidateOptions(args)
        } catch (e: IllegalStateException) {
            if (e.message == "HELP_REQUESTED") {
                return CommandResult.Success
            }
            return CommandResult.Failure(e.message ?: "Unknown error", 1)
        } catch (e: IllegalArgumentException) {
            return CommandResult.Failure(e.message ?: "Invalid arguments", 1)
        }

        if (options.verbose) {
            println("UTL-X UDM Validate")
            println("Input: ${options.inputFile.absolutePath}")
            println()
        }

        try {
            val udmString = options.inputFile.readText()
            val udm = UDMLanguageParser.parse(udmString)

            println("✓ ${options.inputFile.name} is valid")
            if (options.verbose) {
                println("  UDM type: ${udm::class.simpleName}")
            }

            return CommandResult.Success
        } catch (e: UDMParseException) {
            System.err.println("✗ Validation failed: ${e.message}")
            return CommandResult.Failure("Validation failed", 1)
        } catch (e: Exception) {
            System.err.println("✗ Validation error: ${e.message}")
            if (options.verbose) {
                e.printStackTrace()
            }
            return CommandResult.Failure("Validation failed", 1)
        }
    }

    private fun parseValidateOptions(args: Array<String>): ValidateOptions {
        if (args.isEmpty()) {
            printValidateUsage()
            throw IllegalArgumentException("No arguments provided")
        }

        var inputFile: File? = null
        var verbose = false

        var i = 0
        while (i < args.size) {
            when (args[i]) {
                "-v", "--verbose" -> {
                    verbose = true
                }
                "-h", "--help" -> {
                    printValidateUsage()
                    throw IllegalStateException("HELP_REQUESTED")
                }
                else -> {
                    if (!args[i].startsWith("-")) {
                        if (inputFile == null) {
                            inputFile = File(args[i])
                        } else {
                            System.err.println("Error: Unexpected argument: ${args[i]}")
                            printValidateUsage()
                            throw IllegalArgumentException("Unexpected argument: ${args[i]}")
                        }
                    } else {
                        System.err.println("Error: Unknown option: ${args[i]}")
                        printValidateUsage()
                        throw IllegalArgumentException("Unknown option: ${args[i]}")
                    }
                }
            }
            i++
        }

        if (inputFile == null) {
            System.err.println("Error: Input file is required")
            printValidateUsage()
            throw IllegalArgumentException("Input file is required")
        }

        if (!inputFile.exists()) {
            System.err.println("Error: Input file not found: ${inputFile.absolutePath}")
            throw IllegalArgumentException("Input file not found")
        }

        return ValidateOptions(
            inputFile = inputFile,
            verbose = verbose
        )
    }

    private fun printValidateUsage() {
        println("""
            |Validate UDM Language file syntax
            |
            |Usage:
            |  utlx udm validate <file.udm> [options]
            |
            |Arguments:
            |  file.udm            .udm file to validate
            |
            |Options:
            |  -v, --verbose       Enable verbose output
            |  -h, --help          Show this help message
            |
            |Exit Codes:
            |  0   File is valid
            |  1   Validation failed
            |
            |Examples:
            |  # Validate file
            |  utlx udm validate data.udm
            |
            |  # Verbose validation
            |  utlx udm validate data.udm --verbose
        """.trimMargin())
    }

    // ==================== FORMAT ====================

    data class FormatOptions(
        val inputFile: File,
        val outputFile: File? = null,
        val inPlace: Boolean = false,
        val compact: Boolean = false,
        val verbose: Boolean = false
    )

    private fun executeFormat(args: Array<String>): CommandResult {
        val options = try {
            parseFormatOptions(args)
        } catch (e: IllegalStateException) {
            if (e.message == "HELP_REQUESTED") {
                return CommandResult.Success
            }
            return CommandResult.Failure(e.message ?: "Unknown error", 1)
        } catch (e: IllegalArgumentException) {
            return CommandResult.Failure(e.message ?: "Invalid arguments", 1)
        }

        if (options.verbose) {
            println("UTL-X UDM Format")
            println("Input: ${options.inputFile.absolutePath}")
            if (options.inPlace) {
                println("Mode: In-place")
            } else if (options.outputFile != null) {
                println("Output: ${options.outputFile.absolutePath}")
            }
            println()
        }

        try {
            // Parse input
            val udmString = options.inputFile.readText()
            val udm = UDMLanguageParser.parse(udmString)

            // Serialize with formatting
            val formatted = udm.toUDMLanguage(prettyPrint = !options.compact)

            // Write output
            if (options.inPlace) {
                options.inputFile.writeText(formatted)
                println("✓ Formatted ${options.inputFile.name} in place")
            } else if (options.outputFile != null) {
                options.outputFile.writeText(formatted)
                println("✓ Formatted to ${options.outputFile.name}")
            } else {
                // Print to stdout
                println(formatted)
            }

            return CommandResult.Success
        } catch (e: UDMParseException) {
            System.err.println("✗ Parse error: ${e.message}")
            return CommandResult.Failure("Parse failed", 1)
        } catch (e: Exception) {
            System.err.println("✗ Format failed: ${e.message}")
            if (options.verbose) {
                e.printStackTrace()
            }
            return CommandResult.Failure("Format failed", 1)
        }
    }

    private fun parseFormatOptions(args: Array<String>): FormatOptions {
        if (args.isEmpty()) {
            printFormatUsage()
            throw IllegalArgumentException("No arguments provided")
        }

        var inputFile: File? = null
        var outputFile: File? = null
        var inPlace = false
        var compact = false
        var verbose = false

        var i = 0
        while (i < args.size) {
            when (args[i]) {
                "--output", "-o" -> {
                    if (i + 1 >= args.size) {
                        System.err.println("Error: --output requires a file path")
                        printFormatUsage()
                        throw IllegalArgumentException("--output requires a file path")
                    }
                    outputFile = File(args[++i])
                }
                "--in-place", "-i" -> {
                    inPlace = true
                }
                "--compact", "-c" -> {
                    compact = true
                }
                "-v", "--verbose" -> {
                    verbose = true
                }
                "-h", "--help" -> {
                    printFormatUsage()
                    throw IllegalStateException("HELP_REQUESTED")
                }
                else -> {
                    if (!args[i].startsWith("-")) {
                        if (inputFile == null) {
                            inputFile = File(args[i])
                        } else {
                            System.err.println("Error: Unexpected argument: ${args[i]}")
                            printFormatUsage()
                            throw IllegalArgumentException("Unexpected argument: ${args[i]}")
                        }
                    } else {
                        System.err.println("Error: Unknown option: ${args[i]}")
                        printFormatUsage()
                        throw IllegalArgumentException("Unknown option: ${args[i]}")
                    }
                }
            }
            i++
        }

        if (inputFile == null) {
            System.err.println("Error: Input file is required")
            printFormatUsage()
            throw IllegalArgumentException("Input file is required")
        }

        if (!inputFile.exists()) {
            System.err.println("Error: Input file not found: ${inputFile.absolutePath}")
            throw IllegalArgumentException("Input file not found")
        }

        if (inPlace && outputFile != null) {
            System.err.println("Error: Cannot use both --in-place and --output")
            printFormatUsage()
            throw IllegalArgumentException("Cannot use both --in-place and --output")
        }

        return FormatOptions(
            inputFile = inputFile,
            outputFile = outputFile,
            inPlace = inPlace,
            compact = compact,
            verbose = verbose
        )
    }

    private fun printFormatUsage() {
        println("""
            |Format/pretty-print UDM Language file
            |
            |Usage:
            |  utlx udm format <file.udm> [options]
            |
            |Arguments:
            |  file.udm            .udm file to format
            |
            |Options:
            |  --output, -o FILE   Output file (default: stdout)
            |  --in-place, -i      Format file in place
            |  --compact, -c       Compact format (minimal whitespace)
            |  -v, --verbose       Enable verbose output
            |  -h, --help          Show this help message
            |
            |Examples:
            |  # Format to stdout
            |  utlx udm format data.udm
            |
            |  # Format in place
            |  utlx udm format data.udm --in-place
            |
            |  # Format to new file
            |  utlx udm format data.udm --output formatted.udm
            |
            |  # Compact format
            |  utlx udm format data.udm --compact
        """.trimMargin())
    }

    // ==================== MAIN USAGE ====================

    private fun printUsage() {
        println("""
            |Work with UDM Language files
            |
            |Usage:
            |  utlx udm <subcommand> [options]
            |
            |Subcommands:
            |  export     Parse input file and export to .udm format
            |  import     Load and validate UDM from .udm file
            |  validate   Validate .udm file syntax
            |  format     Pretty-print/reformat .udm file
            |
            |Options:
            |  -h, --help Show this help message
            |
            |Examples:
            |  utlx udm export data.json data.udm
            |  utlx udm import data.udm --structure
            |  utlx udm validate data.udm
            |  utlx udm format data.udm --in-place
            |
            |For subcommand help:
            |  utlx udm <subcommand> --help
        """.trimMargin())
    }

    // ==================== HELPER FUNCTIONS ====================

    private fun detectInputFormat(filename: String): String {
        return when {
            filename.endsWith(".json") -> "json"
            filename.endsWith(".xml") -> "xml"
            filename.endsWith(".csv") -> "csv"
            filename.endsWith(".yaml") || filename.endsWith(".yml") -> "yaml"
            else -> "json" // default
        }
    }

    private fun parseInputToUDM(content: String, format: String): UDM {
        // This would normally use the actual parsers from the core module
        // For now, placeholder that would integrate with existing parsers
        throw NotImplementedError("Input parsing to UDM not yet implemented in CLI - use transformation service")
    }

    private fun runtimeValueToUDM(value: org.apache.utlx.core.interpreter.RuntimeValue): UDM {
        // Convert RuntimeValue to UDM
        throw NotImplementedError("RuntimeValue to UDM conversion not yet implemented")
    }

    private fun describeUDM(udm: UDM, indent: Int): String {
        val spaces = "  ".repeat(indent)
        return when (udm) {
            is UDM.Scalar -> "${spaces}Scalar(${udm.value})"
            is UDM.Array -> {
                val items = udm.elements.joinToString("\n") { describeUDM(it, indent + 1) }
                "${spaces}Array[\n$items\n$spaces]"
            }
            is UDM.Object -> {
                val props = udm.properties.entries.joinToString("\n") { (key, value) ->
                    "$spaces  $key:\n${describeUDM(value, indent + 2)}"
                }
                "${spaces}Object(name=${udm.name})[\n$props\n$spaces]"
            }
            is UDM.DateTime -> "${spaces}DateTime(${udm.instant})"
            is UDM.Date -> "${spaces}Date(${udm.date})"
            is UDM.LocalDateTime -> "${spaces}LocalDateTime(${udm.dateTime})"
            is UDM.Time -> "${spaces}Time(${udm.time})"
            is UDM.Binary -> "${spaces}Binary(${udm.data.size} bytes)"
            is UDM.Lambda -> "${spaces}Lambda"
        }
    }
}
