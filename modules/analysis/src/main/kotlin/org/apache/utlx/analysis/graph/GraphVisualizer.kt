// modules/analysis/src/main/kotlin/org/apache/utlx/analysis/graph/GraphVisualizer.kt
package org.apache.utlx.analysis.graph

/**
 * Graph visualization exporters
 *
 * Supports multiple formats:
 * - DOT (Graphviz) for rendering with dot, neato, etc.
 * - Mermaid for embedding in Markdown and web pages
 * - PlantUML for UML-style diagrams
 */

/**
 * Export graph to DOT format (Graphviz)
 */
class DotExporter(private val options: DotOptions = DotOptions()) {

    /**
     * Export a graph to DOT format
     */
    fun export(graph: DirectedGraph): String {
        return buildString {
            appendLine("digraph ${sanitizeId(graph.name)} {")
            appendLine("  ${options.toGraphAttributes()}")
            appendLine()

            // Add nodes
            graph.getNodes().forEach { node ->
                appendLine("  ${sanitizeId(node.id)} ${formatNodeAttributes(node)};")
            }

            appendLine()

            // Add edges
            graph.getEdges().forEach { edge ->
                appendLine("  ${sanitizeId(edge.from.id)} -> ${sanitizeId(edge.to.id)} ${formatEdgeAttributes(edge)};")
            }

            appendLine("}")
        }
    }

    private fun formatNodeAttributes(node: GraphNode): String {
        val attrs = mutableListOf<String>()

        // Add label
        attrs.add("label=\"${escapeLabel(node.label)}\"")

        // Add shape based on node type
        attrs.add("shape=\"${getNodeShape(node.type)}\"")

        // Add color based on node type
        val color = getNodeColor(node.type)
        if (color != null) {
            attrs.add("fillcolor=\"$color\"")
            attrs.add("style=filled")
        }

        // Add custom attributes
        node.attributes.forEach { (key, value) ->
            attrs.add("$key=\"${escapeLabel(value)}\"")
        }

        return "[${attrs.joinToString(", ")}]"
    }

    private fun formatEdgeAttributes(edge: GraphEdge): String {
        val attrs = mutableListOf<String>()

        // Add label if present
        if (edge.label != null) {
            attrs.add("label=\"${escapeLabel(edge.label)}\"")
        }

        // Add style based on edge type
        val style = getEdgeStyle(edge.type)
        if (style != null) {
            attrs.add("style=\"$style\"")
        }

        // Add color based on edge type
        val color = getEdgeColor(edge.type)
        if (color != null) {
            attrs.add("color=\"$color\"")
        }

        // Add custom attributes
        edge.attributes.forEach { (key, value) ->
            attrs.add("$key=\"${escapeLabel(value)}\"")
        }

        return if (attrs.isNotEmpty()) "[${attrs.joinToString(", ")}]" else ""
    }

    private fun getNodeShape(type: NodeType): String {
        return when (type) {
            NodeType.LITERAL -> "box"
            NodeType.VARIABLE -> "ellipse"
            NodeType.FUNCTION_CALL -> "component"
            NodeType.LAMBDA -> "diamond"
            NodeType.IF_CONDITION, NodeType.MATCH_EXPRESSION -> "diamond"
            NodeType.INPUT, NodeType.OUTPUT -> "parallelogram"
            NodeType.OBJECT -> "folder"
            NodeType.ARRAY -> "tab"
            NodeType.START, NodeType.END -> "doublecircle"
            NodeType.MERGE, NodeType.FORK -> "circle"
            else -> "ellipse"
        }
    }

