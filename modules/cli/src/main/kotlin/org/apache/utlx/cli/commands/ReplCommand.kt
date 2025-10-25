// modules/cli/src/main/kotlin/org/apache/utlx/cli/commands/ReplCommand.kt
package org.apache.utlx.cli.commands

import org.apache.utlx.core.interpreter.*
import org.apache.utlx.core.lexer.Lexer
import org.apache.utlx.core.parser.Parser
import org.apache.utlx.core.parser.ParseResult
import org.apache.utlx.core.udm.UDM
import java.time.Instant
import org.apache.utlx.formats.json.JSONParser
import org.apache.utlx.formats.xml.XMLParser
import org.apache.utlx.formats.csv.CSVParser
import org.apache.utlx.formats.yaml.YAMLParser
import org.apache.utlx.formats.xsd.XSDParser
import org.apache.utlx.formats.jsch.JSONSchemaParser
import java.io.File
import org.jline.reader.LineReaderBuilder
import org.jline.reader.LineReader
import org.jline.reader.impl.history.DefaultHistory
import org.jline.terminal.TerminalBuilder
import java.nio.file.Paths

/**
 * REPL command - interactive Read-Eval-Print Loop for UTL-X
 *
 * Usage:
 *   utlx repl
 *
 * Features:
 *   - Evaluate expressions interactively
 *   - Test lambda predicates
 *   - Load data files
 *   - Explore functions
 *   - Persistent session (variables remain defined)
 */
object ReplCommand {
    private const val VERSION = "1.0.0"
    private var env = Environment()
    private var stdlib = StandardLibraryImpl()

    fun execute(args: Array<String>) {
        // Check for help flag
        if (args.contains("--help") || args.contains("-h")) {
            printHelp()
            return
        }

        printWelcome()

        // Initialize standard library in environment
        stdlib.registerAll(env)

        // Set up JLine terminal and line reader with history
        val terminal = TerminalBuilder.builder()
            .system(true)
            .build()

        val historyFile = Paths.get(System.getProperty("user.home"), ".utlx", "repl_history")
        historyFile.parent?.toFile()?.mkdirs()  // Create .utlx directory if needed

        val lineReader = LineReaderBuilder.builder()
            .terminal(terminal)
            .history(DefaultHistory())
            .variable(LineReader.HISTORY_FILE, historyFile)
            .variable(LineReader.HISTORY_SIZE, 500)
            .build()

        // Load history from file
        lineReader.history.load()

        // Main REPL loop
        try {
            while (true) {
                val input = try {
                    lineReader.readLine("utlx> ")
                } catch (e: org.jline.reader.UserInterruptException) {
                    // Ctrl+C pressed
                    println()
                    continue
                } catch (e: org.jline.reader.EndOfFileException) {
                    // Ctrl+D pressed
                    break
                }

                if (input.isBlank()) continue

                // Handle special commands
                if (input.startsWith(":")) {
                    if (!handleCommand(input.trim(), lineReader)) break
                    continue
                }

                // Evaluate expression
                try {
                    val result = evaluateExpression(input)
                    println(formatResult(result))
                } catch (e: Exception) {
                    System.err.println("Error: ${e.message}")
                    if (System.getProperty("utlx.debug") == "true") {
                        e.printStackTrace()
                    }
                }
            }
        } finally {
            // Save history on exit
            lineReader.history.save()
        }

        println("\nGoodbye!")
    }

    private fun evaluateExpression(input: String): RuntimeValue {
        // For REPL, we need to parse just an expression, not a full program
        // We'll wrap it in a minimal program structure
        val wrappedInput = "%utlx 1.0\ninput json\noutput json\n---\n$input"
        val tokens = Lexer(wrappedInput).tokenize()
        val parseResult = Parser(tokens).parse()

        if (parseResult is ParseResult.Failure) {
            throw RuntimeError("Parse error: ${parseResult.errors.joinToString("; ")}")
        }

        val program = (parseResult as ParseResult.Success).program
        val interpreter = Interpreter()
        return interpreter.evaluate(program.body, env)
    }

