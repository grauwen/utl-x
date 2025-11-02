// modules/daemon/src/main/kotlin/org/apache/utlx/daemon/schema/OutputSchemaInferenceService.kt
package org.apache.utlx.daemon.schema

import org.apache.utlx.analysis.schema.JSONSchemaGenerator
import org.apache.utlx.analysis.schema.GeneratorOptions
import org.apache.utlx.analysis.types.AdvancedTypeInference
import org.apache.utlx.analysis.types.TypeDefinition
import org.apache.utlx.daemon.state.StateManager
import org.slf4j.LoggerFactory

/**
 * Output Schema Inference Service
 *
 * Infers output schema (JSON Schema) from UTL-X transformations in design-time mode.
 *
 * Workflow:
 * 1. Get cached AST from StateManager
 * 2. Get input type environment
 * 3. Use AdvancedTypeInference to infer output type
 * 4. Generate JSON Schema from inferred type
 *
 * This enables "schema engineering" - generating schemas from transformations
 * without executing them on actual data.
 */
class OutputSchemaInferenceService(
    private val stateManager: StateManager
) {

    private val logger = LoggerFactory.getLogger(OutputSchemaInferenceService::class.java)
    private val schemaGenerator = JSONSchemaGenerator()

    /**
     * Infer output schema for a document
     *
     * @param uri Document URI
     * @param pretty Whether to pretty-print the schema
     * @param includeComments Whether to include comments in schema
     * @return JSON Schema string, or null if inference fails
     */
    fun inferOutputSchema(
        uri: String,
        pretty: Boolean = true,
        includeComments: Boolean = true
    ): String? {
        logger.debug("Inferring output schema for: $uri")

        // 1. Get cached AST from StateManager
        val ast = stateManager.getAst(uri)
        if (ast == null) {
            logger.warn("No AST cached for $uri. Document may not have been parsed yet.")
            return null
        }

        // 2. Get input type environment
        val typeEnv = stateManager.getTypeEnvironment(uri)
        if (typeEnv == null) {
            logger.warn("No type environment for $uri. Load schema via utlx/loadSchema first.")
            return null
        }

        try {
            // 3. Use AdvancedTypeInference to infer output type
            val typeInference = AdvancedTypeInference()

            // Extract input type from TypeContext
            val inputType = typeEnv.inputType ?: TypeDefinition.Any

            val outputType = typeInference.inferOutputType(ast, inputType)

            logger.debug("Inferred output type: ${outputType::class.simpleName}")

            // 4. Generate JSON Schema from inferred type
            val options = GeneratorOptions(
                pretty = pretty,
                includeComments = includeComments
            )

            val schema = schemaGenerator.generate(outputType, options)

            logger.info("Successfully generated output schema for $uri")

            return schema

        } catch (e: Exception) {
            logger.error("Error inferring output schema for $uri", e)
            return null
        }
    }

    /**
     * Infer output schema with validation
     *
     * Returns a result object with success/failure status and schema or error message.
     *
     * @param uri Document URI
     * @param pretty Whether to pretty-print the schema
     * @param includeComments Whether to include comments in schema
     * @return InferenceResult with schema or error
     */
    fun inferOutputSchemaWithValidation(
        uri: String,
        pretty: Boolean = true,
        includeComments: Boolean = true
    ): InferenceResult {
        // Check if document exists
        val document = stateManager.getDocument(uri)
        if (document == null) {
            return InferenceResult.Failure("Document not found: $uri")
        }

        // Check if AST exists
        val ast = stateManager.getAst(uri)
        if (ast == null) {
            return InferenceResult.Failure(
                "No AST cached for document. The document may not have been parsed yet or contains syntax errors."
            )
        }

        // Check if type environment exists
        val typeEnv = stateManager.getTypeEnvironment(uri)
        if (typeEnv == null) {
            return InferenceResult.Failure(
                "No type environment for document. Load a schema using utlx/loadSchema first."
            )
        }

        // Perform inference
        val schema = inferOutputSchema(uri, pretty, includeComments)
        if (schema == null) {
            return InferenceResult.Failure("Failed to infer output schema. Check logs for details.")
        }

        return InferenceResult.Success(schema)
    }
}

/**
 * Result of output schema inference
 */
sealed class InferenceResult {
    /**
     * Successful inference
     */
    data class Success(val schema: String) : InferenceResult()

    /**
     * Failed inference with error message
     */
    data class Failure(val error: String) : InferenceResult()
}
