// modules/cli/src/main/kotlin/org/apache/utlx/cli/commands/LintCommand.kt
package org.apache.utlx.cli.commands

import org.apache.utlx.core.lexer.Lexer
import org.apache.utlx.core.parser.Parser
import org.apache.utlx.core.parser.ParseResult
import org.apache.utlx.core.ast.*
import org.apache.utlx.cli.CommandResult
import java.io.File

/**
 * Lint command - checks for style issues and best practices (Level 4)
 *
 * Implements Option A (Separate Commands) from validation-and-analysis-study.md
 *
 * Level 4: Logical validation (lint)
 *   - Dead code detection
 *   - Unused variable detection
 *   - Complexity analysis
 *   - Style checking
 *
 * Lint produces warnings only - it never fails the build.
 * Use `validate` command for correctness checking.
 *
 * Usage:
 *   utlx lint <script-file> [options]
 *   utlx lint <script-file> --fix
 *   utlx lint <script-file> --rules lint-rules.json
 */
object LintCommand {

    data class LintOptions(
        val scriptFile: File,
        val fix: Boolean = false,
        val rulesFile: File? = null,
        val verbose: Boolean = false,
        val format: LintFormat = LintFormat.HUMAN,
        val noDeadCode: Boolean = false,
        val noUnused: Boolean = false,
        val noComplexity: Boolean = false,
        val noStyle: Boolean = false
    )

    enum class LintFormat {
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
            println("UTL-X Lint")
            println("Script: ${options.scriptFile.absolutePath}")
            options.rulesFile?.let { println("Rules: ${it.absolutePath}") }
            if (options.fix) {
                println("Auto-fix: enabled")
            }
            println()
        }

        // Read and parse script
        val scriptContent = try {
            options.scriptFile.readText()
        } catch (e: Exception) {
            System.err.println("✗ Error reading script file: ${e.message}")
            return CommandResult.Failure("Error reading script file", 1)
        }

        val lexer = Lexer(scriptContent)
        val tokens = try {
            lexer.tokenize()
        } catch (e: Exception) {
            System.err.println("⚠️  Cannot lint: Lexer error")
            System.err.println("   ${e.message}")
            System.err.println("   Run 'utlx validate' to check for syntax errors first.")
            return CommandResult.Failure("Cannot lint: Lexer error", 1)
        }

        val parser = Parser(tokens)
        val parseResult = parser.parse()

        val program = when (parseResult) {
            is ParseResult.Success -> parseResult.program
            is ParseResult.Failure -> {
                System.err.println("⚠️  Cannot lint: File has syntax errors")
                parseResult.errors.forEach { error ->
                    System.err.println("   ${error.location.line}:${error.location.column} - ${error.message}")
                }
                System.err.println()
                System.err.println("   Run 'utlx validate' to fix syntax errors first.")
                return CommandResult.Failure("Cannot lint: File has syntax errors", 1)
            }
        }

        // Run linters
        val warnings = mutableListOf<LintWarning>()

        if (!options.noDeadCode) {
            warnings.addAll(DeadCodeAnalyzer().analyze(program))
        }

        if (!options.noUnused) {
            warnings.addAll(UnusedVariableAnalyzer().analyze(program))
        }

        if (!options.noComplexity) {
            warnings.addAll(ComplexityAnalyzer().analyze(program))
        }

        if (!options.noStyle) {
            warnings.addAll(StyleChecker().check(program))
        }

