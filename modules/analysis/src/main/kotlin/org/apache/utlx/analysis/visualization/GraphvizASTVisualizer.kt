// modules/analysis/src/main/kotlin/org/apache/utlx/analysis/visualization/GraphvizASTVisualizer.kt
package org.apache.utlx.analysis.visualization

import org.apache.utlx.core.ast.*

/**
 * Converts UTL-X AST to Graphviz DOT format for visualization
 *
 * Generates a visual representation of the transformation's Abstract Syntax Tree
 * that can be rendered to SVG, PNG, or other formats using Graphviz.
 *
 * Usage:
 * ```
 * val visualizer = GraphvizASTVisualizer()
 * val dotGraph = visualizer.visualize(program)
 * // Save to file and run: dot -Tsvg ast.dot -o ast.svg
 * ```
 */
class GraphvizASTVisualizer {

    private var nodeCounter = 0
    private val edges = mutableListOf<String>()
    private val nodes = mutableListOf<String>()

    /**
     * Generate Graphviz DOT representation of an AST
     */
    fun visualize(program: Program, options: VisualizationOptions = VisualizationOptions()): String {
        nodeCounter = 0
        edges.clear()
        nodes.clear()

        visitProgram(program, options)

        return buildString {
            appendLine("digraph AST {")
            appendLine("  rankdir=${options.layout};")
            appendLine("  node [fontname=\"${options.fontName}\", fontsize=${options.fontSize}];")
            appendLine("  edge [fontname=\"${options.fontName}\", fontsize=${options.fontSize - 2}];")
            appendLine()

            // Output all nodes
            nodes.forEach { appendLine(it) }
            appendLine()

            // Output all edges
            edges.forEach { appendLine(it) }

            appendLine("}")
        }
    }

    private fun visitProgram(program: Program, options: VisualizationOptions): String {
        val nodeId = nextId()

        nodes.add(formatNode(
            nodeId,
            "Program",
            "shape=box, style=filled, fillcolor=lightblue, fontsize=${options.fontSize + 2}, penwidth=2"
        ))

        // Header
        val headerId = visitHeader(program.header, options)
        edges.add(formatEdge(nodeId, headerId, "header"))

        // Body
        val bodyId = visitExpression(program.body, options)
        edges.add(formatEdge(nodeId, bodyId, "body"))

        return nodeId
    }

    private fun visitHeader(header: Header, options: VisualizationOptions): String {
        val nodeId = nextId()

        val inputsDesc = header.inputs.joinToString(", ") { "${it.first}: ${it.second.type}" }
        val outputsDesc = header.outputs.joinToString(", ") { "${it.first}: ${it.second.type}" }

        nodes.add(formatNode(
            nodeId,
            "Header\\nv${header.version}\\nin: $inputsDesc\\nout: $outputsDesc",
            "shape=note, style=filled, fillcolor=lightyellow"
        ))

        return nodeId
    }

    private fun visitExpression(expr: Expression, options: VisualizationOptions): String {
        return when (expr) {
            is Expression.ObjectLiteral -> visitObjectLiteral(expr, options)
            is Expression.ArrayLiteral -> visitArrayLiteral(expr, options)
            is Expression.StringLiteral -> visitStringLiteral(expr, options)
            is Expression.NumberLiteral -> visitNumberLiteral(expr, options)
            is Expression.BooleanLiteral -> visitBooleanLiteral(expr, options)
            is Expression.NullLiteral -> visitNullLiteral(expr, options)
            is Expression.Identifier -> visitIdentifier(expr, options)
            is Expression.MemberAccess -> visitMemberAccess(expr, options)
            is Expression.SafeNavigation -> visitSafeNavigation(expr, options)
            is Expression.IndexAccess -> visitIndexAccess(expr, options)
            is Expression.FunctionCall -> visitFunctionCall(expr, options)
            is Expression.BinaryOp -> visitBinaryOp(expr, options)
            is Expression.UnaryOp -> visitUnaryOp(expr, options)
            is Expression.Ternary -> visitTernary(expr, options)
            is Expression.Conditional -> visitConditional(expr, options)
            is Expression.TryCatch -> visitTryCatch(expr, options)
            is Expression.LetBinding -> visitLetBinding(expr, options)
            is Expression.Lambda -> visitLambda(expr, options)
            is Expression.Pipe -> visitPipe(expr, options)
            is Expression.Match -> visitMatch(expr, options)
            is Expression.TemplateApplication -> visitTemplateApplication(expr, options)
            is Expression.SpreadElement -> visitSpreadElement(expr, options)
            is Expression.Block -> visitBlock(expr, options)
        }
    }

