package org.apache.utlx.cli.commands

import org.apache.utlx.core.udm.*
import org.apache.utlx.cli.CommandResult
import org.apache.utlx.formats.json.JSONParser
import org.apache.utlx.formats.json.JSONSerializer
import org.apache.utlx.formats.xml.XMLParser
import org.apache.utlx.formats.xml.XMLSerializer
import org.apache.utlx.formats.csv.CSVParser
import org.apache.utlx.formats.csv.CSVSerializer
import org.apache.utlx.formats.csv.CSVDialect
import org.apache.utlx.formats.csv.RegionalFormat
import org.apache.utlx.formats.yaml.YAMLParser
import org.apache.utlx.formats.yaml.YAMLSerializer
import org.apache.utlx.formats.jsch.JSONSchemaParser
import org.apache.utlx.formats.jsch.JSONSchemaSerializer
import org.apache.utlx.formats.xsd.XSDParser
import org.apache.utlx.formats.xsd.XSDSerializer
import org.apache.utlx.formats.avro.AvroSchemaParser
import org.apache.utlx.formats.avro.AvroSchemaSerializer
import org.apache.utlx.formats.protobuf.ProtobufSchemaParser
import org.apache.utlx.formats.protobuf.ProtobufSchemaSerializer
import java.io.File

/**
 * UDM Command - work with UDM Language files (Unix-style with stdin/stdout support)
 *
 * Subcommands:
 *   export   - Parse input format and export to .udm format
 *   import   - Import UDM from .udm file and convert to format
 *   validate - Validate .udm file syntax
 *   format   - Pretty-print/reformat .udm file
 *
 * Usage:
 *   utlx udm export --format <format> [--input <file>] [--output <file>] [options]
 *   utlx udm import --format <format> [--input <file>] [--output <file>] [options]
 *   utlx udm validate [file.udm]
 *   utlx udm format [--input <file>] [--output <file>] [options]
 */
object UDMCommand {

    private val SUPPORTED_FORMATS = setOf(
        "json", "xml", "csv", "yaml",
        "jsonschema", "xsd", "avro", "protobuf"
    )

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

    // ==================== STDIN/STDOUT HELPERS ====================

    /**
     * Read input from file or stdin
     */
    private fun readInput(inputFile: String?): String {
        return if (inputFile != null) {
            File(inputFile).readText()
        } else {
            // Read from stdin
            System.`in`.bufferedReader().use { it.readText() }
        }
    }

    /**
     * Write output to file or stdout
     */
    private fun writeOutput(outputFile: String?, content: String) {
        if (outputFile != null) {
            File(outputFile).writeText(content)
        } else {
            // Write to stdout
            print(content)
        }
    }

    /**
     * Get required option value
     */
    private fun getRequiredOption(args: Array<String>, flag: String): String {
        val index = args.indexOf(flag)
        if (index == -1) {
            throw IllegalArgumentException("Required flag $flag is missing")
        }
        if (index + 1 >= args.size) {
            throw IllegalArgumentException("Flag $flag requires a value")
        }
        return args[index + 1]
    }

    /**
     * Get optional option value
     */
    private fun getOptionValue(args: Array<String>, flag: String): String? {
        val index = args.indexOf(flag)
        if (index == -1) return null
        if (index + 1 >= args.size) {
            throw IllegalArgumentException("Flag $flag requires a value")
        }
        return args[index + 1]
    }

    /**
     * Check if flag is present
     */
    private fun hasFlag(args: Array<String>, vararg flags: String): Boolean {
        return flags.any { it in args }
    }

    // ==================== EXPORT ====================