    private fun formatResult(value: RuntimeValue): String {
        // Pretty-print results
        return when (value) {
            is RuntimeValue.StringValue -> "\"${value.value}\""
            is RuntimeValue.NumberValue -> {
                if (value.value % 1.0 == 0.0)
                    value.value.toInt().toString()
                else
                    value.value.toString()
            }
            is RuntimeValue.BooleanValue -> value.value.toString()
            is RuntimeValue.NullValue -> "null"
            is RuntimeValue.ArrayValue -> "[" + value.elements.joinToString(", ") { formatResult(it) } + "]"
            is RuntimeValue.ObjectValue -> "{" + value.properties.entries.joinToString(", ") {
                "${it.key}: ${formatResult(it.value)}"
            } + "}"
            is RuntimeValue.FunctionValue -> "<function(${value.parameters.joinToString(", ")})>"
            is RuntimeValue.UDMValue -> formatUDM(value.udm)
        }
    }

    private fun formatUDM(udm: UDM): String {
        return when (udm) {
            is UDM.Scalar -> {
                val value = udm.value
                when (value) {
                    is String -> "\"$value\""
                    null -> "null"
                    else -> value.toString()
                }
            }
            is UDM.Array -> "[" + udm.elements.joinToString(", ") { formatUDM(it) } + "]"
            is UDM.Object -> "{" + udm.properties.entries.joinToString(", ") {
                "\"${it.key}\": ${formatUDM(it.value)}"
            } + "}"
            is UDM.DateTime -> "\"${udm.toISOString()}\""
            is UDM.Date -> "\"${udm.toISOString()}\""
            is UDM.LocalDateTime -> "\"${udm.toISOString()}\""
            is UDM.Time -> "\"${udm.toISOString()}\""
            is UDM.Binary -> "\"<binary:${udm.data.size} bytes>\""
            is UDM.Lambda -> "<lambda>"
        }
    }

    private fun handleCommand(command: String, lineReader: LineReader): Boolean {
        val parts = command.substring(1).split(Regex("\\s+"), 2)
        val cmd = parts[0].lowercase()
        val arg = parts.getOrNull(1)

        return when (cmd) {
            "quit", "exit", "q" -> false
            "help", "h", "?" -> { printHelp(); true }
            "clear", "c" -> { clearSession(); true }
            "load", "l" -> { if (arg != null) loadFile(arg) else println("Usage: :load <file>"); true }
            "type", "t" -> { if (arg != null) showType(arg) else println("Usage: :type <expression>"); true }
            "functions", "fn", "f" -> { listFunctions(arg); true }
            "info", "i" -> { if (arg != null) showFunctionInfo(arg) else println("Usage: :info <function>"); true }
            "history" -> { showHistory(lineReader, arg); true }
            else -> { println("Unknown command: :$cmd (type :help for commands)"); true }
        }
    }

    private fun clearSession() {
        env = Environment()
        stdlib = StandardLibraryImpl()
        stdlib.registerAll(env)
        println("Session cleared - all variables reset")
    }

    private fun loadFile(path: String) {
        try {
            val file = File(path)
            if (!file.exists()) {
                println("File not found: $path")
                return
            }

            val content = file.readText()
            val udm = when (file.extension.lowercase()) {
                "json" -> JSONParser(content.reader()).parse()
                "xml" -> XMLParser(content.reader()).parse()
                "csv" -> CSVParser(content.reader()).parse(hasHeaders = true)
                "yaml", "yml" -> YAMLParser().parse(content)
                "xsd" -> XSDParser(content.reader()).parse()
                "jsch" -> JSONSchemaParser(content.reader()).parse()
                else -> {
                    println("Unsupported format: ${file.extension}")
                    println("Supported: json, xml, csv, yaml, xsd, jsch")
                    return
                }
            }

            env.define("input", RuntimeValue.UDMValue(udm))
            println("Loaded ${file.name} as ${'$'}input")
        } catch (e: Exception) {
            println("Error loading file: ${e.message}")
        }
    }

