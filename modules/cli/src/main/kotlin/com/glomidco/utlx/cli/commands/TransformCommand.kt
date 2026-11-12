// modules/cli/src/main/kotlin/com/glomidco/utlx/cli/commands/TransformCommand.kt
package com.glomidco.utlx.cli.commands

import com.glomidco.utlx.cli.service.TransformationService
import com.glomidco.utlx.cli.capture.TestCaptureService
import com.glomidco.utlx.cli.CommandResult
import com.glomidco.utlx.core.debug.DebugConfig
import com.glomidco.utlx.core.format.ParseProfile
import java.io.File
import com.glomidco.utlx.core.udm.UDM
import com.glomidco.utlx.formats.json.JSONSerializer

/**
 * Transform command - CLI wrapper for TransformationService
 * Handles file I/O and delegates transformation logic to TransformationService
 *
 * Usage:
 *   utlx transform <input-file> <script-file> [options]
 *   utlx transform <script-file> [options]  # reads from stdin
 *   cat data.xml | utlx                     # identity mode: auto-detect input, smart flip output
 *   cat data.xml | utlx --to json           # identity mode with explicit output format
 */
object TransformCommand {

    // Create service instance
    private val transformationService = TransformationService()

    data class TransformOptions(
        val namedInputs: Map<String, File> = emptyMap(),        // Named inputs: input1=file1.xml, input2=file2.json
        val namedOutputs: Map<String, File> = emptyMap(),       // Named outputs: summary=out.json, details=out.xml
        val scriptFile: File? = null,                           // null = identity mode (passthrough)
        val expression: String? = null,                         // -e inline expression
        val inputFormat: String? = null,
        val outputFormat: String? = null,
        val verbose: Boolean = false,
        val pretty: Boolean = true,
        val rawOutput: Boolean = false,                         // -r strip quotes from string output
        val args: Map<String, String> = emptyMap(),             // F27: --arg name=value, bound as a string
        val jsonArgs: Map<String, String> = emptyMap(),         // F27: --argjson name=<json>, parsed as JSON
        val nullInput: Boolean = false,                         // F27: -n, run with no input (${'$'}input = null)
        val exitStatus: Boolean = false,                        // F27: --exit-status, exit 1 on null/false
        val captureEnabled: Boolean? = null,  // null = use config, true = force enable, false = force disable
        val debugLevel: DebugConfig.LogLevel? = null,  // Global debug level
        val debugComponents: Set<DebugConfig.Component> = emptySet(),  // Component-specific debug
        val strictTypes: Boolean = false,  // Enforce type checking (fail on type errors)
        val identityMode: Boolean = false,  // true = no script, passthrough with smart format flip
        val charset: String? = null,  // B20: explicit input charset override (e.g., "UTF-16", "ISO-8859-1")
        val parseProfile: String? = null,  // F23: parser strictness profile (strict|standard|lenient); null = standard
        val languageMode: String? = null  // F21: engine language mode (1.0|1.1|utlxe|utlxs); null = latest (1.1)
    ) {
        // Backward compatibility properties
        val inputFile: File? get() = namedInputs["input"] ?: namedInputs.values.firstOrNull()
        val outputFile: File? get() = namedOutputs["output"] ?: namedOutputs.values.firstOrNull()
        val hasMultipleInputs: Boolean get() = namedInputs.size > 1
        val hasMultipleOutputs: Boolean get() = namedOutputs.size > 1
    }

    /**
     * Detect format from data content (same logic as TransformationService auto-detect)
     */
    private fun detectFormatFromContent(data: String): String {
        val trimmed = data.trim()
        return when {
            trimmed.startsWith("<") -> "xml"
            trimmed.startsWith("{") || trimmed.startsWith("[") -> "json"
            trimmed.startsWith("---") || trimmed.contains(":\n") || trimmed.contains(": ") -> "yaml"
            trimmed.contains(",") && trimmed.lines().size > 1 -> {
                val lines = trimmed.lines().filter { it.isNotBlank() }
                val firstLineCommas = lines.firstOrNull()?.count { it == ',' } ?: 0
                if (firstLineCommas > 0 && lines.take(3).all { it.count { c -> c == ',' } == firstLineCommas }) {
                    "csv"
                } else {
                    "json"
                }
            }
            else -> "json"
        }
    }

    /**
     * Smart format flip: choose the most useful output format based on detected input.
     * XML↔JSON is the #1 use case; everything else defaults to JSON.
     */
    private fun inferOutputFormat(detectedInputFormat: String): String {
        return when (detectedInputFormat) {
            "xml"  -> "json"
            "json" -> "xml"
            else   -> "json"
        }
    }

