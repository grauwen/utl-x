package org.apache.utlx.core.interpreter

import org.apache.utlx.core.lexer.Lexer
import org.apache.utlx.core.parser.ParseResult
import org.apache.utlx.core.parser.Parser
import org.apache.utlx.core.udm.UDM
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

/**
 * B25 root-cause regression tests.
 *
 * Root cause: stdlib functions live in the `stdlib` module and reach the interpreter through a
 * lookup map (Interpreter.setStdlibLookup) — a GraalVM-native-safe path that uses NO reflection.
 * If the lookup is not consulted, calls fall back to `Class.forName("...StandardLibrary")` +
 * reflective invoke, which throws on the native binary (NoSuchMethod/missing reflect-config). The
 * bug had two faces:
 *
 *   (a) Top level: the lookup must be consulted before the reflection fallback.
 *   (b) Inside map/filter/reduce: the lambda body used to be evaluated on a throwaway `Interpreter()`
 *       that never received the lookup, so any stdlib call inside a lambda hit the reflection path
 *       and failed on native. The fix routes lambda evaluation through the owning interpreter.
 *
 * Strategy: register a synthetic function that exists ONLY in the lookup — it is not in
 * StandardLibraryImpl and is not a real method on the stdlib `StandardLibrary` class, so the
 * reflection fallback CANNOT satisfy it. The only way these programs can succeed is the
 * reflection-free lookup path. If the owner fix regresses, the map/filter/reduce cases throw
 * "Undefined function tripleIt".
 */
class B25StdlibLookupTest {

    // "tripleIt" exists only here — never in StandardLibraryImpl, never as a real stdlib method.
    private val syntheticLookup: Map<String, (List<UDM>) -> UDM> = mapOf(
        "tripleIt" to { args ->
            val n = ((args[0] as UDM.Scalar).value as Number).toDouble()
            UDM.Scalar(n * 3.0)
        }
    )

    private fun run(body: String): UDM {
        val src = "%utlx 1.0\ninput json\noutput json\n---\n$body"
        val tokens = Lexer(src).tokenize()
        val program = when (val r = Parser(tokens, src).parse()) {
            is ParseResult.Success -> r.program
            is ParseResult.Failure -> throw AssertionError("parse failed: ${r.errors}")
        }
        val interpreter = Interpreter()
        interpreter.setStdlibLookup(syntheticLookup)
        return interpreter.execute(program, UDM.Scalar(5.0)).toUDM()
    }

    private fun scalar(udm: UDM): Double = ((udm as UDM.Scalar).value as Number).toDouble()
    private fun nums(udm: UDM): List<Double> =
        (udm as UDM.Array).elements.map { ((it as UDM.Scalar).value as Number).toDouble() }

    @Test
    fun `lookup-only stdlib function resolves at top level (no reflection)`() {
        assertEquals(15.0, scalar(run("tripleIt(\$input)")))
    }

    @Test
    fun `lookup-only stdlib function resolves inside map (B25 owner fix)`() {
        assertEquals(listOf(3.0, 6.0, 9.0), nums(run("map([1, 2, 3], x => tripleIt(x))")))
    }

    @Test
    fun `lookup-only stdlib function resolves inside filter (B25 owner fix)`() {
        assertEquals(listOf(2.0, 3.0), nums(run("filter([1, 2, 3], x => tripleIt(x) > 5)")))
    }

    @Test
    fun `lookup-only stdlib function resolves inside reduce (B25 owner fix)`() {
        assertEquals(18.0, scalar(run("reduce([1, 2, 3], (acc, x) => acc + tripleIt(x), 0)")))
    }
}
