// modules/analysis/src/main/kotlin/org/apache/utlx/analysis/graph/ASTGraphConverter.kt
package org.apache.utlx.analysis.graph

import org.apache.utlx.core.ast.*

/**
 * Converts UTL-X AST to graph representations
 *
 * Supports multiple graph types:
 * - Data flow graphs: Show how data flows through transformations
 * - Control flow graphs: Show execution paths
 * - Dependency graphs: Show variable and function dependencies
 */
class ASTGraphConverter {

    private var nodeCounter = 0

    /**
     * Generate a unique node ID
     */
    private fun nextNodeId(): String = "n${nodeCounter++}"

    /**
     * Convert a Program to a data flow graph
     */
    fun toDataFlowGraph(program: Program): DirectedGraph {
        nodeCounter = 0
        val builder = GraphBuilder("data_flow", GraphType.DATA_FLOW)

        // Create input node
        val inputNode = builder.node(
            id = "input",
            label = "Input",
            type = NodeType.INPUT,
            metadata = mapOf(
                "inputs" to program.header.inputs.map { it.first to it.second.type.name }
            )
        )

        // Convert body expression
        val bodyNode = convertExpressionToDataFlow(program.body, builder)

        // Connect input to body
        if (bodyNode != null) {
            builder.edge(inputNode, bodyNode, type = EdgeType.DATA_FLOW)
        }

        // Create output node
        val outputNode = builder.node(
            id = "output",
            label = "Output",
            type = NodeType.OUTPUT,
            metadata = mapOf(
                "outputs" to program.header.outputs.map { it.first to it.second.type.name }
            )
        )

        // Connect body to output
        if (bodyNode != null) {
            builder.edge(bodyNode, outputNode, type = EdgeType.DATA_FLOW)
        }

        return builder.build()
    }

