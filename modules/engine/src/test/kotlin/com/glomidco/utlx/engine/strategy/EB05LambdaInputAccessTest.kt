package com.glomidco.utlx.engine.strategy

import com.glomidco.utlx.core.ast.Program
import com.glomidco.utlx.core.interpreter.Interpreter
import com.glomidco.utlx.core.lexer.Lexer
import com.glomidco.utlx.core.parser.ParseResult
import com.glomidco.utlx.core.parser.Parser
import com.glomidco.utlx.core.udm.UDM
import com.glomidco.utlx.engine.strategy.compiled.ASTCompiler
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * EB05 — **a lambda body must be able to reach a named input.**
 *
 * `compileIdentifier` resolves any name that is not a local by loading the named-input map from
 * **slot 1** and calling `Map.get`. Slot 1 is the inputs map in `execute(Map)` — slot 0 is `this` —
 * but a lambda is a *static* method `lambda_N(List args)`, where slot 1 held the **first lambda
 * parameter**. So a lambda body referring to an input compiled without complaint and then died:
 *
 * ```
 * java.lang.IncompatibleClassChangeError:
 *     Class com.glomidco.utlx.core.udm.UDM$Object does not implement
 *     the requested interface java.util.Map
 * ```
 *
 * The idiom that triggers it is ordinary — a `map` callback that reaches back to a second input, as
 * in a lookup join:
 *
 * ```utlx
 * map($input.orders, (order) -> { ..., customer: lookupBy(order.customerId, $input.customers, ...) })
 * ```
 *
 * The lambda now takes the map as a second parameter, which puts the inputs at slot 1 in **both**
 * method shapes. `compileIdentifier` therefore needs no knowledge of which kind of method it is
 * emitting into, and it works for a lambda created inside another lambda, where the enclosing frame
 * is itself a lambda method. The map is captured per construction rather than parked in a static
 * field on the generated class, which would have raced between concurrent executions of the same
 * compiled transformation.
 *
 * As with EB04, the tests assert `compile(...) != null` and then compare against the interpreter:
 * the symptom was a runtime failure on correctly-compiled-looking bytecode, so checking the output
 * alone would be satisfied by the interpreter fallback and prove nothing.
 */
class EB05LambdaInputAccessTest {

    private val input = UDM.Object.of(
        "orders" to UDM.Array(listOf(
            UDM.Object.of("id" to UDM.Scalar("A"), "cid" to UDM.Scalar("c1")),
            UDM.Object.of("id" to UDM.Scalar("B"), "cid" to UDM.Scalar("c2"))
        )),
        "customers" to UDM.Array(listOf(
            UDM.Object.of("id" to UDM.Scalar("c1"), "name" to UDM.Scalar("Acme")),
            UDM.Object.of("id" to UDM.Scalar("c2"), "name" to UDM.Scalar("Globex"))
        )),
        "rate" to UDM.Scalar(2L)
    )

    private fun program(body: String): Program {
        val src = "%utlx 1.0\ninput json\noutput json\n---\n$body"
        return when (val r = Parser(Lexer(src).tokenize(), src).parse()) {
            is ParseResult.Success -> r.program
            is ParseResult.Failure -> throw AssertionError("parse failed: ${r.errors}")
        }
    }

    private fun compiled(body: String): UDM {
        val fn = ASTCompiler().compile(program(body))
        assertNotNull(fn, "`$body` did not compile — the compiled path was not exercised")
        return fn.execute(mapOf("input" to input))
    }

    private fun interpreted(body: String): UDM =
        Interpreter().execute(program(body), input).toUDM()

    private fun assertAgree(body: String) =
        assertEquals(interpreted(body).toString(), compiled(body).toString(), "disagreed on `$body`")

    @Test
    fun `a lambda body can read a scalar input`() {
        assertAgree("map(\$input.orders, (o) -> \$input.rate)")
    }

    @Test
    fun `a lambda body can read a collection input — the lookup-join idiom`() {
        assertAgree("map(\$input.orders, (o) -> count(\$input.customers))")
    }

    @Test
    fun `a lambda parameter and an input are both reachable, and distinct`() {
        // The pre-EB05 bug was slot collision between the two, so this is the direct regression case.
        assertAgree("map(\$input.orders, (o) -> { id: o.id, others: count(\$input.customers) })")
    }

    @Test
    fun `a nested lambda can still read an input`() {
        // The enclosing frame here is itself a lambda method, so the inputs slot has to be right in
        // a lambda as well as in execute().
        assertAgree("map(\$input.orders, (o) -> map(\$input.customers, (c) -> c.name))")
    }

    @Test
    fun `a lambda with two parameters keeps its own parameters straight`() {
        // Parameters start at slot 2 now; this fails if the shift was applied inconsistently.
        assertAgree("reduce(\$input.orders, (acc, o) -> acc + 1, 0)")
    }

    @Test
    fun `a let binding inside a lambda still resolves as a local`() {
        assertAgree("map(\$input.orders, (o) -> { let n = o.id, out: n })")
    }
}