    private fun executeExport(args: Array<String>): CommandResult {
        try {
            // Show help
            if (hasFlag(args, "-h", "--help")) {
                printExportUsage()
                return CommandResult.Success
            }

            // Parse required format flag
            val format = try {
                getRequiredOption(args, "--format")
            } catch (e: IllegalArgumentException) {
                System.err.println("Error: ${e.message}")
                System.err.println()
                printExportUsage()
                return CommandResult.Failure(e.message ?: "Invalid arguments", 1)
            }

            // Validate format
            if (format.lowercase() !in SUPPORTED_FORMATS) {
                System.err.println("Error: Unsupported format: $format")
                System.err.println("Supported formats: ${SUPPORTED_FORMATS.joinToString(", ")}")
                return CommandResult.Failure("Unsupported format", 1)
            }

            // Parse I/O flags
            val inputFile = getOptionValue(args, "--input")
            val outputFile = getOptionValue(args, "--output")
            val verbose = hasFlag(args, "-v", "--verbose")

            // Parse format-specific options
            val formatOptions = parseFormatOptions(args, format, isExport = true)

            // Verbose output to stderr
            if (verbose) {
                if (inputFile == null) {
                    System.err.println("Reading from stdin...")
                } else {
                    System.err.println("Reading from $inputFile...")
                }
                System.err.println("Format: $format")
            }

            // Read input (stdin or file)
            val content = readInput(inputFile)

            // Parse format → UDM
            if (verbose) {
                System.err.println("Parsing $format...")
            }
            val udm = parseInputToUDM(content, format, formatOptions)

            // Serialize UDM → .udm
            if (verbose) {
                System.err.println("Serializing to UDM Language...")
            }
            val sourceInfo = if (inputFile != null) {
                mapOf(
                    "source" to File(inputFile).name,
                    "parsed-at" to java.time.Instant.now().toString()
                )
            } else {
                mapOf("parsed-at" to java.time.Instant.now().toString())
            }
            val udmLang = udm.toUDMLanguage(prettyPrint = true, sourceInfo = sourceInfo)

            // Write output (stdout or file)
            writeOutput(outputFile, udmLang)

            // Verbose completion to stderr
            if (verbose) {
                if (outputFile != null) {
                    System.err.println("✓ Exported to $outputFile")
                } else {
                    System.err.println("✓ Exported to stdout")
                }
            }

            return CommandResult.Success
        } catch (e: Exception) {
            System.err.println("✗ Export failed: ${e.message}")
            return CommandResult.Failure("Export failed", 1)
        }
    }

    private fun printExportUsage() {
        println("""
            |Export data from format to UDM Language (.udm)
            |
            |USAGE:
            |  utlx udm export --format <format> [OPTIONS]
            |
            |REQUIRED FLAGS:
            |  --format <format>     Input data format
            |                        Formats: json, xml, csv, yaml, jsonschema, xsd, avro, protobuf
            |
            |OPTIONAL FLAGS:
            |  --input <file>        Input file path (default: stdin)
            |  --output <file>       Output .udm file path (default: stdout)
            |  -v, --verbose         Verbose output to stderr
            |
            |FORMAT-SPECIFIC OPTIONS:
            |  CSV:
            |    --csv-delimiter <char>     Delimiter: comma, semicolon, tab, pipe (default: comma)
            |    --csv-headers              Include headers (default)
            |    --csv-no-headers           Exclude headers
            |    --csv-format <region>      Regional format: usa, european, french, swiss, none (default: none)
            |
            |  XML:
            |    --xml-array-hints <list>   Comma-separated elements to treat as arrays (e.g., "Item,Product")
            |
            |  XSD:
            |    --xsd-array-hints <list>   Comma-separated elements to treat as arrays
            |
            |  YAML:
            |    --yaml-multi-doc           Parse as multi-document YAML (default: false)
            |
            |EXAMPLES:
            |  # Export from file to file:
            |  utlx udm export --format json --input data.json --output data.udm
            |
            |  # Export from stdin to stdout:
            |  cat data.json | utlx udm export --format json > data.udm
            |
            |  # Export CSV with options:
            |  utlx udm export --format csv --input data.csv --csv-delimiter ";" --csv-headers > data.udm
            |
            |  # Chain with API:
            |  curl -s https://api.example.com/data.json | utlx udm export --format json | tee data.udm
            |
            |STDIN/STDOUT BEHAVIOR:
            |  - If --input is omitted, reads from stdin
            |  - If --output is omitted, writes to stdout
            |  - Both flags can be omitted for full pipe support
        """.trimMargin())
    }

