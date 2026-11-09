package com.glomidco.utlx.engine.strategy

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
 * EB11 — **a spread must take effect where it is written.**
 *
 * Two defects in the same operator, both from building the container before applying the spreads:
 *
 *  - **Object literals** compiled every keyed property first and then merged the spreads on top. So
 *    the spread always won regardless of position:
 *
 *    ```
 *    { ...$input, z: 9 }     interpreter  a, b, z     compiled  z, a, b    ← wrong ORDER
 *    { ...$input, a: 99 }    interpreter  a = 99      compiled  a = 1      ← wrong VALUE
 *    ```
 *
 *  - **Array literals** compiled a `SpreadElement` as an ordinary element, so `[...arr]` produced
 *    `[[1, 2, 3]]` — nested rather than flattened.
 *
 * Order is not cosmetic: XML output *is* element order, and the interpreter's rule is last-wins by
 * position, so getting the order wrong gets the value wrong too. Both literals now fold their
 * segments left to right — runs of plain members grouped into one build call, spreads merged at
 * their position — which is the interpreter's rule by construction rather than by coincidence.
 *
 * A spread of a non-array contributes nothing, mirroring the interpreter, which evaluates it and
 * silently ignores anything that is not an array. Mirrored rather than improved: the point is
 * agreement.
 */
class EB11SpreadOrderTest {

    private val input = UDM.Object.of(
        "a" to UDM.Scalar(1L),
        "b" to UDM.Scalar(2L),
        "arr" to UDM.Array(listOf(UDM.Scalar(1L), UDM.Scalar(2L))),
        "arr2" to UDM.Array(listOf(UDM.Scalar(3L))),
        "scalar" to UDM.Scalar("s")
    )

    private fun program(body: String) =
        "%utlx 1.0\ninput json\noutput json\n---\n$body".let { src ->
            when (val r = Parser(Lexer(src).tokenize(), src).parse()) {
                is ParseResult.Success -> r.program
                is ParseResult.Failure -> throw AssertionError("parse failed: ${r.errors}")
            }
        }

    /** Compiled output must equal interpreted, and must genuinely have compiled. */
    private fun assertAgree(body: String) {
        val fn = ASTCompiler().compile(program(body))
        assertNotNull(fn, "`$body` did not compile — the compiled path was not exercised")
        val compiled = fn.execute(mapOf("input" to input)).toString()
        val interpreted = Interpreter().execute(program(body), input).toUDM().toString()
        assertEquals(interpreted, compiled, "disagreed on `$body`")
    }

    // ── Object spread: position decides both order and precedence ──

    @Test
    fun `a spread before a property keeps the spread's keys first`() {
        assertAgree("{ ...\$input, z: 9 }")
    }

    @Test
    fun `a spread after a property keeps the property first`() {
        assertAgree("{ z: 9, ...\$input }")
    }

    @Test
    fun `a spread between properties lands between them`() {
        assertAgree("{ y: 8, ...\$input, z: 9 }")
    }

    @Test
    fun `a later property overrides a spread`() {
        // This was a wrong VALUE, not only a wrong order: the spread used to win.
        assertAgree("{ ...\$input, a: 99 }")
    }

    @Test
    fun `a later spread overrides an earlier property`() {
        assertAgree("{ a: 99, ...\$input }")
    }

    @Test
    fun `two spreads apply in order`() {
        assertAgree("{ ...\$input, ...\$input }")
    }

    @Test
    fun `a spread alongside an attribute`() {
        // EB08's attribute split has to survive the regrouping.
        assertAgree("{ R: { @id: \"7\", ...\$input, z: 9 } }")
    }

    @Test
    fun `an object with no spread is unchanged`() {
        assertAgree("{ x: 1, y: 2 }")
    }

    // ── Array spread: flattening ──

    @Test
    fun `an array spread is flattened, not nested`() {
        assertAgree("[...\$input.arr]")
    }

    @Test
    fun `a spread among plain elements keeps position`() {
        assertAgree("[0, ...\$input.arr, 9]")
    }

    @Test
    fun `two array spreads both flatten in order`() {
        assertAgree("[...\$input.arr, ...\$input.arr2]")
    }

    @Test
    fun `spreading a non-array contributes nothing`() {
        assertAgree("[0, ...\$input.scalar, 9]")
    }

    @Test
    fun `an array with no spread is unchanged`() {
        assertAgree("[1, 2, 3]")
    }

    @Test
    fun `an empty array and an empty object still build`() {
        assertAgree("[]")
        assertAgree("{ }")
    }
}