    /**
     * Expand dot shorthand to $input references in -e expressions.
     * This is a pre-processing step before the expression is wrapped in a UTL-X script.
     *
     * Rules:
     * - "." alone → "$input" (identity)
     * - ".name" at start → "$input.name"
     * - "..name" at start → "$input..name" (recursive descent)
     * - ".name" after (, =>, , or whitespace → "$input.name"
     * - Dots inside strings are not touched
     * - "$input.name" is left unchanged
     */
    private fun expandDotShorthand(expression: String): String {
        if (!expression.contains('.')) return expression
        if (expression.contains("\$input")) return expression // already explicit

        val result = StringBuilder()
        var i = 0
        var inString = false
        var stringChar = ' '

        while (i < expression.length) {
            val ch = expression[i]

            // Track string boundaries
            if (!inString && (ch == '"' || ch == '\'')) {
                inString = true
                stringChar = ch
                result.append(ch)
                i++
                continue
            }
            if (inString) {
                if (ch == stringChar && (i == 0 || expression[i - 1] != '\\')) {
                    inString = false
                }
                result.append(ch)
                i++
                continue
            }

            // Check for dot that should be expanded
            if (ch == '.') {
                // Is this a dot at a position where it starts a path expression?
                val prevChar = if (i > 0) expression[i - 1] else ' '
                val isPathStart = i == 0 ||
                    prevChar == '(' || prevChar == ',' || prevChar == ' ' || prevChar == '\t' ||
                    prevChar == '\n' || prevChar == '>' // => arrow

                if (isPathStart) {
                    // Check for .. (recursive descent)
                    if (i + 1 < expression.length && expression[i + 1] == '.') {
                        result.append("\$input..")
                        i += 2
                    } else if (i + 1 < expression.length && (expression[i + 1].isLetterOrDigit() || expression[i + 1] == '@' || expression[i + 1] == '*')) {
                        // .name or .@attr or .*
                        result.append("\$input.")
                        i++
                    } else if (i + 1 >= expression.length || expression[i + 1] == ')' || expression[i + 1] == ',' || expression[i + 1] == ' ') {
                        // Standalone dot = $input
                        result.append("\$input")
                        i++
                    } else {
                        result.append(ch)
                        i++
                    }
                } else {
                    // Regular dot (part of a path like obj.name) — leave as-is
                    result.append(ch)
                    i++
                }
            } else {
                result.append(ch)
                i++
            }
        }

        return result.toString()
    }
    
