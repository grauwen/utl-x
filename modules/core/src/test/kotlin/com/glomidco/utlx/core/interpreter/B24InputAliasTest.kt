package com.glomidco.utlx.core.interpreter

import com.glomidco.utlx.core.lexer.Lexer
import com.glomidco.utlx.core.parser.ParseResult
import com.glomidco.utlx.core.parser.Parser
import com.glomidco.utlx.core.udm.UDM
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * B24 — **`$input` must never silently become a different document.**
 *
 * The alias keyed off the runtime map alone: "if no `input` arrived, use the first one". Empty
 * inputs are dropped before they reach the interpreter, so a header declaring
 *
 * ```
 * input: input json, enterprise-order json     // 'input' empty, the other carries the data
 * ```
 *
 * left no `input` in the map, and every `$input` in the body quietly resolved to
 * *enterprise-order*. The transformation succeeded and produced plausible output from the wrong
 * document. Worse, the engine's strategies were stricter, so the same script could pass in IDE
 * preview and fail in production.
 *
 * The rule now consults the declared header rather than what happened to arrive:
 *
 *  1. header declares an input named `input` → `$input` is that input; if absent, it stays
 *     unbound and the reference fails rather than becoming another document
 *  2. no `input` declared and exactly one input → alias it (the single-input convenience)
 *  3. no `input` declared and more than one → no alias; `$input` is ambiguous
 */
class B24InputAliasTest {

    private fun run(header: String, body: String, inputs: Map<String, UDM>): UDM {
        val src = "%utlx 1.0\n$header\noutput json\n---\n$body"
        val program = when (val r = Parser(Lexer(src).tokenize(), src).parse()) {
            is ParseResult.Success -> r.program
            is ParseResult.Failure -> throw AssertionError("parse failed: ${r.errors}")
        }
        return Interpreter().execute(program, inputs).toUDM()
    }

    private fun str(result: UDM) = ((result as UDM.Object).properties["r"] as UDM.Scalar).value

    private val fromOrder = UDM.Object.of("orderId" to UDM.Scalar("FROM-ORDER"))
    private val fromInput = UDM.Object.of("orderId" to UDM.Scalar("FROM-INPUT"))

    // ── the defect ──

    @Test
    fun `a declared but absent 'input' is not substituted by another input`() {
        // The reported case. Previously returned "FROM-ORDER" — the wrong document, silently.
        assertFailsWith<RuntimeError> {
            run(
                "input: input json, enterprise-order json",
                "{ r: \$input.orderId }",
                mapOf("enterprise-order" to fromOrder)      // 'input' empty, so absent here
            )
        }
    }

    @Test
    fun `with no 'input' declared and several inputs, the alias is not applied`() {
        assertFailsWith<RuntimeError> {
            run(
                "input: orders json, customers json",
                "{ r: \$input.orderId }",
                mapOf("orders" to fromOrder, "customers" to fromInput)
            )
        }
    }

    // ── what must keep working ──

    @Test
    fun `a declared 'input' that is supplied resolves to itself, not to the first entry`() {
        // Ordering must not decide this: 'enterprise-order' is first in the map.
        val r = run(
            "input: input json, enterprise-order json",
            "{ r: \$input.orderId }",
            linkedMapOf("enterprise-order" to fromOrder, "input" to fromInput)
        )
        assertEquals("FROM-INPUT", str(r))
    }

    @Test
    fun `the single-input convenience survives`() {
        // The commonest script of all: the header names nothing and one document is supplied.
        val r = run("input json", "{ r: \$input.orderId }", mapOf("input" to fromOrder))
        assertEquals("FROM-ORDER", str(r))
    }

    @Test
    fun `a single named input is still reachable as both its name and as input`() {
        val r = run("input: orders json", "{ r: \$orders.orderId }", mapOf("orders" to fromOrder))
        assertEquals("FROM-ORDER", str(r))
        val aliased = run("input: orders json", "{ r: \$input.orderId }", mapOf("orders" to fromOrder))
        assertEquals("FROM-ORDER", str(aliased), "one unambiguous input keeps the alias")
    }

    @Test
    fun `named inputs are reachable by name when there are several`() {
        val r = run(
            "input: orders json, customers json",
            "{ r: \$orders.orderId }",
            mapOf("orders" to fromOrder, "customers" to fromInput)
        )
        assertEquals("FROM-ORDER", str(r))
    }
}
