// modules/analysis/src/main/kotlin/org/apache/utlx/analysis/graph/Graph.kt
package org.apache.utlx.analysis.graph

/**
 * Core graph data structures for representing UTL-X transformations
 *
 * Supports:
 * - Data flow graphs (how data moves through transformations)
 * - Control flow graphs (execution paths)
 * - Dependency graphs (variable and function dependencies)
 */

/**
 * Represents a node in the graph
 */
data class GraphNode(
    val id: String,
    val label: String,
    val type: NodeType,
    val metadata: Map<String, Any> = emptyMap(),
    val attributes: Map<String, String> = emptyMap()
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is GraphNode) return false
        return id == other.id
    }

    override fun hashCode(): Int = id.hashCode()
}

/**
 * Node types for different graph representations
 */
enum class NodeType {
    // Expression nodes
    LITERAL,           // Constant value
    VARIABLE,          // Variable reference
    PROPERTY_ACCESS,   // Object property access
    ARRAY_ACCESS,      // Array element access
    FUNCTION_CALL,     // Function invocation
    LAMBDA,            // Lambda function
    BINARY_OP,         // Binary operation (+, -, *, etc.)
    UNARY_OP,          // Unary operation (!, -, etc.)

    // Control flow nodes
    IF_CONDITION,      // If expression condition
    IF_THEN,           // If expression then branch
    IF_ELSE,           // If expression else branch
    MATCH_EXPRESSION,  // Match expression
    MATCH_CASE,        // Match case

    // Data flow nodes
    INPUT,             // Input source
    OUTPUT,            // Output destination
    TRANSFORMATION,    // Data transformation
    LET_BINDING,       // Let binding (variable definition)

    // Structural nodes
    OBJECT,            // Object literal
    ARRAY,             // Array literal
    PROPERTY,          // Object property

    // Special nodes
    START,             // Entry point
    END,               // Exit point
    MERGE,             // Control flow merge point
    FORK              // Control flow fork point
}

/**
 * Represents an edge in the graph
 */
data class GraphEdge(
    val from: GraphNode,
    val to: GraphNode,
    val label: String? = null,
    val type: EdgeType = EdgeType.DATA_FLOW,
    val attributes: Map<String, String> = emptyMap()
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is GraphEdge) return false
        return from == other.from && to == other.to && type == other.type
    }

    override fun hashCode(): Int {
        var result = from.hashCode()
        result = 31 * result + to.hashCode()
        result = 31 * result + type.hashCode()
        return result
    }
}

/**
 * Edge types for different relationships
 */
enum class EdgeType {
    DATA_FLOW,         // Data flows from source to target
    CONTROL_FLOW,      // Control flows from source to target
    DEPENDENCY,        // Target depends on source
    DEFINITION,        // Source defines target
    USE,               // Source uses target
    CALL,              // Function call relationship
    PARENT_CHILD,      // Structural parent-child relationship
    SEQUENCE           // Sequential execution order
}

/**
 * Directed graph implementation
 */