    private fun visitObjectLiteral(obj: Expression.ObjectLiteral, options: VisualizationOptions): String {
        val nodeId = nextId()
        val propCount = obj.properties.size
        val letCount = obj.letBindings.size

        nodes.add(formatNode(
            nodeId,
            "Object\\n{$propCount props${if (letCount > 0) ", $letCount lets" else ""}}",
            "shape=box, style=\"filled,rounded\", fillcolor=lightgreen"
        ))

        // Let bindings
        obj.letBindings.forEachIndexed { index, let ->
            val letId = visitLetBinding(let, options)
            edges.add(formatEdge(nodeId, letId, "let[$index]"))
        }

        // Properties
        obj.properties.forEachIndexed { index, prop ->
            if (prop.isSpread) {
                val valueId = visitExpression(prop.value, options)
                edges.add(formatEdge(nodeId, valueId, "...spread[$index]"))
            } else {
                val valueId = visitExpression(prop.value, options)
                val label = if (prop.isAttribute) "@${prop.key}" else prop.key ?: "?"
                edges.add(formatEdge(nodeId, valueId, label))
            }
        }

        return nodeId
    }

    private fun visitArrayLiteral(arr: Expression.ArrayLiteral, options: VisualizationOptions): String {
        val nodeId = nextId()

        nodes.add(formatNode(
            nodeId,
            "Array\\n[${arr.elements.size}]",
            "shape=box, style=filled, fillcolor=palegreen"
        ))

        arr.elements.forEachIndexed { index, elem ->
            val elemId = visitExpression(elem, options)
            edges.add(formatEdge(nodeId, elemId, "[$index]"))
        }

        return nodeId
    }

    private fun visitStringLiteral(str: Expression.StringLiteral, options: VisualizationOptions): String {
        val nodeId = nextId()
        val truncated = if (str.value.length > 30) str.value.take(27) + "..." else str.value
        val escaped = truncated.replace("\"", "\\\"").replace("\n", "\\n")

        nodes.add(formatNode(
            nodeId,
            "\"$escaped\"",
            "shape=oval, style=filled, fillcolor=wheat"
        ))

        return nodeId
    }

    private fun visitNumberLiteral(num: Expression.NumberLiteral, options: VisualizationOptions): String {
        val nodeId = nextId()

        nodes.add(formatNode(
            nodeId,
            num.value.toString(),
            "shape=oval, style=filled, fillcolor=wheat"
        ))

        return nodeId
    }

    private fun visitBooleanLiteral(bool: Expression.BooleanLiteral, options: VisualizationOptions): String {
        val nodeId = nextId()

        nodes.add(formatNode(
            nodeId,
            bool.value.toString(),
            "shape=oval, style=filled, fillcolor=wheat"
        ))

        return nodeId
    }

    private fun visitNullLiteral(expr: Expression.NullLiteral, options: VisualizationOptions): String {
        val nodeId = nextId()

        nodes.add(formatNode(
            nodeId,
            "null",
            "shape=oval, style=filled, fillcolor=wheat"
        ))

        return nodeId
    }

    private fun visitIdentifier(id: Expression.Identifier, options: VisualizationOptions): String {
        val nodeId = nextId()

        nodes.add(formatNode(
            nodeId,
            id.name,
            "shape=ellipse, style=filled, fillcolor=lightcyan"
        ))

        return nodeId
    }

