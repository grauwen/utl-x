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
        val declaredInputs = compiledProgram.header.inputs
        val inputs: Map<String, TransformationService.InputData>

        if (declaredInputs.size <= 1) {
            // Single input: pass payload directly as the named input
            val inputName = transformConfig.inputs.firstOrNull()?.name
                ?: declaredInputs.firstOrNull()?.first
                ?: "input"
            val declaredFormat = declaredInputs.firstOrNull()?.second?.type?.name?.lowercase()
            inputs = mapOf(
                inputName to TransformationService.InputData(content = input, format = declaredFormat)
            )
        } else {
            // Multi-input: payload is a JSON envelope where each key
            // maps to a declared input name. The engine splits the envelope
            // and feeds each part to the transformation as a separate named input.
            val mapper = com.fasterxml.jackson.databind.ObjectMapper()
            val envelope = mapper.readTree(input)

            inputs = declaredInputs.associate { (name, formatSpec) ->
                val node = envelope.get(name)
                    ?: throw IllegalArgumentException(
                        "Envelope missing required input '$name'. " +
                        "Expected keys: ${declaredInputs.map { it.first }}"
                    )
                val declaredFormat = formatSpec.type.name.lowercase()
                // For non-JSON formats, extract raw string; for JSON, serialize the node
                val content = if (declaredFormat != "json" && node.isTextual) {
                    node.asText()
                } else {
                    mapper.writeValueAsString(node)
                }
                name to TransformationService.InputData(
                    content = content,
                    format = declaredFormat
                )
            }
            logger.debug("Envelope split into {} named inputs: {}", inputs.size, inputs.keys)
        }

        // Let exceptions propagate — ValidationOrchestrator handles error classification
        val (output, _) = transformationService.transform(utlxSource, inputs)
        return ExecutionResult(output = output)
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