    fun execute(args: Array<String>, identityMode: Boolean = false): CommandResult {
        val options = try {
            parseOptions(args, allowIdentityMode = identityMode)
        } catch (e: IllegalStateException) {
            // Special case: --help was requested
            if (e.message == "HELP_REQUESTED") {
                return CommandResult.Success
            }
            return CommandResult.Failure(e.message ?: "Unknown error", 1)
        } catch (e: IllegalArgumentException) {
            // B26: argument-parsing errors carry their message here; Main.kt prints it once as
            // "Error: <message>". parseOptions no longer prints the message itself (only usage).
            return CommandResult.Failure(e.message ?: "Invalid arguments", 1)
        }

        // Apply debug settings from CLI flags
        options.debugLevel?.let { level ->
            DebugConfig.setGlobalLogLevel(level)
        }
        options.debugComponents.forEach { component ->
            DebugConfig.enableComponent(component)
        }

        if (options.verbose) {
            if (options.expression != null) {
                println("UTL-X Expression Mode")
                println("Expression: ${options.expression}")
            } else if (options.identityMode) {
                println("UTL-X Identity Transform (passthrough with format conversion)")
            } else {
                println("UTL-X Transform")
                println("Script: ${options.scriptFile!!.absolutePath}")
            }
            options.inputFile?.let { println("Input: ${it.absolutePath}") }
            options.outputFile?.let { println("Output: ${it.absolutePath}") }
        }

        // Track execution time for capture
        val startTime = System.currentTimeMillis()
        var captureSuccess = false
        var captureError: String? = null
        var captureOutputData = ""

        try {
            // Step 2: Read input files and create InputData map
            // F27: -n runs with no input at all (jq -n), for computation and data generation.
            // A bare `null` JSON document already binds $input to null — typeOf() reports
            // "null" and isNull() is true — so this needs no new binding mechanism and no
            // special case downstream. It also bypasses the interactive-stdin guard below,
            // which would otherwise fail for the entirely reasonable `utlx -n -e '1+1'`.
            val inputs = if (options.nullInput) {
                mapOf("input" to TransformationService.InputData(content = "null", format = "json"))
            } else if (options.namedInputs.isNotEmpty()) {
                // Named inputs from --input flags
                options.namedInputs.mapValues { (name, file) ->
                    val inputFormat = options.inputFormat
                    val charsetHint = options.charset?.let {
                        try { java.nio.charset.Charset.forName(it) } catch (_: Exception) { null }
                    }

                    if (options.verbose && inputFormat != null) {
                        println("Input '$name' format: $inputFormat (from CLI option)")
                    }

                    // B20: read raw bytes when charset is specified (non-UTF-8 safe)
                    if (charsetHint != null) {
                        TransformationService.InputData(
                            content = file.readText(charsetHint),  // String fallback for backward compat
                            format = inputFormat,
                            bytes = file.readBytes(),
                            charset = charsetHint
                        )
                    } else {
                        TransformationService.InputData(
                            content = file.readText(),
                            format = inputFormat
                        )
                    }
                }
            } else {
                // No named inputs - read from stdin (backward compat).
                // B26: guard against blocking forever on an interactive terminal. readStdin() calls
                // readLine(), which waits indefinitely for typed input + EOF when nothing is piped.
                // System.console() != null means stdin is a TTY (not redirected) — same heuristic
                // Main.kt uses to detect a pipe. In that case there is no input to read: fail fast.
                // The `utlx.stdin.interactive` property is a test seam: a subprocess has no real
                // console, so tests set it to drive this branch; production leaves it unset.
                val stdinIsInteractive = System.getProperty("utlx.stdin.interactive")
                    ?.toBooleanStrictOrNull() ?: (System.console() != null)
                if (stdinIsInteractive) {
                    // Verb-neutral: this code path is shared by `transform` and `convert`, so the
                    // message names neither a verb nor a script — just the two ways to supply input.
                    return CommandResult.Failure(
                        "No input provided. Pass an input file (-i/--input FILE) " +
                        "or pipe data via stdin.", 1)
                }
                val inputFormat = options.inputFormat
                val charsetHint = options.charset?.let {
                    try { java.nio.charset.Charset.forName(it) } catch (_: Exception) { null }
                }

                if (options.verbose && inputFormat != null) {
                    println("Input format: $inputFormat (from CLI option)")
                }

                // B20: read raw bytes from stdin when --charset is set (non-UTF-8 safe)
                if (charsetHint != null) {
                    val stdinBytes = System.`in`.readBytes()
                    mapOf("input" to TransformationService.InputData(
                        content = String(stdinBytes, charsetHint),
                        format = inputFormat,
                        bytes = stdinBytes,
                        charset = charsetHint
                    ))
                } else {
                    val inputData = readStdin()
                    mapOf("input" to TransformationService.InputData(
                        content = inputData,
                        format = inputFormat
                    ))
                }
            }

            // F27: --argjson values join the inputs map as JSON documents, so they are read by
            // the real JSON parser under the resolved --parse-profile. Appended AFTER the real
            // inputs: `inputs.values.first()` below selects the primary input, and an arg must
            // never become it. `hasMultipleInputs` reads options.namedInputs, not this map, so
            // adding entries here cannot flip the multi-input code paths either.
            val inputsWithArgs = if (options.jsonArgs.isEmpty()) inputs else {
                val clash = options.jsonArgs.keys.intersect(inputs.keys)
                if (clash.isNotEmpty()) {
                    return CommandResult.Failure(
                        "--argjson name(s) ${clash.joinToString(", ")} collide with an input of the same name", 1)
                }
                inputs + options.jsonArgs.mapValues { (_, json) ->
                    TransformationService.InputData(content = json, format = "json")
                }
            }

            // Step 1: Determine script content (file, expression, or identity)
            val scriptContent: String
            val effectiveOutputFormat: String?

            if (options.expression != null) {
                // Expression mode: synthesize script from inline expression
                val primaryInput = inputs.values.first()
                val expandedExpression = expandDotShorthand(options.expression)

                // Expression mode defaults to JSON output
                effectiveOutputFormat = options.outputFormat ?: "json"

                if (options.verbose) {
                    println("Expression (expanded): $expandedExpression")
                    println("Output format: $effectiveOutputFormat" +
                        if (options.outputFormat != null) " (explicit)" else " (default json)")
                }

                // F27: --arg values become `let` bindings ahead of the body. A top-level let
                // followed by the expression is valid UTL-X, so this needs no language change
                // and no header plumbing. Values are bound as STRINGS, like jq's --arg.
                val argBindings = options.args.entries.joinToString("") { (name, value) ->
                    "let $name = \"${escapeUtlxString(value)}\"\n"
                }
                scriptContent = """%utlx 1.0
input auto
output $effectiveOutputFormat
---
$argBindings$expandedExpression"""
            } else if (options.identityMode) {
                // Identity mode: synthesize a passthrough script with smart format flip
                val primaryInput = inputs.values.first()
                val detectedInputFormat = options.inputFormat
                    ?: detectFormatFromContent(primaryInput.content)

                // User-specified output format wins; otherwise smart flip
                effectiveOutputFormat = options.outputFormat
                    ?: inferOutputFormat(detectedInputFormat)

                if (options.verbose) {
                    println("Detected input format: $detectedInputFormat")
                    println("Output format: $effectiveOutputFormat" +
                        if (options.outputFormat != null) " (explicit)" else " (smart flip)")
                }

                scriptContent = """%utlx 1.0
input auto
output $effectiveOutputFormat
---
${"$"}input"""
            } else {
                // Normal mode: read script from file
                scriptContent = options.scriptFile!!.readText()
                // B26: friendly message for an empty script, instead of the parser-internal
                // "Expected '---' separator after header" the user would otherwise see.
                if (scriptContent.isBlank()) {
                    return CommandResult.Failure(
                        "Script file is empty: ${options.scriptFile!!.absolutePath}", 1)
                }
                effectiveOutputFormat = options.outputFormat
            }

            // Step 3: Call TransformationService
            // F23: resolve the parse profile (operator-selected; default standard). A guard pins this
            // via deployment config, not here — the CLI flag is for the mapper/dev.
            val resolvedProfile = when (options.parseProfile?.lowercase()) {
                null, "standard" -> ParseProfile.STANDARD
                "strict" -> ParseProfile.STRICT
                "lenient" -> ParseProfile.LENIENT
                else -> throw IllegalArgumentException(
                    "Invalid --parse-profile '${options.parseProfile}' (expected: strict | standard | lenient)")
            }
            if (options.verbose && options.parseProfile != null) {
                println("Parse profile: ${resolvedProfile.name.lowercase()} (from CLI option)")
            }
            // F21: resolve the engine language mode (default = latest). "utlxe"/"utlxs" are the deployment
            // vocabulary for the 1.0-only / 1.1 modes; "1.0"/"1.1" are accepted directly.
            val resolvedLanguageMode = when (options.languageMode?.lowercase()) {
                null -> com.glomidco.utlx.core.semantic.LanguageVersions.LATEST
                "1.0", "utlxe" -> "1.0"
                "1.1", "utlxs" -> "1.1"
                else -> throw IllegalArgumentException(
                    "Invalid --language-mode '${options.languageMode}' (expected: 1.0 | 1.1 | utlxe | utlxs)")
            }
            if (options.verbose && options.languageMode != null) {
                println("Language mode: %utlx $resolvedLanguageMode (from CLI option)")
            }
            val serviceOptions = TransformationService.TransformOptions(
                verbose = options.verbose,
                pretty = options.pretty,
                strictTypes = options.strictTypes,
                overrideOutputFormat = effectiveOutputFormat,
                parseProfile = resolvedProfile,
                maxLanguageVersion = resolvedLanguageMode
            )

            // F27: -r needs to know whether the result is a scalar or a sequence, which the
            // serialized string cannot tell it reliably. transformWithUdm (added for EB14)
            // hands back the result UDM alongside the rendered text.
            val outcome = transformationService.transformWithUdm(scriptContent, inputsWithArgs, serviceOptions)
            val outputData = outcome.output
            val outputFormat = outcome.outputFormat
            captureOutputData = outputData
            captureSuccess = true

            if (options.verbose) {
                println("Output format: $outputFormat")
            }

            // Write output (apply raw mode if requested)
            val finalOutput = if (options.rawOutput) {
                renderRaw(outcome.outputUDM, outputData)
            } else {
                outputData
            }

            val outputFilePath = options.outputFile
            if (outputFilePath != null) {
                outputFilePath.writeText(finalOutput)
                if (options.verbose) {
                    println("✓ Transformation complete: ${outputFilePath.absolutePath}")
                }
            } else {
                println(finalOutput)
            }

            // Step 4: Capture successful execution (for single input only, skip identity/expression mode)
            val durationMs = System.currentTimeMillis() - startTime
            if (!options.hasMultipleInputs && !options.identityMode && options.expression == null && options.scriptFile != null) {
                val primaryInputData = inputs.values.firstOrNull()
                val captureInputFormat = primaryInputData?.format ?: "json"

                TestCaptureService.captureExecution(
                    transformation = scriptContent,
                    inputData = primaryInputData?.content ?: "",
                    inputFormat = captureInputFormat,
                    outputData = outputData,
                    outputFormat = outputFormat,
                    success = true,
                    error = null,
                    durationMs = durationMs,
                    scriptFile = options.scriptFile!!,
                    overrideEnabled = options.captureEnabled
                )
            }

            // F27: --exit-status mirrors `jq -e` — the result decides the exit code, so a
            // transformation can be used as a shell predicate. Output has already been written
            // above; only the status changes. The failure message is EMPTY on purpose: Main.kt
            // prints `Error: <message>` for a non-empty one, and a predicate returning false is
            // not an error.
            if (options.exitStatus) {
                val r = outcome.outputUDM
                val falsy = r is UDM.Scalar && (r.value == null || r.value == false)
                if (falsy) return CommandResult.Failure("", 1)
            }

            return CommandResult.Success

        } catch (e: Exception) {
            // Capture failed execution
            captureError = e.message ?: "Unknown error"
            val durationMs = System.currentTimeMillis() - startTime

            // Try to capture the failure (only for single input, skip identity mode)
            if (!options.hasMultipleInputs && !options.identityMode && options.scriptFile != null) {
                try {
                    val scriptContent = options.scriptFile.readText()
                    val inputFilePath = options.inputFile
                    val inputData = if (inputFilePath != null) {
                        inputFilePath.readText()
                    } else {
                        "" // Can't capture stdin after error
                    }
                    val inputFormat = options.inputFormat ?: "json"

                    TestCaptureService.captureExecution(
                        transformation = scriptContent,
                        inputData = inputData,
                        inputFormat = inputFormat,
                        outputData = captureError,
                        outputFormat = options.outputFormat ?: inputFormat,
                        success = false,
                        error = captureError,
                        durationMs = durationMs,
                        scriptFile = options.scriptFile!!,
                        overrideEnabled = options.captureEnabled
                    )
                } catch (captureException: Exception) {
                    // Silently fail capture on error
                    if (options.verbose) {
                        System.err.println("  [Capture] Failed to capture error: ${captureException.message}")
                    }
                }
            }

            // Return failure with error message
            return CommandResult.Failure(e.message ?: "Transformation failed", 1)
        }
    }
    
