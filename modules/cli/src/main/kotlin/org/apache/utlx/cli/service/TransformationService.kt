// modules/cli/src/main/kotlin/org/apache/utlx/cli/service/TransformationService.kt
package org.apache.utlx.cli.service

import org.apache.utlx.cli.commands.TransformCommand
import org.apache.utlx.core.ast.Program
import org.apache.utlx.core.interpreter.Interpreter
import org.apache.utlx.core.lexer.Lexer
import org.apache.utlx.core.parser.Parser
import org.apache.utlx.core.parser.ParseResult
import org.apache.utlx.core.types.StandardLibrary
import org.apache.utlx.core.types.TypeCheckResult
import org.apache.utlx.core.types.TypeChecker
import org.apache.utlx.core.udm.UDM
import org.apache.utlx.formats.avro.AvroSchemaParser
import org.apache.utlx.formats.avro.AvroSchemaSerializer
import org.apache.utlx.formats.csv.CSVDialect
import org.apache.utlx.formats.csv.CSVParser
import org.apache.utlx.formats.csv.CSVSerializer
import org.apache.utlx.formats.csv.RegionalFormat
import org.apache.utlx.formats.jsch.JSONSchemaParser
import org.apache.utlx.formats.jsch.JSONSchemaSerializer
import org.apache.utlx.formats.json.JSONParser
import org.apache.utlx.formats.json.JSONSerializer
import org.apache.utlx.formats.protobuf.ProtobufSchemaParser
import org.apache.utlx.formats.protobuf.ProtobufSchemaSerializer
import org.apache.utlx.formats.xml.XMLParser
import org.apache.utlx.formats.xml.XMLSerializer
import org.apache.utlx.formats.xsd.XSDParser
import org.apache.utlx.formats.xsd.XSDSerializer
import org.apache.utlx.formats.yaml.YAMLParser
import org.apache.utlx.formats.yaml.YAMLSerializer

/**
 * Core transformation service - single source of truth for all UTLX transformations
 *
 * Architecture:
 *   TransformationService (core logic)
 *         ↑
 *         │
 *    CLI (thin wrapper for I/O)
 */
class TransformationService {

    /**
     * Input data (just content and format - no file I/O here)
     */
    data class InputData(
        val content: String,
        val format: String?  // null = auto-detect from header
    )

    /**
     * Transformation options
     */
    data class TransformOptions(
        val verbose: Boolean = false,
        val pretty: Boolean = true,
        val strictTypes: Boolean = false,
        val overrideOutputFormat: String? = null
    )

    /**
     * Transform UTLX script with inputs
     * This is the ONLY transformation method - all transformation logic lives here
     */
    fun transform(
        utlxSource: String,
        inputs: Map<String, InputData>,
        options: TransformOptions = TransformOptions()
    ): Pair<String, String> {  // Returns (output, outputFormat)

        // Step 1: Compile the UTLX script (using CLI's proven compileScript logic)
        val program = compileScript(utlxSource, options)

        // Step 2: Parse inputs using format from header if not specified
        val inputUDMs = inputs.mapValues { (name, inputData) ->
            // Priority: explicit format > header format > auto-detect
            val format = inputData.format
                ?: program.header.inputs.find { it.first == name }?.second?.type?.name?.lowercase()
                ?: "json"  // fallback

            // Get format options from header
            val inputSpec = program.header.inputs.find { it.first == name }?.second
            val formatOptions = inputSpec?.options ?: emptyMap()

            if (options.verbose) {
                println("[TransformationService] Parsing input '$name' as $format")
            }

            parseInput(inputData.content, format, formatOptions)
        }

        // Step 3: Execute transformation
        val interpreter = Interpreter()
        val result = interpreter.execute(program, inputUDMs)
        val outputUDM = result.toUDM()

        // Step 4: Determine output format (Priority: override > header)
        val outputFormat = options.overrideOutputFormat
            ?: program.header.outputFormat.type.name.lowercase()

        if (options.verbose) {
            println("[TransformationService] Output format: $outputFormat")
        }

        // Step 5: Serialize output using format + format spec from header
        val outputData = serializeOutput(outputUDM, outputFormat, program.header.outputFormat, options.pretty)

        return Pair(outputData, outputFormat)
    }