class DirectedGraph(
    val name: String = "graph",
    val type: GraphType = GraphType.DATA_FLOW
) {
    private val nodes = mutableMapOf<String, GraphNode>()
    private val edges = mutableSetOf<GraphEdge>()
    private val adjacencyList = mutableMapOf<GraphNode, MutableSet<GraphEdge>>()
    private val reverseAdjacencyList = mutableMapOf<GraphNode, MutableSet<GraphEdge>>()

    val metadata = mutableMapOf<String, Any>()

    /**
     * Add a node to the graph
     */
    fun addNode(node: GraphNode): GraphNode {
        nodes[node.id] = node
        adjacencyList.computeIfAbsent(node) { mutableSetOf() }
        reverseAdjacencyList.computeIfAbsent(node) { mutableSetOf() }
        return node
    }

    /**
     * Add an edge to the graph
     */
    fun addEdge(edge: GraphEdge): GraphEdge {
        // Ensure both nodes exist
        addNode(edge.from)
        addNode(edge.to)

        edges.add(edge)
        adjacencyList[edge.from]?.add(edge)
        reverseAdjacencyList[edge.to]?.add(edge)
        return edge
    }

    /**
     * Add an edge by node IDs
     */
    fun addEdge(
        fromId: String,
        toId: String,
        label: String? = null,
        type: EdgeType = EdgeType.DATA_FLOW,
        attributes: Map<String, String> = emptyMap()
    ): GraphEdge? {
        val from = nodes[fromId] ?: return null
        val to = nodes[toId] ?: return null
        return addEdge(GraphEdge(from, to, label, type, attributes))
    }

    /**
     * Get a node by ID
     */
    fun getNode(id: String): GraphNode? = nodes[id]

    /**
     * Get all nodes
     */
    fun getNodes(): Set<GraphNode> = nodes.values.toSet()

    /**
     * Get all edges
     */
    fun getEdges(): Set<GraphEdge> = edges.toSet()

    /**
     * Get outgoing edges from a node
     */
    fun getOutgoingEdges(node: GraphNode): Set<GraphEdge> {
        return adjacencyList[node]?.toSet() ?: emptySet()
    }

    /**
     * Get incoming edges to a node
     */
    fun getIncomingEdges(node: GraphNode): Set<GraphEdge> {
        return reverseAdjacencyList[node]?.toSet() ?: emptySet()
    }

    /**
     * Get successors of a node
     */
    fun getSuccessors(node: GraphNode): Set<GraphNode> {
        return getOutgoingEdges(node).map { it.to }.toSet()
    }

    /**
     * Get predecessors of a node
     */
    fun getPredecessors(node: GraphNode): Set<GraphNode> {
        return getIncomingEdges(node).map { it.from }.toSet()
    }

    /**
     * Get nodes with no incoming edges (entry points)
     */
    fun getEntryNodes(): Set<GraphNode> {
        return nodes.values.filter { getIncomingEdges(it).isEmpty() }.toSet()
    }

    /**
     * Get nodes with no outgoing edges (exit points)
     */
    fun getExitNodes(): Set<GraphNode> {
        return nodes.values.filter { getOutgoingEdges(it).isEmpty() }.toSet()
    }

    /**
     * Get statistics about the graph
     */
    fun getStatistics(): GraphStatistics {
        return GraphStatistics(
            nodeCount = nodes.size,
            edgeCount = edges.size,
            entryNodeCount = getEntryNodes().size,
            exitNodeCount = getExitNodes().size,
            averageDegree = if (nodes.isNotEmpty()) edges.size.toDouble() / nodes.size else 0.0,
            maxInDegree = nodes.values.maxOfOrNull { getIncomingEdges(it).size } ?: 0,
            maxOutDegree = nodes.values.maxOfOrNull { getOutgoingEdges(it).size } ?: 0
        )
    }

    override fun toString(): String {
        return "DirectedGraph(name='$name', nodes=${nodes.size}, edges=${edges.size})"
    }
}

/**
 * Graph type classification
 */
enum class GraphType {
    DATA_FLOW,         // Shows how data flows through transformations
    CONTROL_FLOW,      // Shows execution paths
    DEPENDENCY,        // Shows dependencies between components
    CALL_GRAPH,        // Shows function call relationships
    HYBRID             // Combines multiple graph types
}

/**
 * Graph statistics
 */
data class GraphStatistics(
    val nodeCount: Int,
    val edgeCount: Int,
    val entryNodeCount: Int,
    val exitNodeCount: Int,
    val averageDegree: Double,
    val maxInDegree: Int,
    val maxOutDegree: Int
) {
    override fun toString(): String = buildString {
        appendLine("Graph Statistics:")
        appendLine("  Nodes: $nodeCount")
        appendLine("  Edges: $edgeCount")
        appendLine("  Entry points: $entryNodeCount")
        appendLine("  Exit points: $exitNodeCount")
        appendLine("  Average degree: ${"%.2f".format(averageDegree)}")
        appendLine("  Max in-degree: $maxInDegree")
        appendLine("  Max out-degree: $maxOutDegree")
    }
}

/**
 * Builder for creating graphs fluently
 */
class GraphBuilder(private val name: String = "graph", private val type: GraphType = GraphType.DATA_FLOW) {
    private val graph = DirectedGraph(name, type)
    private var nodeIdCounter = 0

    fun node(
        id: String? = null,
        label: String,
        type: NodeType,
        metadata: Map<String, Any> = emptyMap(),
        attributes: Map<String, String> = emptyMap()
    ): GraphNode {
        val nodeId = id ?: "node_${nodeIdCounter++}"
        val node = GraphNode(nodeId, label, type, metadata, attributes)
        return graph.addNode(node)
    }

    fun edge(
        from: GraphNode,
        to: GraphNode,
        label: String? = null,
        type: EdgeType = EdgeType.DATA_FLOW,
        attributes: Map<String, String> = emptyMap()
    ): GraphEdge {
        return graph.addEdge(GraphEdge(from, to, label, type, attributes))
    }

    fun metadata(key: String, value: Any): GraphBuilder {
        graph.metadata[key] = value
        return this
    }

    fun build(): DirectedGraph = graph
}
