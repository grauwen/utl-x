// modules/analysis/src/test/kotlin/org/apache/utlx/analysis/graph/GraphExamplesTest.kt
package org.apache.utlx.analysis.graph

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Examples and tests for graph functionality
 *
 * Demonstrates:
 * - Creating graphs directly
 * - Graph visualization (DOT and Mermaid)
 * - Graph analysis (cycles, paths, metrics)
 */
class GraphExamplesTest {

    /**
     * Example 1: Simple graph creation and visualization
     */
    @Test
    fun `example 1 - simple graph creation`() {
        val builder = GraphBuilder("simple_flow", GraphType.DATA_FLOW)

        val input = builder.node(id = "input", label = "Input", type = NodeType.INPUT)
        val process = builder.node(id = "proc", label = "Process", type = NodeType.TRANSFORMATION)
        val output = builder.node(id = "output", label = "Output", type = NodeType.OUTPUT)

        builder.edge(input, process, type = EdgeType.DATA_FLOW)
        builder.edge(process, output, type = EdgeType.DATA_FLOW)

        val graph = builder.build()

        // Verify graph structure
        assertNotNull(graph)
        assertEquals(3, graph.getNodes().size)
        assertEquals(2, graph.getEdges().size)

        // Get statistics
        val stats = graph.getStatistics()
        println("=== Simple Graph Statistics ===")
        println(stats)

        // Export to DOT format
        val dotExporter = DotExporter()
        val dot = dotExporter.export(graph)
        println("\n=== DOT Format ===")
        println(dot)

        // Verify DOT output
        assertTrue(dot.contains("digraph"))
        assertTrue(dot.contains("input"))
        assertTrue(dot.contains("output"))

        // Export to Mermaid format
        val mermaidExporter = MermaidExporter()
        val mermaid = mermaidExporter.export(graph)
        println("\n=== Mermaid Format ===")
        println(mermaid)

        // Verify Mermaid output
        assertTrue(mermaid.contains("flowchart"))
    }

    /**
     * Example 2: Data flow graph with multiple operations
     */
    @Test
    fun `example 2 - data flow graph`() {
        val builder = GraphBuilder("data_flow", GraphType.DATA_FLOW)

        // Create nodes
        val input = builder.node(id = "in", label = "Input", type = NodeType.INPUT)
        val val1 = builder.node(id = "v1", label = "10", type = NodeType.LITERAL)
        val val2 = builder.node(id = "v2", label = "20", type = NodeType.LITERAL)
        val add = builder.node(id = "add", label = "+", type = NodeType.BINARY_OP)
        val multiply = builder.node(id = "mul", label = "*", type = NodeType.BINARY_OP)
        val output = builder.node(id = "out", label = "Output", type = NodeType.OUTPUT)

        // Connect them
        builder.edge(input, multiply, label = "left", type = EdgeType.DATA_FLOW)
        builder.edge(val1, add, label = "left", type = EdgeType.DATA_FLOW)
        builder.edge(val2, add, label = "right", type = EdgeType.DATA_FLOW)
        builder.edge(add, multiply, label = "right", type = EdgeType.DATA_FLOW)
        builder.edge(multiply, output, type = EdgeType.DATA_FLOW)

        val graph = builder.build()

        println("=== Data Flow Graph ===")
        println("Nodes: ${graph.getNodes().size}")
        println("Edges: ${graph.getEdges().size}")

        val mermaid = GraphVisualizer.toMermaid(graph)
        println("\n=== Mermaid Visualization ===")
        println(mermaid)
    }

    /**
     * Example 3: Dependency graph
     */
    @Test
    fun `example 3 - dependency graph`() {
        val builder = GraphBuilder("dependencies", GraphType.DEPENDENCY)

        val varA = builder.node(id = "a", label = "A", type = NodeType.VARIABLE)
        val varB = builder.node(id = "b", label = "B", type = NodeType.VARIABLE)
        val varC = builder.node(id = "c", label = "C", type = NodeType.VARIABLE)
        val varD = builder.node(id = "d", label = "D", type = NodeType.VARIABLE)

        builder.edge(varA, varB, type = EdgeType.DEPENDENCY)
        builder.edge(varA, varC, type = EdgeType.DEPENDENCY)
        builder.edge(varB, varD, type = EdgeType.DEPENDENCY)
        builder.edge(varC, varD, type = EdgeType.DEPENDENCY)

        val graph = builder.build()

        println("=== Dependency Graph ===")
        val dot = GraphVisualizer.toDot(graph)
        println(dot)

        assertTrue(dot.contains("style=\"dashed\""))  // Dependency edges are dashed
    }