    /**
     * Compile script - extracted from CLI's compileScript
     */
    private fun compileScript(source: String, options: TransformOptions): Program {
        if (options.verbose) println("[TransformationService] Lexing...")
        val lexer = Lexer(source)
        val tokens = lexer.tokenize()

        if (options.verbose) println("[TransformationService] Parsing...")
        val parser = Parser(tokens)
        val parseResult = parser.parse()

        val program = when (parseResult) {
            is ParseResult.Success -> {
                if (options.verbose) println("[TransformationService] ✓ Parsing successful")
                parseResult.program
            }
            is ParseResult.Failure -> {
                System.err.println("Parse errors:")
                parseResult.errors.forEach { error ->
                    System.err.println("  ${error.message} at ${error.location}")
                }
                throw IllegalStateException("Parsing failed")
            }
        }

        // Type checking
        if (options.verbose) println("[TransformationService] Type checking...")
        val stdlib = StandardLibrary()
        val typeChecker = TypeChecker(stdlib)
        val typeResult = typeChecker.check(program)

        when (typeResult) {
            is TypeCheckResult.Success -> {
                if (options.verbose) {
                    println("[TransformationService] ✓ Type checking successful (type: ${typeResult.type})")
                }
            }
            is TypeCheckResult.Failure -> {
                if (options.strictTypes) {
                    System.err.println("Type checking failed:")
                    typeResult.errors.forEach { error ->
                        System.err.println("  ${error.message} at ${error.location}")
                        if (error.expected != null && error.actual != null) {
                            System.err.println("    Expected: ${error.expected}")
                            System.err.println("    Actual: ${error.actual}")
                        }
                    }
                    throw IllegalStateException("Type checking failed")
                } else if (options.verbose) {
                    println("[TransformationService] Type warnings:")
                    typeResult.errors.forEach { error ->
                        println("  ${error.message} at ${error.location}")
                    }
                }
            }
        }

        return program
    }

    /**
     * Parse input - extracted from CLI's parseInput (EXACT copy to maintain 100% compatibility)
     */
    private fun parseInput(data: String, format: String, options: Map<String, Any> = emptyMap()): UDM {
        return try {
            when (format.lowercase()) {
                "xml" -> {
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
                    val delimiter = (options["delimiter"] as? String)?.firstOrNull() ?: ','
                    val headers = (options["headers"] as? Boolean) ?: true

                    val dialect = CSVDialect(delimiter = delimiter)
                    CSVParser(data, dialect).parse(hasHeaders = headers)
                }
                "yaml", "yml" -> {
                    YAMLParser().parse(data)
                }
                "xsd" -> {
                    val arrayHints = (options["arrays"] as? List<*>)
                        ?.mapNotNull { it as? String }
                        ?.toSet()
                        ?: emptySet()
                    XSDParser(data, arrayHints).parse()
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

    /**
     * Serialize output - extracted from CLI's serializeOutput (EXACT copy to maintain 100% compatibility)
     */
    private fun serializeOutput(udm: UDM, format: String, formatSpec: org.apache.utlx.core.ast.FormatSpec, pretty: Boolean): String {
        return try {
            when (format.lowercase()) {
                "xml" -> {
                    val encoding = formatSpec.options["encoding"] as? String
                    XMLSerializer(prettyPrint = pretty, outputEncoding = encoding).serialize(udm)
                }
                "json" -> JSONSerializer(pretty).serialize(udm)
                "csv" -> {
                    val delimiter = (formatSpec.options["delimiter"] as? String)?.firstOrNull() ?: ','
                    val headers = (formatSpec.options["headers"] as? Boolean) ?: true
                    val bom = (formatSpec.options["bom"] as? Boolean) ?: false

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
                "jsch", "json-schema", "jsonschema" -> {
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
                "avro", "avsc" -> {
                    val namespace = formatSpec.options["namespace"] as? String
                    val validate = formatSpec.options["validate"] as? Boolean ?: true

                    AvroSchemaSerializer(
                        namespace = namespace,
                        prettyPrint = pretty,
                        validate = validate
                    ).serialize(udm)
                }
                "proto", "protobuf" -> {
                    ProtobufSchemaSerializer().serialize(udm)
                }
                else -> throw IllegalArgumentException("Unsupported output format: $format")
            }
        } catch (e: Exception) {
            System.err.println("Error serializing output: ${e.message}")
            throw e
        }
    }
}