    private fun showType(expr: String) {
        try {
            val result = evaluateExpression(expr)
            val typeName = when (result) {
                is RuntimeValue.StringValue -> "String"
                is RuntimeValue.NumberValue -> "Number"
                is RuntimeValue.BooleanValue -> "Boolean"
                is RuntimeValue.NullValue -> "Null"
                is RuntimeValue.ArrayValue -> "Array"
                is RuntimeValue.ObjectValue -> "Object"
                is RuntimeValue.FunctionValue -> "Function"
                is RuntimeValue.UDMValue -> when (result.udm) {
                    is UDM.Scalar -> "UDM.Scalar"
                    is UDM.Array -> "UDM.Array"
                    is UDM.Object -> "UDM.Object"
                    is UDM.DateTime -> "UDM.DateTime"
                    is UDM.Date -> "UDM.Date"
                    is UDM.LocalDateTime -> "UDM.LocalDateTime"
                    is UDM.Time -> "UDM.Time"
                    is UDM.Binary -> "UDM.Binary"
                    is UDM.Lambda -> "UDM.Lambda"
                }
            }
            println(typeName)
        } catch (e: Exception) {
            println("Error: ${e.message}")
        }
    }

    private fun listFunctions(pattern: String?) {
        // Call FunctionsCommand
        if (pattern != null) {
            FunctionsCommand.execute(arrayOf("search", pattern))
        } else {
            FunctionsCommand.execute(arrayOf("list"))
        }
    }

    private fun showFunctionInfo(name: String) {
        FunctionsCommand.execute(arrayOf("info", name))
    }

    private fun showHistory(lineReader: LineReader, arg: String?) {
        when (arg?.lowercase()) {
            "clear" -> {
                lineReader.history.purge()
                println("History cleared")
            }
            else -> {
                val history = lineReader.history
                if (history.size() == 0) {
                    println("No history yet")
                } else {
                    val entries = history.iterator().asSequence().toList()
                    entries.forEachIndexed { index, entry ->
                        println("${index + 1}: ${entry.line()}")
                    }
                }
            }
        }
    }

    private fun printWelcome() {
        println("""
            |UTL-X REPL v$VERSION
            |Type expressions to evaluate, or :help for commands
            |Press Ctrl+D or :quit to exit
            |
        """.trimMargin())
    }

    private fun printHelp() {
        println("""
            |UTL-X REPL Commands:
            |
            |  :help, :h, :?          Show this help message
            |  :quit, :exit, :q       Exit REPL
            |  :clear, :c             Clear session (reset all variables)
            |  :load <file>, :l       Load JSON/XML/CSV/YAML file as ${'$'}input
            |  :type <expr>, :t       Show type of expression
            |  :functions [pattern]   List all functions or search by pattern
            |  :info <function>, :i   Show detailed function documentation
            |  :history               Show command history
            |  :history clear         Clear command history
            |
            |Navigation:
            |  Up/Down arrows         Navigate through command history
            |  Ctrl+D                 Exit REPL
            |  Ctrl+C                 Cancel current input
            |
            |Examples:
            |  Basic operations:
            |    utlx> 2 + 2
            |    utlx> "hello" + " " + "world"
            |    utlx> [1, 2, 3]
            |
            |  Variables:
            |    utlx> let x = 10
            |    utlx> let data = [1, 3, 5, 2, 4]
            |    utlx> data
            |
            |  Lambda predicates:
            |    utlx> map([1,2,3], x => x * 2)
            |    utlx> filter([1,2,3,4,5], x => x % 2 == 0)
            |    utlx> find(data, x => x % 2 == 0)
            |
            |  Pipelines:
            |    utlx> [1,2,3,4,5] |> filter(x => x > 2) |> map(x => x * 2)
            |
            |  Loading data:
            |    utlx> :load data.json
            |    utlx> ${'$'}input
            |    utlx> ${'$'}input.orders
            |    utlx> map(${'$'}input.orders, o => o.total)
            |
            |  Function exploration:
            |    utlx> :functions map
            |    utlx> :info find
            |    utlx> :type [1,2,3]
            |
        """.trimMargin())
    }
}