    // ==================== IMPORT ====================

    private fun executeImport(args: Array<String>): CommandResult {
        try {
            // Show help
            if (hasFlag(args, "-h", "--help")) {
                printImportUsage()
                return CommandResult.Success
            }

            // Parse required format flag
            val format = try {
                getRequiredOption(args, "--format")
            } catch (e: IllegalArgumentException) {
                System.err.println("Error: ${e.message}")
                System.err.println()
                printImportUsage()
                return CommandResult.Failure(e.message ?: "Invalid arguments", 1)
            }

            // Validate format
            if (format.lowercase() !in SUPPORTED_FORMATS) {
                System.err.println("Error: Unsupported format: $format")
                System.err.println("Supported formats: ${SUPPORTED_FORMATS.joinToString(", ")}")
                return CommandResult.Failure("Unsupported format", 1)
            }

            // Parse I/O flags
            val inputFile = getOptionValue(args, "--input")
            val outputFile = getOptionValue(args, "--output")
            val verbose = hasFlag(args, "-v", "--verbose")

            // Parse format-specific options
            val formatOptions = parseFormatOptions(args, format, isExport = false)

            // Verbose output to stderr
            if (verbose) {
                if (inputFile == null) {
                    System.err.println("Reading from stdin...")
                } else {
                    System.err.println("Reading from $inputFile...")
                }
                System.err.println("Format: $format")
            }

            // Read .udm input (stdin or file)
            val udmContent = readInput(inputFile)

            // Parse .udm → UDM
            if (verbose) {
                System.err.println("Parsing UDM Language...")
            }
            val udm = UDMLanguageParser.parse(udmContent)

            // Serialize UDM → format
            if (verbose) {
                System.err.println("Serializing to $format...")
            }
            val output = serializeUDMToFormat(udm, format, formatOptions)

            // Write output (stdout or file)
            writeOutput(outputFile, output)

            // Verbose completion to stderr
            if (verbose) {
                if (outputFile != null) {
                    System.err.println("✓ Converted to $outputFile")
                } else {
                    System.err.println("✓ Converted to stdout")
                }
            }

            return CommandResult.Success
        } catch (e: UDMParseException) {
            System.err.println("✗ Parse error: ${e.message}")
            return CommandResult.Failure("Parse failed", 1)
        } catch (e: Exception) {
            System.err.println("✗ Import failed: ${e.message}")
            return CommandResult.Failure("Import failed", 1)
        }
    }