    /**
     * Example 4: Cycle detection
     */
    @Test
    fun `example 4 - cycle detection`() {
        val graph = createGraphWithCycle()

        val hasCycle = GraphAnalysis.hasCycle(graph)
        println("=== Cycle Detection ===")
        println("Has cycle: $hasCycle")

        if (hasCycle) {
            val cycles = GraphAnalysis.findCycles(graph)
            println("Found ${cycles.size} cycle(s)")
            cycles.forEachIndexed { index, cycle ->
                println("  Cycle ${index + 1}: ${cycle.map { it.label }.joinToString(" -> ")}")
            }
        }

        assertTrue(hasCycle)
        val cycles = GraphAnalysis.findCycles(graph)
        assertTrue(cycles.isNotEmpty())
    }

    /**
     * Example 5: Topological sort on DAG
     */
    @Test
    fun `example 5 - topological sort`() {
        val graph = createDAG()

        val sorted = GraphAnalysis.topologicalSort(graph)
        println("=== Topological Sort ===")
        if (sorted != null) {
            println("Order: ${sorted.map { it.label }.joinToString(" -> ")}")
        } else {
            println("Graph contains cycles, cannot sort")
        }

        assertNotNull(sorted)
        assertEquals(5, sorted.size)
    }

    /**
     * Example 6: Path finding
     */
    @Test
    fun `example 6 - path finding`() {
        val graph = createDAG()
        val entryNodes = graph.getEntryNodes().toList()
        val exitNodes = graph.getExitNodes().toList()

        if (entryNodes.isNotEmpty() && exitNodes.isNotEmpty()) {
            val source = entryNodes.first()
            val target = exitNodes.first()

            println("=== Path Finding ===")
            println("Source: ${source.label}")
            println("Target: ${target.label}")

            // Find shortest path
            val shortestPath = GraphAnalysis.findShortestPath(graph, source, target)
            if (shortestPath != null) {
                println("Shortest path (${shortestPath.size} nodes): ${shortestPath.map { it.label }.joinToString(" -> ")}")
            }

            // Find all paths
            val allPaths = GraphAnalysis.findAllPaths(graph, source, target, maxPaths = 10)
            println("Found ${allPaths.size} path(s)")
            allPaths.forEachIndexed { index, path ->
                println("  Path ${index + 1}: ${path.map { it.label }.joinToString(" -> ")}")
            }

            assertNotNull(shortestPath)
            assertTrue(allPaths.isNotEmpty())
        }
    }

    /**
     * Example 7: Graph metrics
     */
    @Test
    fun `example 7 - graph metrics`() {
        val graph = createComplexGraph()

        val metrics = GraphAnalysis.calculateMetrics(graph)
        println("=== Graph Metrics ===")
        println(metrics)

        assertTrue(metrics.nodeCount > 0)
        assertTrue(metrics.edgeCount >= 0)
        assertTrue(metrics.density >= 0.0)
    }

    /**
     * Example 8: Reachability analysis
     */
    @Test
    fun `example 8 - reachability analysis`() {
        val graph = createDAG()
        val entryNodes = graph.getEntryNodes()

        println("=== Reachability Analysis ===")
        println("Entry nodes: ${entryNodes.size}")

        entryNodes.forEach { entry ->
            val reachable = GraphAnalysis.getReachableNodes(graph, entry)
            println("From '${entry.label}' can reach ${reachable.size} nodes:")
            println("  ${reachable.map { it.label }.joinToString(", ")}")
        }

        assertTrue(entryNodes.isNotEmpty())
    }

    /**
     * Example 9: Strongly connected components
     */
    @Test
    fun `example 9 - strongly connected components`() {
        val graph = createGraphWithCycle()

        val sccs = GraphAnalysis.findStronglyConnectedComponents(graph)
        println("=== Strongly Connected Components ===")
        println("Found ${sccs.size} SCC(s)")

        sccs.forEachIndexed { index, scc ->
            println("  SCC ${index + 1} (${scc.size} nodes): ${scc.map { it.label }.joinToString(", ")}")
        }

        assertTrue(sccs.isNotEmpty())
    }

    /**
     * Example 10: Longest path in DAG
     */
    @Test
    fun `example 10 - longest path`() {
        val graph = createDAG()

        val longestPath = GraphAnalysis.findLongestPath(graph)
        println("=== Longest Path ===")
        if (longestPath != null) {
            println("Length: ${longestPath.size}")
            println("Path: ${longestPath.map { it.label }.joinToString(" -> ")}")
        }

        assertNotNull(longestPath)
    }