    private fun detectFormat(data: String, extension: String?): String {
        // Try extension first
        extension?.lowercase()?.let {
            if (it in listOf("xml", "json", "csv", "yaml", "yml", "xsd", "jsch", "avro", "avsc", "proto")) {
                return when (it) {
                    "yml" -> "yaml"
                    "jsch" -> "jsch"  // JSON Schema files
                    "avsc" -> "avro"  // Avro schema files (.avsc extension)
                    "proto" -> "proto"  // Protocol Buffers schema files
                    else -> it
                }
            }
        }
        
        // Auto-detect from content
        val trimmed = data.trim()
        return when {
            trimmed.startsWith("<") -> "xml"
            trimmed.startsWith("{") || trimmed.startsWith("[") || trimmed.startsWith("\"") -> "json"
            trimmed.contains("---") || trimmed.contains(":") && !trimmed.contains(",") -> "yaml"
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
    
    /**
     * Parse options - supports both normal mode (with script file) and identity mode (no script).
     * @param allowIdentityMode if true, missing script file triggers identity mode instead of error.
     *                          Set to true when invoked from Main.kt's implicit routing.
     */
    fun parseOptions(args: Array<String>, allowIdentityMode: Boolean = false): TransformOptions {
        // Identity mode: no args at all means passthrough (read stdin, smart flip output)
        if (args.isEmpty() && allowIdentityMode) {
            return TransformOptions(identityMode = true)
        }
        if (args.isEmpty()) {
            printUsage()
            throw IllegalArgumentException("No arguments provided")
        }

        val namedInputs = mutableMapOf<String, File>()
        val namedOutputs = mutableMapOf<String, File>()
        var scriptFile: File? = null
        var expression: String? = null
        var inputFormat: String? = null
        var outputFormat: String? = null
        var verbose = false
        var pretty = true
        var rawOutput = false
        val argValues = mutableMapOf<String, String>()
        val jsonArgValues = mutableMapOf<String, String>()
        var nullInput = false
        var exitStatus = false
        var captureEnabled: Boolean? = null
        var debugLevel: DebugConfig.LogLevel? = null
        val debugComponents = mutableSetOf<DebugConfig.Component>()
        var strictTypes = false
        var charset: String? = null
        var parseProfile: String? = null
        var languageMode: String? = null

        var i = 0
        while (i < args.size) {
            when (args[i]) {
                "-o", "--output" -> {
                    val arg = args[++i]
                    // Parse: either "file.xml" or "name=file.xml"
                    if (arg.contains("=")) {
                        val (name, path) = arg.split("=", limit = 2)
                        namedOutputs[name] = File(path)
                    } else {
                        namedOutputs["output"] = File(arg)
                    }
                }
                "-i", "--input" -> {
                    val arg = args[++i]
                    // Parse: either "file.xml" or "name=file.xml"
                    if (arg.contains("=")) {
                        val (name, path) = arg.split("=", limit = 2)
                        namedInputs[name] = File(path)
                    } else {
                        namedInputs["input"] = File(arg)
                    }
                }
                "-e", "--expression" -> {
                    expression = args[++i]
                }
                "-r", "--raw-output" -> {
                    rawOutput = true
                }
                "-n", "--null-input" -> {
                    nullInput = true
                }
                "--exit-status" -> {
                    // Deliberately long-form only. jq spells this `-e`, which here already means
                    // --expression; aliasing it to `-E` would be a one-keystroke typo away from
                    // silently selecting the wrong mode.
                    exitStatus = true
                }
                "--arg" -> {
                    // F27: --arg name=value. jq passes args as strings and so do we; coerce in
                    // the expression with toNumber()/toBoolean() when a typed value is wanted.
                    val raw = args[++i]
                    val eq = raw.indexOf('=')
                    if (eq <= 0) throw IllegalArgumentException(
                        "Invalid --arg '$raw' (expected name=value)")
                    val name = raw.substring(0, eq)
                    if (!name.matches(Regex("[A-Za-z_][A-Za-z0-9_]*"))) throw IllegalArgumentException(
                        "Invalid --arg name '$name' (must be a valid identifier: letters, digits, underscore; not starting with a digit)")
                    argValues[name] = raw.substring(eq + 1)
                }
                "--argjson" -> {
                    // F27: --argjson name=<json>. The value is handed to the real JSON reader
                    // (as a named input) rather than spliced into the script as a literal. The
                    // expression lexer would *mostly* agree with the JSON parser, but not on
                    // duplicate keys: the JSON reader rejects them under the standard profile
                    // while the lexer silently takes the last (B01's failure mode, which F23's
                    // strictness profiles exist to prevent). One JSON reader, one policy.
                    val raw = args[++i]
                    val eq = raw.indexOf('=')
                    if (eq <= 0) throw IllegalArgumentException(
                        "Invalid --argjson '$raw' (expected name=<json>)")
                    val name = raw.substring(0, eq)
                    if (!name.matches(Regex("[A-Za-z_][A-Za-z0-9_]*"))) throw IllegalArgumentException(
                        "Invalid --argjson name '$name' (must be a valid identifier)")
                    jsonArgValues[name] = raw.substring(eq + 1)
                }
                "--input-format", "--from" -> {
                    inputFormat = args[++i]
                }
                "--output-format", "--to" -> {
                    outputFormat = args[++i]
                }
                "-v", "--verbose" -> {
                    verbose = true
                }
                "--no-pretty" -> {
                    pretty = false
                }
                "--charset" -> {
                    charset = args[++i]
                }
                "--parse-profile" -> {
                    parseProfile = args[++i]
                }
                "--language-mode" -> {
                    languageMode = args[++i]
                }
                "--strict-types" -> {
                    strictTypes = true
                }
                "--capture" -> {
                    captureEnabled = true
                }
                "--no-capture" -> {
                    captureEnabled = false
                }
                "--debug" -> {
                    debugLevel = DebugConfig.LogLevel.DEBUG
                }
                "--debug-parser" -> {
                    debugComponents.add(DebugConfig.Component.PARSER)
                }
                "--debug-lexer" -> {
                    debugComponents.add(DebugConfig.Component.LEXER)
                }
                "--debug-interpreter" -> {
                    debugComponents.add(DebugConfig.Component.INTERPRETER)
                }
                "--debug-types" -> {
                    debugComponents.add(DebugConfig.Component.TYPE_SYSTEM)
                }
                "--debug-all" -> {
                    debugLevel = DebugConfig.LogLevel.DEBUG
                }
                "--trace" -> {
                    debugLevel = DebugConfig.LogLevel.TRACE
                }
                "-h", "--help" -> {
                    printUsage()
                    // Special case: help is a successful operation
                    throw IllegalStateException("HELP_REQUESTED")
                }
                else -> {
                    if (!args[i].startsWith("-")) {
                        if (scriptFile == null) {
                            scriptFile = File(args[i])
                        } else if (namedInputs.isEmpty()) {
                            // Positional input file (backward compat)
                            namedInputs["input"] = File(args[i])
                        }
                    } else {
                        // B26: don't print the message here; printUsage() gives context and Main.kt
                        // prints "Error: <message>" once from the thrown exception.
                        printUsage()
                        throw IllegalArgumentException("Unknown option: ${args[i]}")
                    }
                }
            }
            i++
        }

        // Expression mode: -e provided, no script file needed
        if (expression != null) {
            if (expression.isBlank()) {
                throw IllegalArgumentException("Expression cannot be empty. Usage: utlx -e '<expression>'")
            }
            if (scriptFile != null) {
                throw IllegalArgumentException("Cannot use -e/--expression with a script file. Use one or the other.")
            }
            return TransformOptions(
                namedInputs = namedInputs,
                namedOutputs = namedOutputs,
                scriptFile = null,
                expression = expression,
                inputFormat = inputFormat,
                outputFormat = outputFormat,
                verbose = verbose,
                pretty = pretty,
                rawOutput = rawOutput,
                args = argValues,
                jsonArgs = jsonArgValues,
                nullInput = nullInput,
                exitStatus = exitStatus,
                captureEnabled = captureEnabled,
                debugLevel = debugLevel,
                debugComponents = debugComponents,
                strictTypes = strictTypes,
                charset = charset,
                parseProfile = parseProfile,
                languageMode = languageMode
            )
        }

        // F27: --arg only has an injection point in expression mode. A script file brings its
        // own header and body, and splicing bindings into someone else's source is the kind of
        // textual surgery that breaks on the first script with `---` inside a string.
        if ((argValues.isNotEmpty() || jsonArgValues.isNotEmpty()) && scriptFile != null) {
            throw IllegalArgumentException(
                "--arg/--argjson are only supported with -e/--expression. For a script file, pass values with --input name=file.")
        }

        // If no script file: identity mode (when allowed) or error
        if (scriptFile == null) {
            if (allowIdentityMode) {
                return TransformOptions(
                    namedInputs = namedInputs,
                    namedOutputs = namedOutputs,
                    scriptFile = null,
                    inputFormat = inputFormat,
                    outputFormat = outputFormat,
                    verbose = verbose,
                    pretty = pretty,
                    rawOutput = rawOutput,
                args = argValues,
                jsonArgs = jsonArgValues,
                nullInput = nullInput,
                exitStatus = exitStatus,
                    captureEnabled = captureEnabled,
                    debugLevel = debugLevel,
                    debugComponents = debugComponents,
                    strictTypes = strictTypes,
                    charset = charset,
                    identityMode = true,
                    parseProfile = parseProfile,
                    languageMode = languageMode
                )
            }
            // B26: single output channel — printUsage() for context, Main.kt prints the error once.
            printUsage()
            throw IllegalArgumentException("Script file is required")
        }

        if (!scriptFile.exists()) {
            // B26: was printed here AND again by Main.kt → duplicate. Throw only; Main.kt prints once.
            throw IllegalArgumentException("Script file not found: ${scriptFile.absolutePath}")
        }

        // Validate all input files exist
        namedInputs.forEach { (name, file) ->
            if (!file.exists()) {
                // B26: throw only (was duplicated by Main.kt). Keep the "(input: name)" detail in the message.
                throw IllegalArgumentException("Input file not found: ${file.absolutePath} (input: $name)")
            }
        }

        return TransformOptions(
            namedInputs = namedInputs,
            namedOutputs = namedOutputs,
            scriptFile = scriptFile,
            inputFormat = inputFormat,
            outputFormat = outputFormat,
            verbose = verbose,
            pretty = pretty,
            rawOutput = rawOutput,
                args = argValues,
                jsonArgs = jsonArgValues,
                nullInput = nullInput,
                exitStatus = exitStatus,
            captureEnabled = captureEnabled,
            debugLevel = debugLevel,
            debugComponents = debugComponents,
            strictTypes = strictTypes,
            charset = charset,
            parseProfile = parseProfile,
            languageMode = languageMode
        )
    }

    private fun printUsage() {
        println("""
            |Transform data using UTL-X scripts
            |
            |Usage:
            |  utlx transform <script-file> [input-file] [options]
            |  utlx transform <script-file> [options] < input-file
            |  utlx transform <script-file> --input input1=file1.xml --input input2=file2.json [options]
            |  utlx transform -e '<expression>' [input-file] [options]
            |
            |Inline expressions (no script file):
            |  cat d.json | utlx transform -e '${'$'}input.tag'                  One-off query, like jq
            |  cat d.json | utlx transform -e '${'$'}input.tag' -r               Unquoted scalar output
            |  cat d.json | utlx transform -e '${'$'}input.items[*].p'           Project over a sequence
            |  cat d.json | utlx transform -e '${'$'}input.items[*].n' -r        One result per line
            |  utlx transform -e '${'$'}input.tag' -i d.json                     Read a file instead of stdin
            |  cat d.json | utlx transform -e '${'$'}t' --arg t=12               Pass a string in
            |  cat d.json | utlx transform -e '${'$'}c.n' --argjson 'c={"n":1}'  Pass structured data
            |  utlx transform -n -e '1+1'                                   No input at all
            |
            |Identity mode (no script, format conversion):
            |  cat data.xml | utlx                        Auto-detect XML, output JSON (smart flip)
            |  cat data.json | utlx                       Auto-detect JSON, output XML (smart flip)
            |  cat data.csv | utlx                        Auto-detect CSV, output JSON
            |  cat data.xml | utlx --to yaml              Override smart flip with explicit format
            |  cat data.csv | utlx --from csv --to xml    Explicit input and output formats
            |
            |Arguments:
            |  input-file      Input data file (if not provided, reads from stdin)
            |  script-file     UTL-X transformation script (.utlx)
            |                  If omitted, identity transform is used (passthrough with format conversion)
            |
            |Options:
            |  -o, --output FILE           Write output to FILE (default: stdout)
            |      --output name=FILE      Named output for multi-output transformations
            |  -i, --input FILE            Read input from FILE
            |      --input name=FILE       Named input for multi-input transformations
            |  -e, --expression EXPR       Run EXPR as the transformation body instead of a script file.
            |                              The `%utlx` header is implied; use --from/--to to set formats.
            |                              Cannot be combined with a script file.
            |  -r, --raw-output            Emit results unquoted, for shell use (like jq -r). A scalar
            |                              prints bare; an array of scalars prints one per line.
            |                              Objects and nested arrays still render as JSON.
            |  --arg name=value            Bind a value as a string variable, usable as ${'$'}name in the
            |                              expression. Repeatable. Coerce with toNumber() if needed.
            |                              Expression mode only (-e); for a script file use --input.
            |  --argjson name=<json>       Bind a JSON value (object, array or scalar), keeping its
            |                              types. Read by the JSON parser under --parse-profile, so
            |                              e.g. duplicate keys are rejected as they are for any input.
            |                              Repeatable. Expression mode only.
            |  -n, --null-input            Run with no input at all; ${'$'}input is null. For computing
            |                              or generating data (jq -n).
            |  --exit-status               Exit 1 when the result is null or false, 0 otherwise, so a
            |                              transformation can be used as a shell test (jq spells this
            |                              -e, which here already means --expression). Output is
            |                              written either way; only the exit code changes.
            |  --charset NAME              Force the input character set (default: auto-detect)
            |  --input-format FORMAT       Force input format (xml, json, csv, yaml, odata, osch)
            |  --from FORMAT               Alias for --input-format
            |  --output-format FORMAT      Force output format (xml, json, csv, yaml, odata, osch)
            |  --to FORMAT                 Alias for --output-format
            |  -v, --verbose               Enable verbose output
            |  --no-pretty                 Disable pretty-printing
            |  --strict-types              Enforce type checking (fail on type errors)
            |  --parse-profile PROFILE     Parser strictness: strict | standard | lenient (default standard)
            |  --language-mode MODE        Engine language mode: 1.0 | 1.1 | utlxe | utlxs (default latest/1.1).
            |                              1.0 (utlxe) rejects %utlx 1.1 scripts (e.g. validate.*)
            |  --capture                   Force enable test capture (overrides config)
            |  --no-capture                Force disable test capture (overrides config)
            |  -h, --help                  Show this help message
            |
            |Debug Options:
            |  --debug                     Enable DEBUG level logging for all components
            |  --debug-parser              Enable DEBUG logging for parser only
            |  --debug-lexer               Enable DEBUG logging for lexer only
            |  --debug-interpreter         Enable DEBUG logging for interpreter only
            |  --debug-types               Enable DEBUG logging for type system only
            |  --debug-all                 Enable DEBUG logging for all components (same as --debug)
            |  --trace                     Enable TRACE level logging (most verbose)
            |
            |Identity Mode (Smart Format Flip):
            |  When no script file is provided, utlx performs a passthrough (identity) transform.
            |  The output format is automatically chosen as the "opposite" of the detected input:
            |    XML  -> JSON     (most common conversion)
            |    JSON -> XML      (most common conversion)
            |    CSV  -> JSON     (JSON is universal interchange)
            |    YAML -> JSON     (JSON is universal interchange)
            |  Use --to to override the smart default.
            |
            |Examples:
            |  # Identity mode: format conversion without a script
            |  cat data.xml | utlx                                    # XML to JSON
            |  cat data.json | utlx                                   # JSON to XML
            |  cat data.csv | utlx --to yaml                          # CSV to YAML
            |
            |  # Single input/output (backward compatible)
            |  utlx transform script.utlx input.xml -o output.json
            |
            |  # Multiple named inputs
            |  utlx transform script.utlx --input input1=customer.xml --input input2=orders.json -o output.xml
            |
            |  # Multiple named outputs
            |  utlx transform script.utlx -i data.xml --output summary=sum.json --output details=det.xml
            |
            |  # Inline expressions, for shell pipelines
            |  gh api repos/o/r/releases/latest | utlx transform -e '${'$'}input.tag_name' -r
            |  cat orders.json | utlx transform -e 'sum(${'$'}input.items[*].price)' -r
            |  cat data.xml | utlx transform -e '${'$'}input.r.i.n' --to json
            |  for n in ${'$'}(cat d.json | utlx transform -e '${'$'}input.items[*].n' -r); do echo "${'$'}n"; done
            |  cat d.json | utlx transform -e 'filter(${'$'}input.items, (i) -> i.p > toNumber(${'$'}min)).*.n' -r --arg min=12
            |  utlx transform -n -e '1+1'                              # no input at all
            |  if utlx transform -e '${'$'}input.enabled' --exit-status < cfg.json >/dev/null; then echo on; fi
        """.trimMargin())
    }

    /**
     * F27: render a result for shell consumption (`-r`), the equivalent of `jq -r`.
     *
     * A **scalar** renders as its own text with no JSON quoting. A **sequence of scalars**
     * renders one element per line, which is what makes `for x in $(utlx ... -r)` and
     * `while read` work at all — the previous behaviour left an array as pretty JSON, so
     * every list extraction needed a `tail`/`tr` pipeline to be usable.
     *
     * Each scalar is rendered **by the JSON serializer**, then unquoted, rather than by
     * calling `toString()` on the boxed value. That matters: `toString()` on a Double
     * gives `2.0` where the serializer gives `2` (B37's integral-Long path) and gives
     * `10.50` rather than `10.5` for an exact decimal (B35). Re-implementing numeric
     * rendering here would silently disagree with every other output path.
     *
     * Anything else — an object, or an array containing structure — falls back to the
     * serialized form untouched. Flattening those would invent a format, and quietly
     * emitting something lossy is worse than emitting JSON the caller can parse.
     */
    private fun renderRaw(result: UDM, serialized: String): String {
        val json = JSONSerializer(prettyPrint = false)
        fun scalarText(u: UDM): String? {
            if (u !is UDM.Scalar) return null
            val v = u.value
            // A String is returned verbatim. Serializing it to JSON and un-escaping again
            // would be a lossy round-trip: sequential replaces cannot undo JSON escaping
            // correctly, because unescaping `\\` first leaves a backslash that then pairs
            // with the following character (`a\tb` came back as `a<TAB>b`).
            if (v is String) return v
            // Everything else goes through the serializer so numbers match every other
            // output path: `2` not `2.0` (B37) and `10.50` not `10.5` (B35).
            return json.serialize(u).trim()
        }
        scalarText(result)?.let { return it }
        if (result is UDM.Array) {
            val parts = result.elements.map { scalarText(it) ?: return serialized }
            return parts.joinToString("\n")
        }
        return serialized
    }


    /** F27: escape a `--arg` value for interpolation into a UTL-X double-quoted string literal. */
    private fun escapeUtlxString(value: String): String =
        value.replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\t", "\\t")
            .replace("\r", "\\r")

}