    private fun getNodeColor(type: NodeType): String? {
        return when (type) {
            NodeType.LITERAL -> "#E8F4FD"
            NodeType.VARIABLE -> "#FFF4E6"
            NodeType.FUNCTION_CALL -> "#E6F7E6"
            NodeType.LAMBDA -> "#F4E6F7"
            NodeType.IF_CONDITION, NodeType.IF_THEN, NodeType.IF_ELSE -> "#FFE6E6"
            NodeType.INPUT -> "#E6FFE6"
            NodeType.OUTPUT -> "#FFE6E6"
            NodeType.OBJECT -> "#FFF4E6"
            NodeType.ARRAY -> "#E6F7FF"
            NodeType.START -> "#90EE90"
            NodeType.END -> "#FFB6C1"
            else -> null
        }
    }

    private fun getEdgeStyle(type: EdgeType): String? {
        return when (type) {
            EdgeType.CONTROL_FLOW -> "bold"
            EdgeType.DEPENDENCY -> "dashed"
            EdgeType.DEFINITION -> "dotted"
            EdgeType.PARENT_CHILD -> "solid"
            else -> null
        }
    }

    private fun getEdgeColor(type: EdgeType): String? {
        return when (type) {
            EdgeType.DATA_FLOW -> "#4682B4"
            EdgeType.CONTROL_FLOW -> "#DC143C"
            EdgeType.DEPENDENCY -> "#9370DB"
            EdgeType.CALL -> "#228B22"
            else -> null
        }
    }

    private fun sanitizeId(id: String): String {
        return if (id.matches(Regex("[a-zA-Z_][a-zA-Z0-9_]*"))) {
            id
        } else {
            "\"$id\""
        }
    }

    private fun escapeLabel(label: String): String {
        return label.replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\t", "\\t")
    }
}

/**
 * DOT export options
 */
data class DotOptions(
    val rankdir: RankDir = RankDir.TOP_TO_BOTTOM,
    val fontname: String = "Arial",
    val fontsize: Int = 12,
    val dpi: Int = 96,
    val splines: String = "ortho"
) {
    enum class RankDir {
        TOP_TO_BOTTOM,
        LEFT_TO_RIGHT,
        BOTTOM_TO_TOP,
        RIGHT_TO_LEFT;

        fun toDot(): String = when (this) {
            TOP_TO_BOTTOM -> "TB"
            LEFT_TO_RIGHT -> "LR"
            BOTTOM_TO_TOP -> "BT"
            RIGHT_TO_LEFT -> "RL"
        }
    }

    fun toGraphAttributes(): String {
        return buildString {
            appendLine("  rankdir=${rankdir.toDot()};")
            appendLine("  fontname=\"$fontname\";")
            appendLine("  fontsize=$fontsize;")
            appendLine("  dpi=$dpi;")
            appendLine("  splines=$splines;")
            appendLine("  node [fontname=\"$fontname\", fontsize=$fontsize];")
            appendLine("  edge [fontname=\"$fontname\", fontsize=${fontsize - 2}];")
        }
    }
}

/**
 * Export graph to Mermaid format
 */
class MermaidExporter {

    /**
     * Export a graph to Mermaid flowchart format
     */
    fun export(graph: DirectedGraph): String {
        return buildString {
            appendLine("flowchart ${getDirection(graph)}")
            appendLine()

            // Add nodes
            graph.getNodes().forEach { node ->
                val nodeId = sanitizeId(node.id)
                val nodeLabel = escapeLabel(node.label)
                val shape = getNodeShape(node.type)
                appendLine("    $nodeId${shape.start}\"$nodeLabel\"${shape.end}")
            }

            appendLine()

            // Add edges
            graph.getEdges().forEach { edge ->
                val fromId = sanitizeId(edge.from.id)
                val toId = sanitizeId(edge.to.id)
                val arrow = getArrow(edge.type)
                val label = edge.label?.let { "|${escapeLabel(it)}|" } ?: ""
                appendLine("    $fromId $arrow$label $toId")
            }

            // Add styling
            if (graph.getNodes().isNotEmpty()) {
                appendLine()
                appendLine("    %% Styling")
                graph.getNodes().forEach { node ->
                    val style = getNodeStyle(node.type)
                    if (style != null) {
                        appendLine("    style ${sanitizeId(node.id)} $style")
                    }
                }
            }
        }
    }

