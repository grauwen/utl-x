package com.glomidco.utlx.core.interpreter

import com.glomidco.utlx.core.lexer.Lexer
import com.glomidco.utlx.core.parser.ParseResult
import com.glomidco.utlx.core.parser.Parser
import com.glomidco.utlx.core.udm.UDM
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * F21 prerequisite — namespaced stdlib function-call resolution (`ns.fn(...)`).
 *
 * A call like `validate.required(...)` parses as `FunctionCall(MemberAccess(Identifier("validate"),
 * "required"), args)`. The interpreter used to derive a stdlib name only from a *bare Identifier* callee,
 * so a namespaced call tried to resolve the base (`validate`) as a variable → "Undefined variable" and
 * never reached the lookup. Now the `MemberAccess` callee is qualified to `"ns.fn"` and resolved through
 * the same reflection-free lookup. This is what makes the `validate.*` namespace callable at all.
 *
 * Strategy (as in B25): a synthetic DOTTED key that exists ONLY in the lookup — nothing else can satisfy it.
 */
class NamespacedFunctionCallTest {

    private val lookup: Map<String, (List<UDM>) -> UDM> = mapOf(
        "ns.triple" to { args ->
            UDM.Scalar(((args[0] as UDM.Scalar).value as Number).toDouble() * 3.0)
        },
        // "validate.*" is a %utlx 1.1 language namespace (the gate rejects it under 1.0).
        "validate.ok" to { args -> args[0] }
    )

    private fun run(body: String): UDM = runV("1.0", body)

    private fun runV(version: String, body: String): UDM {
        val src = "%utlx $version\ninput json\noutput json\n---\n$body"
        val tokens = Lexer(src).tokenize()
        val program = when (val r = Parser(tokens, src).parse()) {
            is ParseResult.Success -> r.program
            is ParseResult.Failure -> throw AssertionError("parse failed: ${r.errors}")
        }
        val interpreter = Interpreter()
        interpreter.setStdlibLookup(lookup)
        return interpreter.execute(program, UDM.Scalar(5.0)).toUDM()
    }

    private fun scalar(udm: UDM) = ((udm as UDM.Scalar).value as Number).toDouble()
    private fun nums(udm: UDM) = (udm as UDM.Array).elements.map { ((it as UDM.Scalar).value as Number).toDouble() }

    @Test
    fun `namespaced call resolves to the dotted lookup key`() {
        assertEquals(15.0, scalar(run("ns.triple(\$input)")))
    }

    @Test
    fun `namespaced call resolves inside a lambda (map owner path)`() {
        assertEquals(listOf(3.0, 6.0, 9.0), nums(run("map([1, 2, 3], x => ns.triple(x))")))
    }

    @Test
    fun `an unknown namespaced function is not silently resolved`() {
        assertThrows<Throwable> { run("ns.missing(\$input)") }
    }

    // ── F21 language-version gate (namespace-based) ──

    @Test
    fun `validate namespace is rejected under utlx 1_0`() {
        val e = assertThrows<Throwable> { runV("1.0", "validate.ok(\$input)") }
        assertTrue(e.message?.contains("1.1") == true, "expected a version-gate message, got: ${e.message}")
    }

    @Test
    fun `validate namespace is allowed under utlx 1_1`() {
        assertEquals(5.0, scalar(runV("1.1", "validate.ok(\$input)")))
    }

    @Test
    fun `a non-gated namespace is unaffected by the version gate under 1_0`() {
        assertEquals(15.0, scalar(runV("1.0", "ns.triple(\$input)")))   // "ns" has no min-version
    }
}
