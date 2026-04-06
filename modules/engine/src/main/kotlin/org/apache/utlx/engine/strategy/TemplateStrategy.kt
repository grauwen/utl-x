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
import org.slf4j.LoggerFactory

class TemplateStrategy : ExecutionStrategy {

    private val logger = LoggerFactory.getLogger(TemplateStrategy::class.java)
    private val transformationService = TransformationService()
    override val name: String = "TEMPLATE"

    private lateinit var compiledProgram: Program
    private lateinit var utlxSource: String
    private lateinit var transformConfig: TransformConfig

    override fun initialize(source: String, config: TransformConfig) {
        this.utlxSource = source
        this.transformConfig = config

        logger.info("Compiling transformation (TEMPLATE strategy)...")
        compiledProgram = compileSource(source)
        logger.info("Transformation compiled successfully")
    }

    override fun execute(input: String): ExecutionResult {
        return try {
            // Determine the primary input name from transform config or program header
            val inputName = transformConfig.inputs.firstOrNull()?.name
                ?: compiledProgram.header.inputs.firstOrNull()?.first
                ?: "input"

            val inputs = mapOf(
                inputName to TransformationService.InputData(content = input, format = null)
            )

            val (output, _) = transformationService.transform(utlxSource, inputs)

            ExecutionResult(output = output)
        } catch (e: Exception) {
            logger.error("Transformation execution failed: {}", e.message)
            ExecutionResult(
                output = "",
                validationErrors = listOf(
                    ValidationError(message = e.message ?: "Unknown execution error")
                )
            )
        }
    }

    override fun shutdown() {
        logger.info("TEMPLATE strategy shutdown")
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
        val typeResult = typeChecker.check(program)

        when (typeResult) {
            is TypeCheckResult.Success -> {
                logger.debug("Type checking passed: {}", typeResult.type)
            }
            is TypeCheckResult.Failure -> {
                logger.warn("Type checking warnings: {}", typeResult.errors.size)
            }
        }

        return program
    }
}