    private fun getDirection(graph: DirectedGraph): String {
        return when (graph.type) {
            GraphType.DATA_FLOW -> "LR"
            GraphType.CONTROL_FLOW -> "TB"
            GraphType.DEPENDENCY -> "TB"
            GraphType.CALL_GRAPH -> "LR"
            GraphType.HYBRID -> "TB"
        }
    }

    private data class NodeShape(val start: String, val end: String)

    private fun getNodeShape(type: NodeType): NodeShape {
        return when (type) {
            NodeType.LITERAL -> NodeShape("[", "]")
            NodeType.VARIABLE -> NodeShape("(", ")")
            NodeType.FUNCTION_CALL -> NodeShape("[[", "]]")
            NodeType.LAMBDA -> NodeShape("{", "}")
            NodeType.IF_CONDITION, NodeType.MATCH_EXPRESSION -> NodeShape("{", "}")
            NodeType.INPUT, NodeType.OUTPUT -> NodeShape("[/", "/]")
            NodeType.OBJECT -> NodeShape("[(", ")]")
            NodeType.ARRAY -> NodeShape("{{", "}}")
            NodeType.START, NodeType.END -> NodeShape("((", "))")
            else -> NodeShape("(", ")")
        }
    }

    private fun getArrow(type: EdgeType): String {
        return when (type) {
            EdgeType.DATA_FLOW -> "-->"
            EdgeType.CONTROL_FLOW -> "==>"
            EdgeType.DEPENDENCY -> "-.->"
            EdgeType.DEFINITION -> "-..->"
            EdgeType.CALL -> "-->"
            EdgeType.PARENT_CHILD -> "-->"
            EdgeType.USE -> "-..->"
            EdgeType.SEQUENCE -> "-->"
        }
    }

    private fun getNodeStyle(type: NodeType): String? {
        return when (type) {
            NodeType.LITERAL -> "fill:#E8F4FD,stroke:#4682B4"
            NodeType.VARIABLE -> "fill:#FFF4E6,stroke:#FF8C00"
            NodeType.FUNCTION_CALL -> "fill:#E6F7E6,stroke:#228B22"
            NodeType.LAMBDA -> "fill:#F4E6F7,stroke:#9370DB"
            NodeType.IF_CONDITION, NodeType.IF_THEN, NodeType.IF_ELSE -> "fill:#FFE6E6,stroke:#DC143C"
            NodeType.INPUT -> "fill:#E6FFE6,stroke:#32CD32"
            NodeType.OUTPUT -> "fill:#FFE6E6,stroke:#FF6347"
            NodeType.START -> "fill:#90EE90,stroke:#006400"
            NodeType.END -> "fill:#FFB6C1,stroke:#DC143C"
            else -> null
        }
    }

    private fun sanitizeId(id: String): String {
        return id.replace(Regex("[^a-zA-Z0-9_]"), "_")
    }

    private fun escapeLabel(label: String): String {
        return label.replace("\"", "&quot;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
    }
}

/**
 * Convenience object for exporting graphs
 */
object GraphVisualizer {

    /**
     * Export a graph to DOT format
     */
    fun toDot(graph: DirectedGraph, options: DotOptions = DotOptions()): String {
        return DotExporter(options).export(graph)
    }

    /**
     * Export a graph to Mermaid format
     */
    fun toMermaid(graph: DirectedGraph): String {
        return MermaidExporter().export(graph)
    }

    /**
     * Save a graph to a DOT file
     */
    fun saveDot(graph: DirectedGraph, filename: String, options: DotOptions = DotOptions()) {
        val dot = toDot(graph, options)
        java.io.File(filename).writeText(dot)
    }

    /**
     * Save a graph to a Mermaid file
     */
    fun saveMermaid(graph: DirectedGraph, filename: String) {
        val mermaid = toMermaid(graph)
        java.io.File(filename).writeText(mermaid)
    }
}