    /**
     * Example 11: Export to files
     */
    @Test
    fun `example 11 - export to files`() {
        val graph = createComplexGraph()

        val tempDir = System.getProperty("java.io.tmpdir")
        val dotFile = "$tempDir/utlx-graph.dot"
        val mermaidFile = "$tempDir/utlx-graph.mmd"

        // Save to files
        GraphVisualizer.saveDot(graph, dotFile)
        GraphVisualizer.saveMermaid(graph, mermaidFile)

        println("=== Exported Files ===")
        println("DOT: $dotFile")
        println("Mermaid: $mermaidFile")

        // Verify files exist
        assertTrue(java.io.File(dotFile).exists())
        assertTrue(java.io.File(mermaidFile).exists())
    }

    /**
     * Example 12: Custom DOT options
     */
    @Test
    fun `example 12 - custom DOT options`() {
        val graph = createDAG()

        // Export with custom options
        val options = DotOptions(
            rankdir = DotOptions.RankDir.LEFT_TO_RIGHT,
            fontname = "Courier",
            fontsize = 14,
            dpi = 150
        )

        val dot = GraphVisualizer.toDot(graph, options)
        println("=== DOT with Custom Options ===")
        println(dot)

        assertTrue(dot.contains("rankdir=LR"))
        assertTrue(dot.contains("fontname=\"Courier\""))
    }

    // Helper functions to create test graphs

    private fun createDAG(): DirectedGraph {
        val builder = GraphBuilder("dag", GraphType.DATA_FLOW)

        val n1 = builder.node(id = "n1", label = "Start", type = NodeType.START)
        val n2 = builder.node(id = "n2", label = "Process A", type = NodeType.TRANSFORMATION)
        val n3 = builder.node(id = "n3", label = "Process B", type = NodeType.TRANSFORMATION)
        val n4 = builder.node(id = "n4", label = "Merge", type = NodeType.MERGE)
        val n5 = builder.node(id = "n5", label = "End", type = NodeType.END)

        builder.edge(n1, n2, type = EdgeType.DATA_FLOW)
        builder.edge(n1, n3, type = EdgeType.DATA_FLOW)
        builder.edge(n2, n4, type = EdgeType.DATA_FLOW)
        builder.edge(n3, n4, type = EdgeType.DATA_FLOW)
        builder.edge(n4, n5, type = EdgeType.DATA_FLOW)

        return builder.build()
    }

    private fun createGraphWithCycle(): DirectedGraph {
        val builder = GraphBuilder("cycle", GraphType.DEPENDENCY)

        val n1 = builder.node(id = "n1", label = "A", type = NodeType.VARIABLE)
        val n2 = builder.node(id = "n2", label = "B", type = NodeType.VARIABLE)
        val n3 = builder.node(id = "n3", label = "C", type = NodeType.VARIABLE)

        builder.edge(n1, n2, type = EdgeType.DEPENDENCY)
        builder.edge(n2, n3, type = EdgeType.DEPENDENCY)
        builder.edge(n3, n1, type = EdgeType.DEPENDENCY)  // Creates cycle

        return builder.build()
    }

    private fun createComplexGraph(): DirectedGraph {
        val builder = GraphBuilder("complex", GraphType.HYBRID)

        val nodes = (1..10).map { i ->
            builder.node(
                id = "n$i",
                label = "Node $i",
                type = if (i == 1) NodeType.START else if (i == 10) NodeType.END else NodeType.TRANSFORMATION
            )
        }

        // Create a more complex structure
        builder.edge(nodes[0], nodes[1], type = EdgeType.DATA_FLOW)
        builder.edge(nodes[0], nodes[2], type = EdgeType.DATA_FLOW)
        builder.edge(nodes[1], nodes[3], type = EdgeType.DATA_FLOW)
        builder.edge(nodes[1], nodes[4], type = EdgeType.DATA_FLOW)
        builder.edge(nodes[2], nodes[4], type = EdgeType.DATA_FLOW)
        builder.edge(nodes[2], nodes[5], type = EdgeType.DATA_FLOW)
        builder.edge(nodes[3], nodes[6], type = EdgeType.DATA_FLOW)
        builder.edge(nodes[4], nodes[6], type = EdgeType.DATA_FLOW)
        builder.edge(nodes[4], nodes[7], type = EdgeType.DATA_FLOW)
        builder.edge(nodes[5], nodes[7], type = EdgeType.DATA_FLOW)
        builder.edge(nodes[6], nodes[8], type = EdgeType.DATA_FLOW)
        builder.edge(nodes[7], nodes[8], type = EdgeType.DATA_FLOW)
        builder.edge(nodes[8], nodes[9], type = EdgeType.DATA_FLOW)

        return builder.build()
    }
}