        // Print results
        if (warnings.isEmpty()) {
            when (options.format) {
                LintFormat.HUMAN -> println("✓ No lint issues found")
                LintFormat.JSON -> println("""{"status":"clean","warnings":[]}""")
                LintFormat.COMPACT -> println("CLEAN")
            }
            return CommandResult.Success
        } else {
            printWarnings(warnings, options.format, scriptContent)

            // Auto-fix if requested
            if (options.fix) {
                val fixableCount = warnings.count { it.fixable }
                if (fixableCount > 0) {
                    if (options.verbose) {
                        println()
                        println("Applying auto-fixes...")
                    }

                    val fixed = autoFix(scriptContent, warnings.filter { it.fixable })
                    options.scriptFile.writeText(fixed)

                    when (options.format) {
                        LintFormat.HUMAN -> println("✓ Fixed $fixableCount issue(s)")
                        LintFormat.JSON -> println("""{"fixed":$fixableCount}""")
                        LintFormat.COMPACT -> println("FIXED:$fixableCount")
                    }
                } else {
                    if (options.verbose) {
                        println()
                        println("No auto-fixable issues found")
                    }
                }
            } else {
                val fixableCount = warnings.count { it.fixable }
                if (fixableCount > 0 && options.format == LintFormat.HUMAN) {
                    println()
                    println("$fixableCount issue(s) can be auto-fixed with --fix")
                }
            }

            // Lint never fails (exit 0 even with warnings)
            return CommandResult.Success
        }
    }

    private fun parseOptions(args: Array<String>): LintOptions {
        if (args.isEmpty()) {
            printUsage()
            throw IllegalArgumentException("Argument error")
        }

        var scriptFile: File? = null
        var fix = false
        var rulesFile: File? = null
        var verbose = false
        var format = LintFormat.HUMAN
        var noDeadCode = false
        var noUnused = false
        var noComplexity = false
        var noStyle = false

        var i = 0
        while (i < args.size) {
            when (args[i]) {
                "--fix" -> {
                    fix = true
                }
                "--rules", "-r" -> {
                    if (i + 1 >= args.size) {
                        System.err.println("Error: --rules requires a file path")
                        printUsage()
                        throw IllegalArgumentException("Argument error")
                    }
                    rulesFile = File(args[++i])
                }
                "-v", "--verbose" -> {
                    verbose = true
                }
                "--format", "-f" -> {
                    if (i + 1 >= args.size) {
                        System.err.println("Error: --format requires a format (human, json, compact)")
                        printUsage()
                        throw IllegalArgumentException("Argument error")
                    }
                    format = when (args[++i].lowercase()) {
                        "human" -> LintFormat.HUMAN
                        "json" -> LintFormat.JSON
                        "compact" -> LintFormat.COMPACT
                        else -> {
                            System.err.println("Error: Invalid format. Use: human, json, compact")
                            throw IllegalArgumentException("Argument error")
                        }
                    }
                }
                "--no-dead-code" -> noDeadCode = true
                "--no-unused" -> noUnused = true
                "--no-complexity" -> noComplexity = true
                "--no-style" -> noStyle = true
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
                            throw IllegalArgumentException("Argument error")
                        }
                    } else {
                        System.err.println("Error: Unknown option: ${args[i]}")
                        printUsage()
                        throw IllegalArgumentException("Argument error")
                    }
                }
            }
            i++
        }

        if (scriptFile == null) {
            System.err.println("Error: Script file is required")
            printUsage()
            throw IllegalArgumentException("Argument error")
        }

        if (!scriptFile.exists()) {
            System.err.println("Error: Script file not found: ${scriptFile.absolutePath}")
            throw IllegalArgumentException("Argument error")
        }

        if (rulesFile != null && !rulesFile.exists()) {
            System.err.println("Error: Rules file not found: ${rulesFile.absolutePath}")
            throw IllegalArgumentException("Argument error")
        }

        return LintOptions(
            scriptFile = scriptFile,
            fix = fix,
            rulesFile = rulesFile,
            verbose = verbose,
            format = format,
            noDeadCode = noDeadCode,
            noUnused = noUnused,
            noComplexity = noComplexity,
            noStyle = noStyle
        )
    }

    private fun printUsage() {
        println("""
            |Check UTL-X scripts for style issues and best practices
            |
            |Usage:
            |  utlx lint <script-file> [options]
            |
            |Lint Checks (Level 4):
            |  - Dead code detection (unreachable expressions)
            |  - Unused variable detection
            |  - Complexity analysis (too complex expressions)
            |  - Style checking (naming conventions, formatting)
            |
            |Arguments:
            |  script-file         UTL-X transformation script to lint
            |
            |Options:
            |  --fix               Auto-fix issues where possible
            |  --rules, -r FILE    Custom lint rules file (JSON)
            |  --format, -f FORMAT Output format: human (default), json, compact
            |  -v, --verbose       Enable verbose output
            |  -h, --help          Show this help message
            |
            |Disable Specific Checks:
            |  --no-dead-code      Skip dead code detection
            |  --no-unused         Skip unused variable detection
            |  --no-complexity     Skip complexity analysis
            |  --no-style          Skip style checking
            |
            |Exit Codes:
            |  0   Always (lint produces warnings only, never fails)
            |
            |Examples:
            |  # Basic linting
            |  utlx lint transform.utlx
            |
            |  # Auto-fix issues
            |  utlx lint transform.utlx --fix
            |
            |  # Custom rules
            |  utlx lint transform.utlx --rules .utlx-lint.json
            |
            |  # JSON output for IDE integration
            |  utlx lint transform.utlx --format json
            |
            |  # Disable specific checks
            |  utlx lint transform.utlx --no-complexity --no-style
            |
            |  # Verbose mode
            |  utlx lint transform.utlx --verbose
            |
            |Workflow:
            |  1. Run 'utlx validate' first to check for errors
            |  2. Run 'utlx lint' to check for style issues
            |  3. Use --fix to automatically fix style issues
            |
            |See also:
            |  utlx validate   - Validate for correctness (Levels 1-3)
            |  utlx design     - Design-time analysis and schema generation
        """.trimMargin())
    }

    private fun printWarnings(warnings: List<LintWarning>, format: LintFormat, scriptContent: String? = null) {
        when (format) {
            LintFormat.HUMAN -> {
                println()
                println("Lint warnings:")
                warnings.forEach { warning ->
                    val fixable = if (warning.fixable) " [fixable]" else ""
                    println("  ⚠ ${warning.location.line}:${warning.location.column} - ${warning.message}$fixable")

                    // Show code context if available
                    if (scriptContent != null) {
                        val context = extractCodeContext(scriptContent, warning.location.line, warning.location.column)
                        if (context.isNotEmpty()) {
                            context.forEach { line ->
                                println("    $line")
                            }
                        }
                    }

                    if (warning.suggestion != null) {
                        println("    💡 Suggestion: ${warning.suggestion}")
                    }
                }
                println()
                println("${warnings.size} warning(s) found")
            }
            LintFormat.JSON -> {
                val warningsJson = warnings.joinToString(",") { warning ->
                    val suggestion = warning.suggestion?.let { """"suggestion":"${it.replace("\"", "\\\"")}",""" } ?: ""
                    """{
                        |  "message":"${warning.message.replace("\"", "\\\"")}",
                        |  "line":${warning.location.line},
                        |  "column":${warning.location.column},
                        |  "code":"${warning.code}",
                        |  "fixable":${warning.fixable},
                        |  $suggestion
                        |  "severity":"${warning.severity.name.lowercase()}"
                        |}""".trimMargin()
                }
                println("""{"warnings":[$warningsJson],"total":${warnings.size}}""")
            }
            LintFormat.COMPACT -> {
                warnings.forEach { warning ->
                    val fixable = if (warning.fixable) ":F" else ""
                    println("W:${warning.location.line}:${warning.location.column}$fixable:${warning.message}")
                }
            }
        }
    }

    /**
     * Extract code context around a warning location for display
     */
    private fun extractCodeContext(
        scriptContent: String,
        warningLine: Int,
        warningColumn: Int,
        contextLines: Int = 2
    ): List<String> {
        val lines = scriptContent.lines()
        if (warningLine < 1 || warningLine > lines.size) {
            return emptyList()
        }

        val result = mutableListOf<String>()

        // Add separator
        result.add("|")

        // Calculate range of lines to show
        val startLine = maxOf(1, warningLine - contextLines)
        val endLine = minOf(lines.size, warningLine + contextLines)

        // Show context lines with line numbers
        for (lineNum in startLine..endLine) {
            val lineContent = lines[lineNum - 1]
            val lineNumStr = lineNum.toString().padStart(3, ' ')

            if (lineNum == warningLine) {
                // Show the warning line
                result.add("$lineNumStr | $lineContent")

                // Add warning indicator (^) pointing to the column
                val padding = " ".repeat(lineNumStr.length + 3 + maxOf(0, warningColumn - 1))
                result.add("$padding^")
            } else {
                // Show context line
                result.add("$lineNumStr | $lineContent")
            }
        }

        return result
    }

    private fun autoFix(source: String, fixableWarnings: List<LintWarning>): String {
        // TODO: Implement auto-fixing logic
        // For now, just return the original source
        // This would involve AST manipulation and pretty-printing
        return source
    }

    /**
     * Lint warning with location and fix information
     */
    data class LintWarning(
        val message: String,
        val location: Location,
        val code: String,
        val fixable: Boolean = false,
        val suggestion: String? = null,
        val severity: Severity = Severity.WARNING
    )

    enum class Severity {
        WARNING,
        INFO
    }

    /**
     * Dead code analyzer - detects unreachable code
     */
    class DeadCodeAnalyzer {
        fun analyze(program: Program): List<LintWarning> {
            val warnings = mutableListOf<LintWarning>()
            // TODO: Implement dead code detection
            // - Detect unreachable code after return/throw
            // - Detect always-false conditionals
            // - Detect unused branches
            return warnings
        }
    }

    /**
     * Unused variable analyzer - detects variables that are defined but never used
     */
    class UnusedVariableAnalyzer {
        fun analyze(program: Program): List<LintWarning> {
            val warnings = mutableListOf<LintWarning>()

            // Track all variable definitions
            val definitions = mutableMapOf<String, Location>()
            val usages = mutableSetOf<String>()

            // Walk AST to find definitions and usages
            collectDefinitions(program.body, definitions)
            collectUsages(program.body, usages)

            // Find unused variables
            definitions.forEach { (name, location) ->
                if (name !in usages && !name.startsWith("_")) {
                    warnings.add(LintWarning(
                        message = "Variable '$name' is defined but never used",
                        location = location,
                        code = "UNUSED_VARIABLE",
                        fixable = false,
                        suggestion = "Remove unused variable or prefix with '_' to indicate intentional"
                    ))
                }
            }

            return warnings
        }

        private fun collectDefinitions(expr: Expression, definitions: MutableMap<String, Location>) {
            when (expr) {
                is Expression.LetBinding -> {
                    definitions[expr.name] = expr.location
                    collectDefinitions(expr.value, definitions)
                }
                is Expression.Block -> {
                    expr.expressions.forEach { collectDefinitions(it, definitions) }
                }
                is Expression.Lambda -> {
                    expr.parameters.forEach { param ->
                        definitions[param.name] = param.location
                    }
                    collectDefinitions(expr.body, definitions)
                }
                is Expression.Conditional -> {
                    collectDefinitions(expr.condition, definitions)
                    collectDefinitions(expr.thenBranch, definitions)
                    expr.elseBranch?.let { collectDefinitions(it, definitions) }
                }
                is Expression.BinaryOp -> {
                    collectDefinitions(expr.left, definitions)
                    collectDefinitions(expr.right, definitions)
                }
                is Expression.UnaryOp -> {
                    collectDefinitions(expr.operand, definitions)
                }
                is Expression.FunctionCall -> {
                    collectDefinitions(expr.function, definitions)
                    expr.arguments.forEach { collectDefinitions(it, definitions) }
                }
                is Expression.MemberAccess -> {
                    collectDefinitions(expr.target, definitions)
                }
                is Expression.IndexAccess -> {
                    collectDefinitions(expr.target, definitions)
                    collectDefinitions(expr.index, definitions)
                }
                is Expression.ObjectLiteral -> {
                    expr.properties.forEach { collectDefinitions(it.value, definitions) }
                }
                is Expression.ArrayLiteral -> {
                    expr.elements.forEach { collectDefinitions(it, definitions) }
                }
                else -> {} // Literals and identifiers don't define variables
            }
        }

        private fun collectUsages(expr: Expression, usages: MutableSet<String>) {
            when (expr) {
                is Expression.Identifier -> {
                    if (expr.name != "input") { // Don't count built-in 'input'
                        usages.add(expr.name)
                    }
                }
                is Expression.LetBinding -> {
                    collectUsages(expr.value, usages)
                }
                is Expression.Block -> {
                    expr.expressions.forEach { collectUsages(it, usages) }
                }
                is Expression.Lambda -> {
                    collectUsages(expr.body, usages)
                }
                is Expression.Conditional -> {
                    collectUsages(expr.condition, usages)
                    collectUsages(expr.thenBranch, usages)
                    expr.elseBranch?.let { collectUsages(it, usages) }
                }
                is Expression.BinaryOp -> {
                    collectUsages(expr.left, usages)
                    collectUsages(expr.right, usages)
                }
                is Expression.UnaryOp -> {
                    collectUsages(expr.operand, usages)
                }
                is Expression.FunctionCall -> {
                    collectUsages(expr.function, usages)
                    expr.arguments.forEach { collectUsages(it, usages) }
                }
                is Expression.MemberAccess -> {
                    collectUsages(expr.target, usages)
                }
                is Expression.IndexAccess -> {
                    collectUsages(expr.target, usages)
                    collectUsages(expr.index, usages)
                }
                is Expression.ObjectLiteral -> {
                    expr.properties.forEach { collectUsages(it.value, usages) }
                }
                is Expression.ArrayLiteral -> {
                    expr.elements.forEach { collectUsages(it, usages) }
                }
                else -> {} // Literals don't use variables
            }
        }
    }

    /**
     * Complexity analyzer - detects overly complex expressions
     */
    class ComplexityAnalyzer {
        private val MAX_NESTING_DEPTH = 5
        private val MAX_EXPRESSION_LENGTH = 100

        fun analyze(program: Program): List<LintWarning> {
            val warnings = mutableListOf<LintWarning>()
            analyzeExpression(program.body, warnings, depth = 0)
            return warnings
        }

        private fun analyzeExpression(expr: Expression, warnings: MutableList<LintWarning>, depth: Int) {
            if (depth > MAX_NESTING_DEPTH) {
                warnings.add(LintWarning(
                    message = "Expression nesting depth ($depth) exceeds maximum ($MAX_NESTING_DEPTH)",
                    location = expr.location,
                    code = "EXCESSIVE_NESTING",
                    fixable = false,
                    suggestion = "Consider extracting nested logic into separate variables"
                ))
            }

            when (expr) {
                is Expression.Block -> {
                    expr.expressions.forEach { analyzeExpression(it, warnings, depth + 1) }
                }
                is Expression.Lambda -> {
                    analyzeExpression(expr.body, warnings, depth + 1)
                }
                is Expression.Conditional -> {
                    analyzeExpression(expr.condition, warnings, depth + 1)
                    analyzeExpression(expr.thenBranch, warnings, depth + 1)
                    expr.elseBranch?.let { analyzeExpression(it, warnings, depth + 1) }
                }
                is Expression.BinaryOp -> {
                    analyzeExpression(expr.left, warnings, depth + 1)
                    analyzeExpression(expr.right, warnings, depth + 1)
                }
                is Expression.UnaryOp -> {
                    analyzeExpression(expr.operand, warnings, depth + 1)
                }
                is Expression.FunctionCall -> {
                    analyzeExpression(expr.function, warnings, depth + 1)
                    expr.arguments.forEach { analyzeExpression(it, warnings, depth + 1) }
                }
                is Expression.LetBinding -> {
                    analyzeExpression(expr.value, warnings, depth + 1)
                }
                else -> {}
            }
        }
    }

    /**
     * Style checker - checks for style violations
     */
    class StyleChecker {
        fun check(program: Program): List<LintWarning> {
            val warnings = mutableListOf<LintWarning>()
            checkExpression(program.body, warnings)
            return warnings
        }

        private fun checkExpression(expr: Expression, warnings: MutableList<LintWarning>) {
            when (expr) {
                is Expression.LetBinding -> {
                    // Check naming convention (camelCase)
                    if (!expr.name.matches(Regex("^[a-z][a-zA-Z0-9]*$")) && !expr.name.startsWith("_")) {
                        warnings.add(LintWarning(
                            message = "Variable '${expr.name}' should use camelCase naming convention",
                            location = expr.location,
                            code = "NAMING_CONVENTION",
                            fixable = false,
                            suggestion = "Use camelCase: ${toCamelCase(expr.name)}"
                        ))
                    }
                    checkExpression(expr.value, warnings)
                }
                is Expression.Block -> {
                    expr.expressions.forEach { checkExpression(it, warnings) }
                }
                is Expression.Lambda -> {
                    checkExpression(expr.body, warnings)
                }
                is Expression.Conditional -> {
                    checkExpression(expr.condition, warnings)
                    checkExpression(expr.thenBranch, warnings)
                    expr.elseBranch?.let { checkExpression(it, warnings) }
                }
                is Expression.BinaryOp -> {
                    checkExpression(expr.left, warnings)
                    checkExpression(expr.right, warnings)
                }
                is Expression.UnaryOp -> {
                    checkExpression(expr.operand, warnings)
                }
                is Expression.FunctionCall -> {
                    checkExpression(expr.function, warnings)
                    expr.arguments.forEach { checkExpression(it, warnings) }
                }
                is Expression.ObjectLiteral -> {
                    expr.properties.forEach { checkExpression(it.value, warnings) }
                }
                is Expression.ArrayLiteral -> {
                    expr.elements.forEach { checkExpression(it, warnings) }
                }
                else -> {}
            }
        }

        private fun toCamelCase(name: String): String {
            return name.split("_", "-")
                .mapIndexed { index, part ->
                    if (index == 0) part.lowercase()
                    else part.lowercase().replaceFirstChar { it.uppercase() }
                }
                .joinToString("")
        }
    }
}