    private fun visitMemberAccess(member: Expression.MemberAccess, options: VisualizationOptions): String {
        val nodeId = nextId()

        val prefix = when {
            member.isAttribute -> "@"
            member.isMetadata -> "^"
            else -> ""
        }

        nodes.add(formatNode(
            nodeId,
            ".$prefix${member.property}",
            "shape=diamond, style=filled, fillcolor=lightpink"
        ))

        val targetId = visitExpression(member.target, options)
        edges.add(formatEdge(nodeId, targetId, "target"))

        return nodeId
    }

    private fun visitSafeNavigation(safe: Expression.SafeNavigation, options: VisualizationOptions): String {
        val nodeId = nextId()

        nodes.add(formatNode(
            nodeId,
            "?.${safe.property}",
            "shape=diamond, style=filled, fillcolor=lightpink"
        ))

        val targetId = visitExpression(safe.target, options)
        edges.add(formatEdge(nodeId, targetId, "target"))

        return nodeId
    }

    private fun visitIndexAccess(idx: Expression.IndexAccess, options: VisualizationOptions): String {
        val nodeId = nextId()

        nodes.add(formatNode(
            nodeId,
            "[index]",
            "shape=diamond, style=filled, fillcolor=lightpink"
        ))

        val targetId = visitExpression(idx.target, options)
        val indexId = visitExpression(idx.index, options)
        edges.add(formatEdge(nodeId, targetId, "target"))
        edges.add(formatEdge(nodeId, indexId, "index"))

        return nodeId
    }

    private fun visitFunctionCall(call: Expression.FunctionCall, options: VisualizationOptions): String {
        val nodeId = nextId()

        nodes.add(formatNode(
            nodeId,
            "call(${call.arguments.size})",
            "shape=box, style=\"filled,rounded\", fillcolor=plum"
        ))

        val funcId = visitExpression(call.function, options)
        edges.add(formatEdge(nodeId, funcId, "function"))

        call.arguments.forEachIndexed { index, arg ->
            val argId = visitExpression(arg, options)
            edges.add(formatEdge(nodeId, argId, "arg[$index]"))
        }

        return nodeId
    }

    private fun visitBinaryOp(op: Expression.BinaryOp, options: VisualizationOptions): String {
        val nodeId = nextId()

        val opSymbol = when (op.operator) {
            BinaryOperator.PLUS -> "+"
            BinaryOperator.MINUS -> "-"
            BinaryOperator.MULTIPLY -> "*"
            BinaryOperator.DIVIDE -> "/"
            BinaryOperator.MODULO -> "%"
            BinaryOperator.EXPONENT -> "**"
            BinaryOperator.EQUAL -> "=="
            BinaryOperator.NOT_EQUAL -> "!="
            BinaryOperator.LESS_THAN -> "<"
            BinaryOperator.LESS_EQUAL -> "<="
            BinaryOperator.GREATER_THAN -> ">"
            BinaryOperator.GREATER_EQUAL -> ">="
            BinaryOperator.AND -> "&&"
            BinaryOperator.OR -> "||"
            BinaryOperator.NULLISH_COALESCE -> "??"
        }

        nodes.add(formatNode(
            nodeId,
            opSymbol,
            "shape=circle, style=filled, fillcolor=orange"
        ))

        val leftId = visitExpression(op.left, options)
        val rightId = visitExpression(op.right, options)
        edges.add(formatEdge(nodeId, leftId, "left"))
        edges.add(formatEdge(nodeId, rightId, "right"))

        return nodeId
    }

    private fun visitUnaryOp(op: Expression.UnaryOp, options: VisualizationOptions): String {
        val nodeId = nextId()

        val opSymbol = when (op.operator) {
            UnaryOperator.MINUS -> "-"
            UnaryOperator.NOT -> "!"
        }

        nodes.add(formatNode(
            nodeId,
            opSymbol,
            "shape=circle, style=filled, fillcolor=orange"
        ))

        val operandId = visitExpression(op.operand, options)
        edges.add(formatEdge(nodeId, operandId, "operand"))

        return nodeId
    }

