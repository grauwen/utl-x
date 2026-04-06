// modules/analysis/src/main/kotlin/org/apache/utlx/analysis/graph/GraphAnalysis.kt
package org.apache.utlx.analysis.graph

/**
 * Graph analysis algorithms
 *
 * Provides:
 * - Cycle detection
 * - Topological sorting
 * - Path finding
 * - Reachability analysis
 * - Strongly connected components
 */
object GraphAnalysis {

    /**
     * Detect cycles in the graph
     * Returns true if the graph contains at least one cycle
     */
    fun hasCycle(graph: DirectedGraph): Boolean {
        val visited = mutableSetOf<GraphNode>()
        val recursionStack = mutableSetOf<GraphNode>()

        fun dfs(node: GraphNode): Boolean {
            visited.add(node)
            recursionStack.add(node)

            for (successor in graph.getSuccessors(node)) {
                if (!visited.contains(successor)) {
                    if (dfs(successor)) return true
                } else if (recursionStack.contains(successor)) {
                    return true // Cycle found
                }
            }

            recursionStack.remove(node)
            return false
        }

        return graph.getNodes().any { node ->
            if (!visited.contains(node)) dfs(node) else false
        }
    }

    /**
     * Find all cycles in the graph
     * Returns a list of cycles, where each cycle is a list of nodes
     */
    fun findCycles(graph: DirectedGraph): List<List<GraphNode>> {
        val cycles = mutableListOf<List<GraphNode>>()
        val visited = mutableSetOf<GraphNode>()
        val path = mutableListOf<GraphNode>()

        fun dfs(node: GraphNode) {
            visited.add(node)
            path.add(node)

            for (successor in graph.getSuccessors(node)) {
                if (path.contains(successor)) {
                    // Found a cycle
                    val cycleStart = path.indexOf(successor)
                    cycles.add(path.subList(cycleStart, path.size).toList() + successor)
                } else if (!visited.contains(successor)) {
                    dfs(successor)
                }
            }

            path.removeLast()
        }

        graph.getNodes().forEach { node ->
            if (!visited.contains(node)) {
                dfs(node)
            }
        }

        return cycles
    }

    /**
     * Perform topological sort on the graph
     * Returns null if the graph contains a cycle
     */
    fun topologicalSort(graph: DirectedGraph): List<GraphNode>? {
        if (hasCycle(graph)) return null

        val visited = mutableSetOf<GraphNode>()
        val result = mutableListOf<GraphNode>()

        fun dfs(node: GraphNode) {
            visited.add(node)

            for (successor in graph.getSuccessors(node)) {
                if (!visited.contains(successor)) {
                    dfs(successor)
                }
            }

            result.add(0, node)
        }

        graph.getNodes().forEach { node ->
            if (!visited.contains(node)) {
                dfs(node)
            }
        }

        return result
    }

    /**
     * Find all paths from source to target
     * Limited to maxPaths to avoid exponential explosion
     */
    fun findAllPaths(
        graph: DirectedGraph,
        source: GraphNode,
        target: GraphNode,
        maxPaths: Int = 100
    ): List<List<GraphNode>> {
        val paths = mutableListOf<List<GraphNode>>()
        val currentPath = mutableListOf<GraphNode>()
        val visited = mutableSetOf<GraphNode>()

        fun dfs(node: GraphNode) {
            if (paths.size >= maxPaths) return

            visited.add(node)
            currentPath.add(node)

            if (node == target) {
                paths.add(currentPath.toList())
            } else {
                for (successor in graph.getSuccessors(node)) {
                    if (!visited.contains(successor)) {
                        dfs(successor)
                    }
                }
            }

            currentPath.removeLast()
            visited.remove(node)
        }

        dfs(source)
        return paths
    }

    /**
     * Find shortest path from source to target using BFS
     * Returns null if no path exists
     */
    fun findShortestPath(
        graph: DirectedGraph,
        source: GraphNode,
        target: GraphNode
    ): List<GraphNode>? {
        if (source == target) return listOf(source)

        val queue = ArrayDeque<GraphNode>()
        val visited = mutableSetOf<GraphNode>()
        val parent = mutableMapOf<GraphNode, GraphNode>()

        queue.add(source)
        visited.add(source)

        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()

            if (current == target) {
                // Reconstruct path
                val path = mutableListOf<GraphNode>()
                var node: GraphNode? = target
                while (node != null) {
                    path.add(0, node)
                    node = parent[node]
                }
                return path
            }

            for (successor in graph.getSuccessors(current)) {
                if (!visited.contains(successor)) {
                    visited.add(successor)
                    parent[successor] = current
                    queue.add(successor)
                }
            }
        }

