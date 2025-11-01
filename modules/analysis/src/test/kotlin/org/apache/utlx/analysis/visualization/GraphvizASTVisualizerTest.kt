// modules/analysis/src/test/kotlin/org/apache/utlx/analysis/visualization/GraphvizASTVisualizerTest.kt
package org.apache.utlx.analysis.visualization

import org.apache.utlx.core.ast.*
import org.junit.jupiter.api.Test
import kotlin.test.assertTrue
import kotlin.test.assertContains

class GraphvizASTVisualizerTest {

    private val visualizer = GraphvizASTVisualizer()
    private val loc = Location(1, 1)

    @Test
    fun `should generate DOT for simple program`() {
        // Create a simple program: output: "Hello"
        val program = Program(
            header = Header(
                version = "1.0",
                inputs = listOf("input" to FormatSpec(FormatType.JSON, null, emptyMap(), loc)),
                outputs = listOf("output" to FormatSpec(FormatType.JSON, null, emptyMap(), loc)),
                location = loc
            ),
            body = Expression.ObjectLiteral(
                properties = listOf(
                    Property(
                        key = "output",
                        value = Expression.StringLiteral("Hello", loc),
                        location = loc
                    )
                ),
                location = loc
            ),
            location = loc
        )

        val dot = visualizer.visualize(program)

        // Verify DOT structure
        assertTrue(dot.startsWith("digraph AST {"))
        assertTrue(dot.endsWith("}\n"))
        assertContains(dot, "Program")
        assertContains(dot, "Header")
        assertContains(dot, "Object")
        assertContains(dot, "Hello")
    }

    @Test
    fun `should generate DOT for binary operation`() {
        // Create: 2 + 3
        val program = Program(
            header = Header("1.0", emptyList(), emptyList(), loc),
            body = Expression.BinaryOp(
                left = Expression.NumberLiteral(2.0, loc),
                operator = BinaryOperator.PLUS,
                right = Expression.NumberLiteral(3.0, loc),
                location = loc
            ),
            location = loc
        )

        val dot = visualizer.visualize(program)

        assertContains(dot, "+")
        assertContains(dot, "2.0")
        assertContains(dot, "3.0")
        assertContains(dot, "left")
        assertContains(dot, "right")
    }

    @Test
    fun `should generate DOT for map operation`() {
        // Create: items map (x) => x.name
        val program = Program(
            header = Header("1.0", emptyList(), emptyList(), loc),
            body = Expression.FunctionCall(
                function = Expression.MemberAccess(
                    target = Expression.Identifier("items", loc),
                    property = "map",
                    location = loc
                ),
                arguments = listOf(
                    Expression.Lambda(
                        parameters = listOf(Parameter("x", null, loc)),
                        body = Expression.MemberAccess(
                            target = Expression.Identifier("x", loc),
                            property = "name",
                            location = loc
                        ),
                        location = loc
                    )
                ),
                location = loc
            ),
            location = loc
        )

        val dot = visualizer.visualize(program)

        assertContains(dot, "call")
        assertContains(dot, "λ(x)")
        assertContains(dot, "items")
        assertContains(dot, ".map")
        assertContains(dot, ".name")
    }

    @Test
    fun `should generate DOT for conditional`() {
        // Create: if (x > 10) "big" else "small"
        val program = Program(
            header = Header("1.0", emptyList(), emptyList(), loc),
            body = Expression.Conditional(
                condition = Expression.BinaryOp(
                    left = Expression.Identifier("x", loc),
                    operator = BinaryOperator.GREATER_THAN,
                    right = Expression.NumberLiteral(10.0, loc),
                    location = loc
                ),
                thenBranch = Expression.StringLiteral("big", loc),
                elseBranch = Expression.StringLiteral("small", loc),
                location = loc
            ),
            location = loc
        )

        val dot = visualizer.visualize(program)

        assertContains(dot, "if-else")
        assertContains(dot, ">")
        assertContains(dot, "big")
        assertContains(dot, "small")
    }

    @Test
    fun `should generate DOT with different layouts`() {
        val program = Program(
            header = Header("1.0", emptyList(), emptyList(), loc),
            body = Expression.StringLiteral("test", loc),
            location = loc
        )

        // Test left-right layout
        val dotLR = visualizer.visualize(program, VisualizationOptions(layout = "LR"))
        assertContains(dotLR, "rankdir=LR")

        // Test top-bottom layout
        val dotTB = visualizer.visualize(program, VisualizationOptions(layout = "TB"))
        assertContains(dotTB, "rankdir=TB")
    }

    @Test
    fun `should generate DOT for complex transformation`() {
        // Create a more complex example with let bindings and object construction
        // let total = prices reduce (a, b) => a + b
        // output: { count: prices.length, total: total }
        val program = Program(
            header = Header("1.0", emptyList(), emptyList(), loc),
            body = Expression.ObjectLiteral(
                letBindings = listOf(
                    Expression.LetBinding(
                        name = "total",
                        value = Expression.FunctionCall(
                            function = Expression.MemberAccess(
                                target = Expression.Identifier("prices", loc),
                                property = "reduce",
                                location = loc
                            ),
                            arguments = listOf(
                                Expression.Lambda(
                                    parameters = listOf(
                                        Parameter("a", null, loc),
                                        Parameter("b", null, loc)
                                    ),
                                    body = Expression.BinaryOp(
                                        left = Expression.Identifier("a", loc),
                                        operator = BinaryOperator.PLUS,
                                        right = Expression.Identifier("b", loc),
                                        location = loc
                                    ),
                                    location = loc
                                )
                            ),
                            location = loc
                        ),
                        location = loc
                    )
                ),
                properties = listOf(
                    Property(
                        key = "count",
                        value = Expression.MemberAccess(
                            target = Expression.Identifier("prices", loc),
                            property = "length",
                            location = loc
                        ),
                        location = loc
                    ),
                    Property(
                        key = "total",
                        value = Expression.Identifier("total", loc),
                        location = loc
                    )
                ),
                location = loc
            ),
            location = loc
        )

        val dot = visualizer.visualize(program)

        // Verify all key elements are present
        assertContains(dot, "Object")
        assertContains(dot, "let total")
        assertContains(dot, "λ(a, b)")
        assertContains(dot, "reduce")
        assertContains(dot, "count")
        assertContains(dot, "total")
    }

    @Test
    fun `should handle array literals`() {
        // Create: [1, 2, 3]
        val program = Program(
            header = Header("1.0", emptyList(), emptyList(), loc),
            body = Expression.ArrayLiteral(
                elements = listOf(
                    Expression.NumberLiteral(1.0, loc),
                    Expression.NumberLiteral(2.0, loc),
                    Expression.NumberLiteral(3.0, loc)
                ),
                location = loc
            ),
            location = loc
        )

        val dot = visualizer.visualize(program)

        assertContains(dot, "Array")
        assertContains(dot, "[3]")
        assertContains(dot, "1.0")
        assertContains(dot, "2.0")
        assertContains(dot, "3.0")
    }

    @Test
    fun `should handle pipe operations`() {
        // Create: data |> filter |> map
        val program = Program(
            header = Header("1.0", emptyList(), emptyList(), loc),
            body = Expression.Pipe(
                source = Expression.Identifier("data", loc),
                target = Expression.Pipe(
                    source = Expression.Identifier("filter", loc),
                    target = Expression.Identifier("map", loc),
                    location = loc
                ),
                location = loc
            ),
            location = loc
        )

        val dot = visualizer.visualize(program)

        assertContains(dot, "|>")
        assertContains(dot, "data")
        assertContains(dot, "filter")
        assertContains(dot, "map")
    }
}