    private fun visitTernary(tern: Expression.Ternary, options: VisualizationOptions): String {
        val nodeId = nextId()

        nodes.add(formatNode(
            nodeId,
            "? :",
            "shape=diamond, style=filled, fillcolor=khaki"
        ))

        val condId = visitExpression(tern.condition, options)
        val thenId = visitExpression(tern.thenExpr, options)
        val elseId = visitExpression(tern.elseExpr, options)

        edges.add(formatEdge(nodeId, condId, "condition"))
        edges.add(formatEdge(nodeId, thenId, "then"))
        edges.add(formatEdge(nodeId, elseId, "else"))

        return nodeId
    }

    private fun visitConditional(cond: Expression.Conditional, options: VisualizationOptions): String {
        val nodeId = nextId()

        nodes.add(formatNode(
            nodeId,
            "if-else",
            "shape=diamond, style=filled, fillcolor=khaki"
        ))

        val conditionId = visitExpression(cond.condition, options)
        val thenId = visitExpression(cond.thenBranch, options)

        edges.add(formatEdge(nodeId, conditionId, "condition"))
        edges.add(formatEdge(nodeId, thenId, "then"))

        val elseBranch = cond.elseBranch
        if (elseBranch != null) {
            val elseId = visitExpression(elseBranch, options)
            edges.add(formatEdge(nodeId, elseId, "else"))
        }

        return nodeId
    }

    private fun visitTryCatch(tryCatch: Expression.TryCatch, options: VisualizationOptions): String {
        val nodeId = nextId()

        val errorVar = tryCatch.errorVariable ?: "_"
        nodes.add(formatNode(
            nodeId,
            "try-catch($errorVar)",
            "shape=hexagon, style=filled, fillcolor=lightcoral"
        ))

        val tryId = visitExpression(tryCatch.tryBlock, options)
        val catchId = visitExpression(tryCatch.catchBlock, options)

        edges.add(formatEdge(nodeId, tryId, "try"))
        edges.add(formatEdge(nodeId, catchId, "catch"))

        return nodeId
    }

    private fun visitLetBinding(let: Expression.LetBinding, options: VisualizationOptions): String {
        val nodeId = nextId()

        val typeAnnotation = let.typeAnnotation?.let { ": ${formatType(it)}" } ?: ""
        nodes.add(formatNode(
            nodeId,
            "let ${let.name}$typeAnnotation",
            "shape=box, style=\"filled,dashed\", fillcolor=lavender"
        ))

        val valueId = visitExpression(let.value, options)
        edges.add(formatEdge(nodeId, valueId, "value"))

        return nodeId
    }

    private fun visitLambda(lambda: Expression.Lambda, options: VisualizationOptions): String {
        val nodeId = nextId()

        val params = lambda.parameters.joinToString(", ") {
            val type = it.type?.let { t -> ": ${formatType(t)}" } ?: ""
            "${it.name}$type"
        }
        val returnType = lambda.returnType?.let { " -> ${formatType(it)}" } ?: ""

        nodes.add(formatNode(
            nodeId,
            "λ($params)$returnType",
            "shape=box, style=\"filled,rounded\", fillcolor=thistle"
        ))

        val bodyId = visitExpression(lambda.body, options)
        edges.add(formatEdge(nodeId, bodyId, "body"))

        return nodeId
    }

    private fun visitPipe(pipe: Expression.Pipe, options: VisualizationOptions): String {
        val nodeId = nextId()

        nodes.add(formatNode(
            nodeId,
            "|>",
            "shape=parallelogram, style=filled, fillcolor=skyblue"
        ))

        val sourceId = visitExpression(pipe.source, options)
        val targetId = visitExpression(pipe.target, options)

        edges.add(formatEdge(nodeId, sourceId, "source"))
        edges.add(formatEdge(nodeId, targetId, "target"))

        return nodeId
    }