    /**
     * Convert an expression to data flow nodes
     */
    private fun convertExpressionToDataFlow(
        expr: Expression,
        builder: GraphBuilder
    ): GraphNode? {
        return when (expr) {
            is Expression.NumberLiteral -> {
                builder.node(
                    id = nextNodeId(),
                    label = expr.value.toString(),
                    type = NodeType.LITERAL,
                    metadata = mapOf("value" to expr.value, "type" to "number")
                )
            }

            is Expression.StringLiteral -> {
                builder.node(
                    id = nextNodeId(),
                    label = "\"${expr.value}\"",
                    type = NodeType.LITERAL,
                    metadata = mapOf("value" to expr.value, "type" to "string")
                )
            }

            is Expression.BooleanLiteral -> {
                builder.node(
                    id = nextNodeId(),
                    label = expr.value.toString(),
                    type = NodeType.LITERAL,
                    metadata = mapOf("value" to expr.value, "type" to "boolean")
                )
            }

            is Expression.NullLiteral -> {
                builder.node(
                    id = nextNodeId(),
                    label = "null",
                    type = NodeType.LITERAL,
                    metadata = mapOf("type" to "null")
                )
            }

            is Expression.Identifier -> {
                builder.node(
                    id = nextNodeId(),
                    label = expr.name,
                    type = NodeType.VARIABLE,
                    metadata = mapOf("name" to expr.name)
                )
            }

            is Expression.MemberAccess -> {
                val objectNode = convertExpressionToDataFlow(expr.target, builder)
                val propertyNode = builder.node(
                    id = nextNodeId(),
                    label = ".${expr.property}",
                    type = NodeType.PROPERTY_ACCESS,
                    metadata = mapOf("property" to expr.property)
                )
                if (objectNode != null) {
                    builder.edge(objectNode, propertyNode, type = EdgeType.DATA_FLOW)
                }
                propertyNode
            }

            is Expression.IndexAccess -> {
                val arrayNode = convertExpressionToDataFlow(expr.target, builder)
                val indexNode = convertExpressionToDataFlow(expr.index, builder)
                val accessNode = builder.node(
                    id = nextNodeId(),
                    label = "[index]",
                    type = NodeType.ARRAY_ACCESS
                )
                if (arrayNode != null) {
                    builder.edge(arrayNode, accessNode, label = "array", type = EdgeType.DATA_FLOW)
                }
                if (indexNode != null) {
                    builder.edge(indexNode, accessNode, label = "index", type = EdgeType.DATA_FLOW)
                }
                accessNode
            }

            is Expression.FunctionCall -> {
                val funcName = when (val func = expr.function) {
                    is Expression.Identifier -> func.name
                    else -> "function"
                }
                val functionNode = builder.node(
                    id = nextNodeId(),
                    label = funcName,
                    type = NodeType.FUNCTION_CALL,
                    metadata = mapOf(
                        "function" to funcName,
                        "argCount" to expr.arguments.size
                    )
                )

                // Connect arguments to function
                expr.arguments.forEachIndexed { index, arg ->
                    val argNode = convertExpressionToDataFlow(arg, builder)
                    if (argNode != null) {
                        builder.edge(argNode, functionNode, label = "arg$index", type = EdgeType.DATA_FLOW)
                    }
                }

                functionNode
            }

            is Expression.BinaryOp -> {
                val leftNode = convertExpressionToDataFlow(expr.left, builder)
                val rightNode = convertExpressionToDataFlow(expr.right, builder)
                val opNode = builder.node(
                    id = nextNodeId(),
                    label = expr.operator.toString(),
                    type = NodeType.BINARY_OP,
                    metadata = mapOf("operator" to expr.operator.toString())
                )

                if (leftNode != null) {
                    builder.edge(leftNode, opNode, label = "left", type = EdgeType.DATA_FLOW)
                }
                if (rightNode != null) {
                    builder.edge(rightNode, opNode, label = "right", type = EdgeType.DATA_FLOW)
                }

                opNode
            }

            is Expression.UnaryOp -> {
                val operandNode = convertExpressionToDataFlow(expr.operand, builder)
                val opNode = builder.node(
                    id = nextNodeId(),
                    label = expr.operator.toString(),
                    type = NodeType.UNARY_OP,
                    metadata = mapOf("operator" to expr.operator.toString())
                )

                if (operandNode != null) {
                    builder.edge(operandNode, opNode, type = EdgeType.DATA_FLOW)
                }

                opNode
            }

            is Expression.ObjectLiteral -> {
                val objectNode = builder.node(
                    id = nextNodeId(),
                    label = "Object",
                    type = NodeType.OBJECT,
                    metadata = mapOf("propertyCount" to expr.properties.size)
                )

                // Process let bindings first
                val bindings = mutableMapOf<String, GraphNode>()
                expr.letBindings.forEach { binding ->
                    val valueNode = convertExpressionToDataFlow(binding.value, builder)
                    if (valueNode != null) {
                        val bindingNode = builder.node(
                            id = nextNodeId(),
                            label = "let ${binding.name}",
                            type = NodeType.LET_BINDING,
                            metadata = mapOf("name" to binding.name)
                        )
                        builder.edge(valueNode, bindingNode, type = EdgeType.DEFINITION)
                        bindings[binding.name] = bindingNode
                    }
                }

                // Process properties
                expr.properties.forEach { property ->
                    val key = property.key ?: "...spread"
                    val propertyNode = builder.node(
                        id = nextNodeId(),
                        label = key,
                        type = NodeType.PROPERTY,
                        metadata = mapOf("key" to key, "isSpread" to property.isSpread)
                    )

                    val valueNode = convertExpressionToDataFlow(property.value, builder)
                    if (valueNode != null) {
                        builder.edge(valueNode, propertyNode, type = EdgeType.DATA_FLOW)
                    }

                    builder.edge(propertyNode, objectNode, type = EdgeType.PARENT_CHILD)
                }

                objectNode
            }

            is Expression.ArrayLiteral -> {
                val arrayNode = builder.node(
                    id = nextNodeId(),
                    label = "Array[${expr.elements.size}]",
                    type = NodeType.ARRAY,
                    metadata = mapOf("size" to expr.elements.size)
                )

                expr.elements.forEachIndexed { index, element ->
                    val elementNode = convertExpressionToDataFlow(element, builder)
                    if (elementNode != null) {
                        builder.edge(elementNode, arrayNode, label = "[$index]", type = EdgeType.DATA_FLOW)
                    }
                }

                arrayNode
            }

            is Expression.Conditional -> {
                val conditionNode = convertExpressionToDataFlow(expr.condition, builder)
                val ifNode = builder.node(
                    id = nextNodeId(),
                    label = "if",
                    type = NodeType.IF_CONDITION
                )

                if (conditionNode != null) {
                    builder.edge(conditionNode, ifNode, label = "condition", type = EdgeType.CONTROL_FLOW)
                }

                val thenNode = convertExpressionToDataFlow(expr.thenBranch, builder)
                if (thenNode != null) {
                    builder.edge(ifNode, thenNode, label = "then", type = EdgeType.CONTROL_FLOW)
                }

                val elseNode = expr.elseBranch?.let { convertExpressionToDataFlow(it, builder) }
                if (elseNode != null) {
                    builder.edge(ifNode, elseNode, label = "else", type = EdgeType.CONTROL_FLOW)
                }

                // Create merge node
                val mergeNode = builder.node(
                    id = nextNodeId(),
                    label = "merge",
                    type = NodeType.MERGE
                )

                if (thenNode != null) {
                    builder.edge(thenNode, mergeNode, type = EdgeType.CONTROL_FLOW)
                }
                if (elseNode != null) {
                    builder.edge(elseNode, mergeNode, type = EdgeType.CONTROL_FLOW)
                }

                mergeNode
            }

            is Expression.Lambda -> {
                val params = expr.parameters.joinToString(", ") { it.name }
                val lambdaNode = builder.node(
                    id = nextNodeId(),
                    label = "λ($params)",
                    type = NodeType.LAMBDA,
                    metadata = mapOf(
                        "parameters" to params,
                        "paramCount" to expr.parameters.size,
                        "hasReturnType" to (expr.returnType != null)
                    )
                )

                val bodyNode = convertExpressionToDataFlow(expr.body, builder)
                if (bodyNode != null) {
                    builder.edge(lambdaNode, bodyNode, type = EdgeType.DATA_FLOW)
                }

                lambdaNode
            }

            else -> {
                // For unhandled expression types, create a generic node
                builder.node(
                    id = nextNodeId(),
                    label = expr::class.simpleName ?: "Expression",
                    type = NodeType.TRANSFORMATION,
                    metadata = mapOf("type" to (expr::class.simpleName ?: "Unknown"))
                )
            }
        }
    }