    private fun printImportUsage() {
        println("""
            |Import UDM Language (.udm) to format
            |
            |USAGE:
            |  utlx udm import --format <format> [OPTIONS]
            |
            |REQUIRED FLAGS:
            |  --format <format>     Output data format
            |                        Formats: json, xml, csv, yaml, jsonschema, xsd, avro, protobuf
            |
            |OPTIONAL FLAGS:
            |  --input <file>        Input .udm file path (default: stdin)
            |  --output <file>       Output file path (default: stdout)
            |  -v, --verbose         Verbose output to stderr
            |
            |FORMAT-SPECIFIC OPTIONS:
            |  CSV:
            |    --csv-delimiter <char>     Delimiter: comma, semicolon, tab, pipe (default: comma)
            |    --csv-headers              Include headers (default)
            |    --csv-no-headers           Exclude headers
            |    --csv-format <region>      Regional format: usa, european, french, swiss (default: none)
            |
            |  XML:
            |    --xml-root <name>          Root element name (default: "root")
            |    --xml-encoding <enc>       Output encoding: UTF-8, UTF-16, NONE (default: UTF-8)
            |
            |  JSON Schema:
            |    --jsch-draft <version>     JSON Schema draft: draft-07, 2019-09, 2020-12 (default: 2020-12)
            |
            |  XSD:
            |    --xsd-pattern <pattern>    Design pattern: russian-doll, salami-slice, venetian-blind, garden-of-eden
            |    --xsd-version <version>    XSD version: 1.0, 1.1 (default: 1.0)
            |    --xsd-namespace <uri>      targetNamespace URI (e.g., "http://example.com/order")
            |
            |  Avro:
            |    --avro-namespace <ns>      Namespace for schema (e.g., "com.example")
            |    --avro-validate            Validate with Apache Avro library (default: true)
            |
            |EXAMPLES:
            |  # Import from file to file:
            |  utlx udm import --format json --input data.udm --output result.json
            |
            |  # Import from stdin to stdout:
            |  cat data.udm | utlx udm import --format json > result.json
            |
            |  # Import to XML with options:
            |  utlx udm import --format xml --input data.udm --xml-root "Order" > result.xml
            |
            |  # Import to XSD with targetNamespace:
            |  utlx udm import --format xsd --input data.udm --xsd-namespace "http://example.com/order" > schema.xsd
            |
            |  # Round-trip test:
            |  cat original.json | utlx udm export --format json | utlx udm import --format json | jq .
            |
            |STDIN/STDOUT BEHAVIOR:
            |  - If --input is omitted, reads from stdin
            |  - If --output is omitted, writes to stdout
            |  - Both flags can be omitted for full pipe support
        """.trimMargin())
    }

    // ==================== VALIDATE ====================

    private fun executeValidate(args: Array<String>): CommandResult {
        try {
            // Show help
            if (hasFlag(args, "-h", "--help")) {
                printValidateUsage()
                return CommandResult.Success
            }

            // Parse input file (optional - if not provided, use stdin)
            val inputFile = args.find { !it.startsWith("-") }
            val verbose = hasFlag(args, "-v", "--verbose")

            if (verbose && inputFile == null) {
                System.err.println("Reading from stdin...")
            }

            // Read input (stdin or file)
            val content = readInput(inputFile)

            // Parse and validate
            val udm = UDMLanguageParser.parse(content)

            // Success message
            if (inputFile != null) {
                println("✓ $inputFile is valid")
            } else {
                println("✓ Valid UDM Language file")
            }

            if (verbose) {
                System.err.println("  UDM type: ${udm::class.simpleName}")
            }

            return CommandResult.Success
        } catch (e: UDMParseException) {
            System.err.println("✗ Validation failed: ${e.message}")
            return CommandResult.Failure("Validation failed", 1)
        } catch (e: Exception) {
            System.err.println("✗ Validation error: ${e.message}")
            return CommandResult.Failure("Validation failed", 1)
        }
    }

    private fun printValidateUsage() {
        println("""
            |Validate UDM Language file syntax
            |
            |USAGE:
            |  utlx udm validate [file.udm] [OPTIONS]
            |
            |ARGUMENTS:
            |  file.udm              .udm file to validate (default: stdin)
            |
            |OPTIONS:
            |  -v, --verbose         Enable verbose output
            |  -h, --help            Show this help message
            |
            |EXIT CODES:
            |  0   File is valid
            |  1   Validation failed
            |
            |EXAMPLES:
            |  # Validate file:
            |  utlx udm validate data.udm
            |
            |  # Validate from stdin:
            |  cat data.udm | utlx udm validate
            |
            |  # Verbose validation:
            |  utlx udm validate data.udm --verbose
        """.trimMargin())
    }

    // ==================== FORMAT ====================

