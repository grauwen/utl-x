// modules/daemon/src/main/kotlin/org/apache/utlx/daemon/schema/SchemaTypeContextFactory.kt
package org.apache.utlx.daemon.schema

import org.apache.utlx.analysis.types.TypeContext
import org.apache.utlx.analysis.types.TypeContextBuilder
import org.apache.utlx.analysis.types.TypeDefinition
import org.slf4j.LoggerFactory

/**
 * Schema Type Context Factory
 *
 * Creates TypeContext instances from TypeDefinition objects.
 *
 * This bridges the gap between schema parsers (which return TypeDefinition)
 * and the LSP daemon's state management (which stores TypeContext).
 *
 * Design-Time Mode Flow:
 * 1. Schema file (XSD/JSON Schema) loaded via utlx/loadSchema
 * 2. SchemaParser parses → TypeDefinition
 * 3. SchemaTypeContextFactory creates → TypeContext
 * 4. StateManager stores TypeContext for completion/hover/diagnostics
 */
class SchemaTypeContextFactory {

    private val logger = LoggerFactory.getLogger(SchemaTypeContextFactory::class.java)

    /**
     * Create a TypeContext from a schema TypeDefinition
     *
     * The TypeContext will contain:
     * - An "input" binding with the schema type
     * - All standard library functions
     * - Standard type registry
     *
     * @param typeDef Type definition from schema parser
     * @param inputBinding Name of the input variable (default: "input")
     * @return TypeContext ready for use in completion/hover/diagnostics
     */
    fun createFromSchema(
        typeDef: TypeDefinition,
        inputBinding: String = "input"
    ): TypeContext {
        logger.debug("Creating TypeContext from schema type: ${typeDef::class.simpleName}")

        // Create standard context with the input type
        val context = TypeContextBuilder.standard(typeDef)

        logger.debug("Created TypeContext with input binding '$inputBinding'")

        return context
    }

    /**
     * Create a TypeContext from multiple schema types
     *
     * Useful when a transformation has multiple inputs or when
     * combining schemas from different sources.
     *
     * @param types Map of variable names to their types
     * @return TypeContext with all bindings
     */
    fun createFromSchemas(
        types: Map<String, TypeDefinition>
    ): TypeContext {
        logger.debug("Creating TypeContext from ${types.size} schema types")

        // Start with the first type as the primary input
        val primaryType = types["input"] ?: types.values.firstOrNull() ?: TypeDefinition.Any
        val context = TypeContextBuilder.standard(primaryType)

        // Note: For now, we only support a single "input" binding
        // Future enhancement: Support multiple inputs
        if (types.size > 1) {
            logger.warn("Multiple schema types provided, but only 'input' is supported. " +
                       "Additional types will be ignored.")
        }

        return context
    }
}