    /**
     * Convert a Program to a dependency graph
     */
    fun toDependencyGraph(program: Program): DirectedGraph {
        nodeCounter = 0
        val builder = GraphBuilder("dependency", GraphType.DEPENDENCY)

        val dependencies = mutableMapOf<String, MutableSet<String>>()
        val variables = mutableSetOf<String>()

        // Collect variables and dependencies
        collectDependencies(program.body, dependencies, variables)

        // Create nodes for each variable
        val nodeMap = mutableMapOf<String, GraphNode>()
        variables.forEach { varName ->
            nodeMap[varName] = builder.node(
                id = "var_$varName",
                label = varName,
                type = NodeType.VARIABLE,
                metadata = mapOf("name" to varName)
            )
        }

        // Create edges for dependencies
        dependencies.forEach { (target, sources) ->
            val targetNode = nodeMap[target]
            if (targetNode != null) {
                sources.forEach { source ->
                    val sourceNode = nodeMap[source]
                    if (sourceNode != null) {
                        builder.edge(sourceNode, targetNode, type = EdgeType.DEPENDENCY)
                    }
                }
            }
        }

        return builder.build()
    }

    /**
     * Collect variable dependencies from an expression
     */
    private fun collectDependencies(
        expr: Expression,
        dependencies: MutableMap<String, MutableSet<String>>,
        variables: MutableSet<String>,
        currentBinding: String? = null
    ) {
        when (expr) {
            is Expression.Identifier -> {
                variables.add(expr.name)
                if (currentBinding != null) {
                    dependencies.computeIfAbsent(currentBinding) { mutableSetOf() }.add(expr.name)
                }
            }

            is Expression.ObjectLiteral -> {
                expr.letBindings.forEach { binding ->
                    variables.add(binding.name)
                    collectDependencies(binding.value, dependencies, variables, binding.name)
                }
                expr.properties.forEach { property ->
                    collectDependencies(property.value, dependencies, variables, currentBinding)
                }
            }

            is Expression.MemberAccess -> {
                collectDependencies(expr.target, dependencies, variables, currentBinding)
            }

            is Expression.IndexAccess -> {
                collectDependencies(expr.target, dependencies, variables, currentBinding)
                collectDependencies(expr.index, dependencies, variables, currentBinding)
            }

            is Expression.FunctionCall -> {
                expr.arguments.forEach { arg ->
                    collectDependencies(arg, dependencies, variables, currentBinding)
                }
            }

            is Expression.BinaryOp -> {
                collectDependencies(expr.left, dependencies, variables, currentBinding)
                collectDependencies(expr.right, dependencies, variables, currentBinding)
            }

            is Expression.UnaryOp -> {
                collectDependencies(expr.operand, dependencies, variables, currentBinding)
            }

            is Expression.ArrayLiteral -> {
                expr.elements.forEach { element ->
                    collectDependencies(element, dependencies, variables, currentBinding)
                }
            }

            is Expression.Conditional -> {
                collectDependencies(expr.condition, dependencies, variables, currentBinding)
                collectDependencies(expr.thenBranch, dependencies, variables, currentBinding)
                expr.elseBranch?.let { collectDependencies(it, dependencies, variables, currentBinding) }
            }

            is Expression.Lambda -> {
                expr.parameters.forEach { param ->
                    variables.add(param.name)
                }
                collectDependencies(expr.body, dependencies, variables, currentBinding)
            }

            else -> {
                // Nothing to collect for literals
            }
        }
    }
}