    private fun executeFormat(args: Array<String>): CommandResult {
        try {
            // Show help
            if (hasFlag(args, "-h", "--help")) {
                printFormatUsage()
                return CommandResult.Success
            }

            // Parse flags
            val inputFile = getOptionValue(args, "--input")
            val outputFile = getOptionValue(args, "--output")
            val compact = hasFlag(args, "--compact", "-c")
            val verbose = hasFlag(args, "-v", "--verbose")

            // Verbose output to stderr
            if (verbose && inputFile == null) {
                System.err.println("Reading from stdin...")
            }

            // Read input (stdin or file)
            val content = readInput(inputFile)

            // Parse and reformat
            val udm = UDMLanguageParser.parse(content)
            val formatted = udm.toUDMLanguage(prettyPrint = !compact)

            // Write output (stdout or file)
            writeOutput(outputFile, formatted)

            // Verbose completion to stderr
            if (verbose) {
                if (outputFile != null) {
                    System.err.println("✓ Formatted to $outputFile")
                } else {
                    System.err.println("✓ Formatted to stdout")
                }
            }

            return CommandResult.Success
        } catch (e: UDMParseException) {
            System.err.println("✗ Parse error: ${e.message}")
            return CommandResult.Failure("Parse failed", 1)
        } catch (e: Exception) {
            System.err.println("✗ Format failed: ${e.message}")
            return CommandResult.Failure("Format failed", 1)
        }
    }

    private fun printFormatUsage() {
        println("""
            |Format/pretty-print UDM Language file
            |
            |USAGE:
            |  utlx udm format [OPTIONS]
            |
            |OPTIONS:
            |  --input <file>        Input .udm file path (default: stdin)
            |  --output <file>       Output file path (default: stdout)
            |  --compact, -c         Compact format (minimal whitespace)
            |  -v, --verbose         Enable verbose output
            |  -h, --help            Show this help message
            |
            |EXAMPLES:
            |  # Format to stdout:
            |  utlx udm format --input data.udm
            |
            |  # Format from stdin:
            |  cat data.udm | utlx udm format > formatted.udm
            |
            |  # Compact format:
            |  cat data.udm | utlx udm format --compact | wc -l
            |
            |  # Format to new file:
            |  utlx udm format --input data.udm --output formatted.udm
        """.trimMargin())
    }

    // ==================== FORMAT PARSING ====================

    /**
     * Parse format-specific options from args
     */
    private fun parseFormatOptions(args: Array<String>, format: String, isExport: Boolean): Map<String, Any> {
        val options = mutableMapOf<String, Any>()

        when (format.lowercase()) {
            "csv" -> {
                // Delimiter
                val delimiterStr = getOptionValue(args, "--csv-delimiter")
                val delimiter = when (delimiterStr?.lowercase()) {
                    "comma", "," -> ','
                    "semicolon", ";" -> ';'
                    "tab", "\\t" -> '\t'
                    "pipe", "|" -> '|'
                    null -> ','
                    else -> delimiterStr.firstOrNull() ?: ','
                }
                options["delimiter"] = delimiter

                // Headers
                val hasHeaders = when {
                    hasFlag(args, "--csv-no-headers") -> false
                    hasFlag(args, "--csv-headers") -> true
                    else -> true // default
                }
                options["hasHeaders"] = hasHeaders

                // Regional format (for serialization only)
                if (!isExport) {
                    val regionalStr = getOptionValue(args, "--csv-format")
                    options["regional"] = regionalStr?.lowercase() ?: "none"
                }
            }

            "xml" -> {
                if (isExport) {
                    // Array hints for parsing
                    val arrayHintsStr = getOptionValue(args, "--xml-array-hints")
                    if (arrayHintsStr != null) {
                        options["arrayHints"] = arrayHintsStr
                    }
                } else {
                    // Root name for serialization
                    val rootName = getOptionValue(args, "--xml-root")
                    if (rootName != null) {
                        options["rootName"] = rootName
                    }

                    // Encoding
                    val encoding = getOptionValue(args, "--xml-encoding")
                    if (encoding != null) {
                        options["encoding"] = encoding
                    }
                }
            }

            "xsd" -> {
                if (isExport) {
                    // Array hints for parsing
                    val arrayHintsStr = getOptionValue(args, "--xsd-array-hints")
                    if (arrayHintsStr != null) {
                        options["arrayHints"] = arrayHintsStr
                    }
                } else {
                    // Pattern
                    val pattern = getOptionValue(args, "--xsd-pattern")
                    if (pattern != null) {
                        options["pattern"] = pattern
                    }

                    // Version
                    val version = getOptionValue(args, "--xsd-version")
                    if (version != null) {
                        options["version"] = version
                    }

                    // Namespace (targetNamespace)
                    val namespace = getOptionValue(args, "--xsd-namespace")
                    if (namespace != null) {
                        options["namespace"] = namespace
                    }
                }
            }

            "jsonschema" -> {
                if (!isExport) {
                    // Draft version
                    val draft = getOptionValue(args, "--jsch-draft")
                    if (draft != null) {
                        options["draft"] = draft
                    }
                }
            }

            "avro" -> {
                if (!isExport) {
                    // Namespace
                    val namespace = getOptionValue(args, "--avro-namespace")
                    if (namespace != null) {
                        options["namespace"] = namespace
                    }

                    // Validate
                    options["validate"] = !hasFlag(args, "--avro-no-validate")
                }
            }

            "yaml" -> {
                if (isExport) {
                    // Multi-document
                    options["multiDoc"] = hasFlag(args, "--yaml-multi-doc")
                }
            }
        }

        return options
    }

