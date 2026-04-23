package org.apache.utlx.engine.strategy

import org.apache.utlx.cli.service.TransformationService
import org.apache.utlx.core.ast.Program
import org.apache.utlx.core.interpreter.Interpreter
import org.apache.utlx.core.lexer.Lexer
import org.apache.utlx.core.parser.ParseResult
import org.apache.utlx.core.parser.Parser
import org.apache.utlx.core.types.StandardLibrary
import org.apache.utlx.core.types.TypeCheckResult
import org.apache.utlx.core.types.TypeChecker
import org.apache.utlx.engine.config.TransformConfig
import org.apache.utlx.engine.strategy.compiled.ASTCompiler
import org.apache.utlx.engine.strategy.compiled.TransformFunction
import org.slf4j.LoggerFactory

/**
 * COMPILED Strategy — compiles .utlx expressions to JVM bytecode for direct execution.
 *
 * Init-time:
 *   1. Compile .utlx source → AST (same as TEMPLATE)
 *   2. Compile AST body → JVM bytecode via ASM → TransformFunction class
 *   3. If compilation fails (unsupported AST nodes), fall back to interpreter
 *
 * Runtime (per message):
 *   1. Parse input (same as TEMPLATE)
 *   2. Call TransformFunction.execute(inputs) — direct method invocation, no AST walking
 *   3. Serialize output (same as TEMPLATE)
 *
 * Falls back to TEMPLATE (interpreter) if the expression contains nodes
 * the bytecode compiler doesn't support yet.
 */
class CompiledStrategy : ExecutionStrategy {

    private val logger = LoggerFactory.getLogger(CompiledStrategy::class.java)
    private val transformationService = TransformationService()
    override val name: String = "COMPILED"

    private lateinit var compiledProgram: Program
    private lateinit var utlxSource: String
    private lateinit var transformConfig: TransformConfig

    // The compiled function — null if fallback to interpreter
    private var transformFunction: TransformFunction? = null
    private var usingFallback = false

    override fun initialize(source: String, config: TransformConfig) {
        this.utlxSource = source
        this.transformConfig = config

        logger.info("Compiling transformation (COMPILED strategy)...")
        compiledProgram = compileSource(source)

        // Try to compile AST body to bytecode
        try {
            val compiler = ASTCompiler()
            transformFunction = compiler.compile(compiledProgram)

            if (transformFunction != null) {
                logger.info("Transformation compiled to JVM bytecode — direct execution enabled")
            } else {
                usingFallback = true
                logger.info("Transformation uses unsupported AST nodes — falling back to interpreter")
            }
        } catch (e: Exception) {
            // Bytecode generation or class loading failed — fall back to interpreter
            usingFallback = true
            logger.warn("Bytecode compilation failed ({}), falling back to interpreter", e.message)
        }
    }

    override fun execute(input: String): ExecutionResult {
        val declaredInputs = compiledProgram.header.inputs
        val inputName = transformConfig.inputs.firstOrNull()?.name
            ?: declaredInputs.firstOrNull()?.first
            ?: "input"
        val declaredFormat = declaredInputs.firstOrNull()?.second?.type?.name?.lowercase()

        // Parse input to UDM
        val inputUDM = transformationService.parseInputPublic(input, declaredFormat ?: "json")

        if (transformFunction != null && !usingFallback) {
            // ── Compiled path: direct method invocation ──
            val outputUDM = transformFunction!!.execute(mapOf(inputName to inputUDM))

            val outputFormat = compiledProgram.header.outputFormat.type.name.lowercase()
            val outputData = transformationService.serializeOutputPublic(
                outputUDM, outputFormat, compiledProgram.header.outputFormat, true
            )
            return ExecutionResult(output = outputData)
        } else {
            // ── Fallback: interpreter (same as TEMPLATE) ──
            val inputs = mapOf(
                inputName to TransformationService.InputData(content = input, format = declaredFormat)
            )
            val (output, _) = transformationService.transform(utlxSource, inputs)
            return ExecutionResult(output = output)
        }
    }

    override fun shutdown() {
        transformFunction = null
        logger.info("COMPILED strategy shutdown (fallback={})", usingFallback)
    }

    private fun compileSource(source: String): Program {
        val lexer = Lexer(source)
        val tokens = lexer.tokenize()
        val parser = Parser(tokens, source)
        val parseResult = parser.parse()
        val program = when (parseResult) {
            is ParseResult.Success -> parseResult.program
            is ParseResult.Failure -> {
                val errors = parseResult.errors.joinToString("\n") { "  ${it.message} at ${it.location}" }
                throw IllegalStateException("Parse errors:\n$errors")
            }
        }
        val stdlib = StandardLibrary()
        val typeChecker = TypeChecker(stdlib)
        typeChecker.check(program)
        return program
    }
}