        return null // No path found
    }

    /**
     * Get all nodes reachable from the given node
     */
    fun getReachableNodes(graph: DirectedGraph, source: GraphNode): Set<GraphNode> {
        val reachable = mutableSetOf<GraphNode>()

        fun dfs(node: GraphNode) {
            reachable.add(node)
            for (successor in graph.getSuccessors(node)) {
                if (!reachable.contains(successor)) {
                    dfs(successor)
                }
            }
        }

        dfs(source)
        return reachable
    }

    /**
     * Get all nodes that can reach the given node
     */
    fun getReachingNodes(graph: DirectedGraph, target: GraphNode): Set<GraphNode> {
        val reaching = mutableSetOf<GraphNode>()

        fun dfs(node: GraphNode) {
            reaching.add(node)
            for (predecessor in graph.getPredecessors(node)) {
                if (!reaching.contains(predecessor)) {
                    dfs(predecessor)
                }
            }
        }

        dfs(target)
        return reaching
    }

    /**
     * Find strongly connected components using Tarjan's algorithm
     */
    fun findStronglyConnectedComponents(graph: DirectedGraph): List<Set<GraphNode>> {
        val components = mutableListOf<Set<GraphNode>>()
        val index = mutableMapOf<GraphNode, Int>()
        val lowLink = mutableMapOf<GraphNode, Int>()
        val onStack = mutableSetOf<GraphNode>()
        val stack = ArrayDeque<GraphNode>()
        var currentIndex = 0

        fun strongConnect(node: GraphNode) {
            index[node] = currentIndex
            lowLink[node] = currentIndex
            currentIndex++
            stack.addLast(node)
            onStack.add(node)

            for (successor in graph.getSuccessors(node)) {
                if (!index.containsKey(successor)) {
                    strongConnect(successor)
                    lowLink[node] = minOf(lowLink[node]!!, lowLink[successor]!!)
                } else if (onStack.contains(successor)) {
                    lowLink[node] = minOf(lowLink[node]!!, index[successor]!!)
                }
            }

            if (lowLink[node] == index[node]) {
                val component = mutableSetOf<GraphNode>()
                var w: GraphNode
                do {
                    w = stack.removeLast()
                    onStack.remove(w)
                    component.add(w)
                } while (w != node)

                components.add(component)
            }
        }

        graph.getNodes().forEach { node ->
            if (!index.containsKey(node)) {
                strongConnect(node)
            }
        }

        return components
    }

    /**
     * Calculate graph metrics
     */
    fun calculateMetrics(graph: DirectedGraph): GraphMetrics {
        val nodes = graph.getNodes()
        val edges = graph.getEdges()

        val inDegrees = nodes.map { graph.getIncomingEdges(it).size }
        val outDegrees = nodes.map { graph.getOutgoingEdges(it).size }

        return GraphMetrics(
            nodeCount = nodes.size,
            edgeCount = edges.size,
            density = if (nodes.size > 1) {
                edges.size.toDouble() / (nodes.size * (nodes.size - 1))
            } else 0.0,
            avgInDegree = inDegrees.average(),
            avgOutDegree = outDegrees.average(),
            maxInDegree = inDegrees.maxOrNull() ?: 0,
            maxOutDegree = outDegrees.maxOrNull() ?: 0,
            hasCycle = hasCycle(graph),
            stronglyConnectedComponentCount = findStronglyConnectedComponents(graph).size,
            entryNodeCount = graph.getEntryNodes().size,
            exitNodeCount = graph.getExitNodes().size
        )
    }

    /**
     * Find the longest path in a DAG (Directed Acyclic Graph)
     * Returns null if the graph contains a cycle
     */
    fun findLongestPath(graph: DirectedGraph): List<GraphNode>? {
        val sorted = topologicalSort(graph) ?: return null

        val distance = mutableMapOf<GraphNode, Int>()
        val parent = mutableMapOf<GraphNode, GraphNode?>()

        // Initialize distances
        sorted.forEach { node ->
            distance[node] = 0
            parent[node] = null
        }

        // Process nodes in topological order
        sorted.forEach { node ->
            graph.getSuccessors(node).forEach { successor ->
                val newDistance = distance[node]!! + 1
                if (newDistance > distance[successor]!!) {
                    distance[successor] = newDistance
                    parent[successor] = node
                }
            }
        }

        // Find node with maximum distance
        val endNode = distance.maxByOrNull { it.value }?.key ?: return emptyList()

        // Reconstruct path
        val path = mutableListOf<GraphNode>()
        var current: GraphNode? = endNode
        while (current != null) {
            path.add(0, current)
            current = parent[current]
        }

        return path
    }
}

/**
 * Graph metrics for analysis
 */
data class GraphMetrics(
    val nodeCount: Int,
    val edgeCount: Int,
    val density: Double,
    val avgInDegree: Double,
    val avgOutDegree: Double,
    val maxInDegree: Int,
    val maxOutDegree: Int,
    val hasCycle: Boolean,
    val stronglyConnectedComponentCount: Int,
    val entryNodeCount: Int,
    val exitNodeCount: Int
) {
    override fun toString(): String = buildString {
        appendLine("Graph Metrics:")
        appendLine("  Nodes: $nodeCount")
        appendLine("  Edges: $edgeCount")
        appendLine("  Density: ${"%.4f".format(density)}")
        appendLine("  Average in-degree: ${"%.2f".format(avgInDegree)}")
        appendLine("  Average out-degree: ${"%.2f".format(avgOutDegree)}")
        appendLine("  Max in-degree: $maxInDegree")
        appendLine("  Max out-degree: $maxOutDegree")
        appendLine("  Has cycle: $hasCycle")
        appendLine("  SCCs: $stronglyConnectedComponentCount")
        appendLine("  Entry nodes: $entryNodeCount")
        appendLine("  Exit nodes: $exitNodeCount")
    }
}