    // ==================== FORMAT PARSERS ====================

    /**
     * Parse input content to UDM based on format
     */
    private fun parseInputToUDM(content: String, format: String, options: Map<String, Any>): UDM {
        return when (format.lowercase()) {
            "json" -> {
                JSONParser(content).parse()
            }

            "xml" -> {
                val arrayHints = (options["arrayHints"] as? String)
                    ?.split(",")?.map { it.trim() }?.toSet() ?: emptySet()
                XMLParser(content, arrayHints).parse()
            }

            "csv" -> {
                val delimiter = (options["delimiter"] as? Char) ?: ','
                val hasHeaders = (options["hasHeaders"] as? Boolean) ?: true
                val dialect = CSVDialect(delimiter = delimiter)
                CSVParser(content, dialect).parse(hasHeaders)
            }

            "yaml" -> {
                val multiDoc = (options["multiDoc"] as? Boolean) ?: false
                val parseOpts = YAMLParser.ParseOptions(multiDocument = multiDoc)
                YAMLParser().parse(content, parseOpts)
            }

            "jsonschema" -> {
                JSONSchemaParser(content).parse()
            }

            "xsd" -> {
                val arrayHints = (options["arrayHints"] as? String)
                    ?.split(",")?.map { it.trim() }?.toSet() ?: emptySet()
                XSDParser(content, arrayHints).parse()
            }

            "avro" -> {
                AvroSchemaParser().parse(content)
            }

            "protobuf" -> {
                ProtobufSchemaParser().parse(content)
            }

            else -> throw IllegalArgumentException(
                "Unsupported format: $format\n" +
                "Supported formats: ${SUPPORTED_FORMATS.joinToString(", ")}"
            )
        }
    }

    // ==================== FORMAT SERIALIZERS ====================

