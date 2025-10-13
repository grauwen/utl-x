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
        val inputFile: File? = null,
        val scriptFile: File,
        val outputFile: File? = null,
        val inputFormat: String? = null,
        val outputFormat: String? = null,
        val verbose: Boolean = false,
        val pretty: Boolean = true
    )
    
    fun execute(args: Array<String>) {
        val options = parseOptions(args)
        
        if (options.verbose) {
            println("UTL-X Transform")
            println("Script: ${options.scriptFile.absolutePath}")
            options.inputFile?.let { println("Input: ${it.absolutePath}") }
            options.outputFile?.let { println("Output: ${it.absolutePath}") }
        }
        
        // Read and compile the UTL-X script
        val scriptContent = options.scriptFile.readText()
        val program = compileScript(scriptContent, options.verbose)
        
        // Read input data
        val inputData = if (options.inputFile != null) {
            options.inputFile.readText()
        } else {
            readStdin()
        }
        
        // Detect or use specified input format
        val inputFormat = options.inputFormat 
            ?: detectFormat(inputData, options.inputFile?.extension)
        
        if (options.verbose) {
            println("Input format: $inputFormat")
        }
        
        // Parse input to UDM
        val inputUDM = parseInput(inputData, inputFormat)
        
        // Execute transformation
        val interpreter = Interpreter()
        val outputUDM = interpreter.execute(program, inputUDM)
        
        // Detect or use specified output format
        val outputFormat = options.outputFormat ?: inputFormat
        
        if (options.verbose) {
            println("Output format: $outputFormat")
        }
        
        // Serialize output
        val outputData = serializeOutput(outputUDM, outputFormat, options.pretty)
        
        // Write output
        if (options.outputFile != null) {
            options.outputFile.writeText(outputData)
            if (options.verbose) {
                println("✓ Transformation complete: ${options.outputFile.absolutePath}")
            }
        } else {
            println(outputData)
        }
    }
    
    private fun compileScript(script: String, verbose: Boolean): org.apache.utlx.core.ast.Program {
        try {
            if (verbose) println("Lexing...")
            val lexer = Lexer(script)
            val tokens = lexer.tokenize()
            
            if (verbose) println("Parsing...")
            val parser = Parser(tokens)
            val ast = parser.parse()
            
            if (verbose) println("Type checking...")
            val typeChecker = TypeChecker()
            typeChecker.check(ast)
            
            return ast
        } catch (e: Exception) {
            System.err.println("Error compiling script: ${e.message}")
            if (verbose) {
                e.printStackTrace()
            }
            exitProcess(1)
        }
    }
    
    private fun parseInput(data: String, format: String): UDM {
        return try {
            when (format.lowercase()) {
                "xml" -> XMLParser().parse(data)
                "json" -> JSONParser().parse(data)
                "csv" -> CSVParser().parse(data)
                else -> throw IllegalArgumentException("Unsupported input format: $format")
            }
        } catch (e: Exception) {
            System.err.println("Error parsing input: ${e.message}")
            throw e
        }
    }
    
    private fun serializeOutput(udm: UDM, format: String, pretty: Boolean): String {
        return try {
            when (format.lowercase()) {
                "xml" -> XMLSerializer(pretty).serialize(udm)
                "json" -> JSONSerializer(pretty).serialize(udm)
                "csv" -> CSVSerializer().serialize(udm)
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
            if (it in listOf("xml", "json", "csv", "yaml")) {
                return it
            }
        }
        
        // Auto-detect from content
        val trimmed = data.trim()
        return when {
            trimmed.startsWith("<") -> "xml"
            trimmed.startsWith("{") || trimmed.startsWith("[") -> "json"
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
        
        var inputFile: File? = null
        var scriptFile: File? = null
        var outputFile: File? = null
        var inputFormat: String? = null
        var outputFormat: String? = null
        var verbose = false
        var pretty = true
        
        var i = 0
        while (i < args.size) {
            when (args[i]) {
                "-o", "--output" -> {
                    outputFile = File(args[++i])
                }
                "-i", "--input" -> {
                    inputFile = File(args[++i])
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
                "-h", "--help" -> {
                    printUsage()
                    exitProcess(0)
                }
                else -> {
                    if (!args[i].startsWith("-")) {
                        if (scriptFile == null) {
                            scriptFile = File(args[i])
                        } else if (inputFile == null) {
                            inputFile = File(args[i])
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
        
        inputFile?.let {
            if (!it.exists()) {
                System.err.println("Error: Input file not found: ${it.absolutePath}")
                exitProcess(1)
            }
        }
        
        return TransformOptions(
            inputFile = inputFile,
            scriptFile = scriptFile,
            outputFile = outputFile,
            inputFormat = inputFormat,
            outputFormat = outputFormat,
            verbose = verbose,
            pretty = pretty
        )
    }
    
    private fun printUsage() {
        println("""
            |Transform data using UTL-X scripts
            |
            |Usage:
            |  utlx transform [input-file] <script-file> [options]
            |  utlx transform <script-file> [options] < input-file
            |
            |Arguments:
            |  input-file      Input data file (if not provided, reads from stdin)
            |  script-file     UTL-X transformation script (.utlx)
            |
            |Options:
            |  -o, --output FILE           Write output to FILE (default: stdout)
            |  -i, --input FILE            Read input from FILE
            |  --input-format FORMAT       Force input format (xml, json, csv)
            |  --output-format FORMAT      Force output format (xml, json, csv)
            |  -v, --verbose               Enable verbose output
            |  --no-pretty                 Disable pretty-printing
            |  -h, --help                  Show this help message
            |
            |Examples:
            |  # Transform XML to JSON
            |  utlx transform input.xml transform.utlx -o output.json
            |
            |  # Read from stdin, write to stdout
            |  cat input.xml | utlx transform script.utlx > output.json
            |
            |  # Force output format
            |  utlx transform input.json script.utlx --output-format xml -o output.xml
            |
            |  # Verbose mode
            |  utlx transform input.xml script.utlx -v -o output.json
        """.trimMargin())
    }
}