    private fun visitMatch(match: Expression.Match, options: VisualizationOptions): String {
        val nodeId = nextId()

        nodes.add(formatNode(
            nodeId,
            "match(${match.cases.size} cases)",
            "shape=hexagon, style=filled, fillcolor=gold"
        ))

        val valueId = visitExpression(match.value, options)
        edges.add(formatEdge(nodeId, valueId, "value"))

        match.cases.forEachIndexed { index, case ->
            val caseId = visitMatchCase(case, options)
            edges.add(formatEdge(nodeId, caseId, "case[$index]"))
        }

        return nodeId
    }

    private fun visitMatchCase(case: MatchCase, options: VisualizationOptions): String {
        val nodeId = nextId()

        val patternDesc = when (val p = case.pattern) {
            is Pattern.Literal -> "= ${p.value}"
            is Pattern.Variable -> "var ${p.name}"
            is Pattern.Wildcard -> "_"
        }

        nodes.add(formatNode(
            nodeId,
            "case $patternDesc",
            "shape=box, style=filled, fillcolor=lightyellow"
        ))

        val guard = case.guard
        if (guard != null) {
            val guardId = visitExpression(guard, options)
            edges.add(formatEdge(nodeId, guardId, "guard"))
        }

        val exprId = visitExpression(case.expression, options)
        edges.add(formatEdge(nodeId, exprId, "expr"))

        return nodeId
    }

    private fun visitTemplateApplication(template: Expression.TemplateApplication, options: VisualizationOptions): String {
        val nodeId = nextId()

        nodes.add(formatNode(
            nodeId,
            "apply",
            "shape=box, style=filled, fillcolor=peachpuff"
        ))

        val selectorId = visitExpression(template.selector, options)
        edges.add(formatEdge(nodeId, selectorId, "selector"))

        return nodeId
    }

    private fun visitSpreadElement(spread: Expression.SpreadElement, options: VisualizationOptions): String {
        val nodeId = nextId()

        nodes.add(formatNode(
            nodeId,
            "...spread",
            "shape=box, style=filled, fillcolor=lightgray"
        ))

        val exprId = visitExpression(spread.expression, options)
        edges.add(formatEdge(nodeId, exprId, "expr"))

        return nodeId
    }

    private fun visitBlock(block: Expression.Block, options: VisualizationOptions): String {
        val nodeId = nextId()

        nodes.add(formatNode(
            nodeId,
            "Block(${block.expressions.size})",
            "shape=box, style=\"filled,rounded\", fillcolor=lightgray"
        ))

        block.expressions.forEachIndexed { index, expr ->
            val exprId = visitExpression(expr, options)
            edges.add(formatEdge(nodeId, exprId, "[$index]"))
        }

        return nodeId
    }

    // Helpers

    private fun nextId(): String = "node${nodeCounter++}"

    private fun formatNode(id: String, label: String, attributes: String): String {
        val escapedLabel = label.replace("\"", "\\\"")
        return "  $id [label=\"$escapedLabel\", $attributes];"
    }

    private fun formatEdge(from: String, to: String, label: String?): String {
        val labelAttr = if (label != null) " [label=\"$label\"]" else ""
        return "  $from -> $to$labelAttr;"
    }

    private fun formatType(type: Type): String = when (type) {
        is Type.String -> "string"
        is Type.Number -> "number"
        is Type.Boolean -> "boolean"
        is Type.Null -> "null"
        is Type.Date -> "date"
        is Type.Any -> "any"
        is Type.Array -> "[${formatType(type.elementType)}]"
        is Type.Object -> "{...}"
        is Type.Function -> "fn"
        is Type.Union -> type.types.joinToString("|") { formatType(it) }
        is Type.Nullable -> "${formatType(type.innerType)}?"
    }
}

/**
 * Configuration options for AST visualization
 */
data class VisualizationOptions(
    /**
     * Graph layout direction: TB (top-bottom), LR (left-right), BT (bottom-top), RL (right-left)
     */
    val layout: String = "TB",

    /**
     * Font name for nodes and edges
     */
    val fontName: String = "Arial",

    /**
     * Base font size for nodes
     */
    val fontSize: Int = 12,

    /**
     * Include location information in nodes
     */
    val showLocations: Boolean = false,

    /**
     * Compact mode (fewer details)
     */
    val compact: Boolean = false
)