    /**
     * Serialize UDM to format
     */
    private fun serializeUDMToFormat(udm: UDM, format: String, options: Map<String, Any>): String {
        return when (format.lowercase()) {
            "json" -> {
                JSONSerializer(prettyPrint = true).serialize(udm)
            }

            "xml" -> {
                val rootName = (options["rootName"] as? String) ?: "root"
                val encoding = options["encoding"] as? String
                XMLSerializer(prettyPrint = true, outputEncoding = encoding)
                    .serialize(udm, rootName)
            }

            "csv" -> {
                val delimiter = (options["delimiter"] as? Char) ?: ','
                val includeHeaders = (options["hasHeaders"] as? Boolean) ?: true
                val regionalFormat = when (options["regional"] as? String) {
                    "usa" -> RegionalFormat.USA
                    "european" -> RegionalFormat.EUROPEAN
                    "french" -> RegionalFormat.FRENCH
                    "swiss" -> RegionalFormat.SWISS
                    else -> RegionalFormat.NONE
                }
                val dialect = CSVDialect(delimiter = delimiter)
                CSVSerializer(dialect, includeHeaders, regionalFormat = regionalFormat)
                    .serialize(udm)
            }

            "yaml" -> {
                val serOpts = YAMLSerializer.SerializeOptions(pretty = true)
                YAMLSerializer().serialize(udm, serOpts)
            }

            "jsonschema" -> {
                val draft = (options["draft"] as? String) ?: "2020-12"
                JSONSchemaSerializer(draft, prettyPrint = true).serialize(udm)
            }

            "xsd" -> {
                val version = (options["version"] as? String) ?: "1.0"
                val namespace = options["namespace"] as? String
                val pattern = when (options["pattern"] as? String) {
                    "russian-doll" -> XSDSerializer.XSDPattern.RUSSIAN_DOLL
                    "salami-slice" -> XSDSerializer.XSDPattern.SALAMI_SLICE
                    "venetian-blind" -> XSDSerializer.XSDPattern.VENETIAN_BLIND
                    "garden-of-eden" -> XSDSerializer.XSDPattern.GARDEN_OF_EDEN
                    else -> null
                }

                // Note: Need to check if XSDSerializer accepts targetNamespace parameter
                // If not, may need to add namespace via metadata before serialization
                val serializer = XSDSerializer(
                    pattern = pattern,
                    version = version,
                    prettyPrint = true
                )

                // TODO: Handle namespace - may need to add to UDM metadata if serializer doesn't support it directly
                if (namespace != null) {
                    // Try to add namespace to UDM structure if needed
                    // This may require checking XSDSerializer API
                }

                serializer.serialize(udm)
            }

            "avro" -> {
                val namespace = options["namespace"] as? String
                val validate = (options["validate"] as? Boolean) ?: true
                AvroSchemaSerializer(namespace, prettyPrint = true, validate).serialize(udm)
            }

            "protobuf" -> {
                ProtobufSchemaSerializer().serialize(udm)
            }

            else -> throw IllegalArgumentException(
                "Unsupported format: $format\n" +
                "Supported formats: ${SUPPORTED_FORMATS.joinToString(", ")}"
            )
        }
    }

    // ==================== MAIN USAGE ====================

    private fun printUsage() {
        println("""
            |Work with UDM Language files (Unix-style with stdin/stdout support)
            |
            |USAGE:
            |  utlx udm <subcommand> [options]
            |
            |SUBCOMMANDS:
            |  export     Export data from format to .udm
            |  import     Import .udm to format
            |  validate   Validate .udm file syntax
            |  format     Pretty-print/reformat .udm file
            |
            |OPTIONS:
            |  -h, --help Show this help message
            |
            |EXAMPLES:
            |  # Export JSON to .udm:
            |  utlx udm export --format json --input data.json --output data.udm
            |  cat data.json | utlx udm export --format json > data.udm
            |
            |  # Import .udm to JSON:
            |  utlx udm import --format json --input data.udm --output result.json
            |  cat data.udm | utlx udm import --format json > result.json
            |
            |  # Validate .udm file:
            |  utlx udm validate data.udm
            |  cat data.udm | utlx udm validate
            |
            |  # Format .udm file:
            |  utlx udm format --input data.udm --output formatted.udm
            |  cat data.udm | utlx udm format > formatted.udm
            |
            |  # Round-trip test:
            |  cat data.json | utlx udm export --format json | utlx udm import --format json | jq .
            |
            |For subcommand help:
            |  utlx udm <subcommand> --help
            |
            |Supported formats:
            |  json, xml, csv, yaml, jsonschema, xsd, avro, protobuf
        """.trimMargin())
    }
}
