package com.glomidco.utlx.core.interpreter

import com.glomidco.utlx.core.lexer.Lexer
import com.glomidco.utlx.core.parser.ParseResult
import com.glomidco.utlx.core.parser.Parser
import com.glomidco.utlx.core.semantic.LanguageNamespaces
import com.glomidco.utlx.core.udm.UDM
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertTrue

/**
 * VB05 regression tests — the `%utlx` namespace gate must be decided for the PROGRAM, not per payload.
 *
 * The gate used to live only in `tryLoadStdlibFunction`, i.e. at name resolution *during execution*,
 * so it fired only when a namespaced call was actually reached. A `%utlx 1.0` script could therefore
 * carry `validate.*` on a branch a given payload did not take and run perfectly — passing tests and
 * failing in production on the first payload to reach the call. Whether a 1.0 script was legal
 * depended on its input, which a version gate must never do.
 *
 * `LanguageVersionStaticGate` now walks the body once in `execute`, before any data is touched.
 * The runtime gate remains as a backstop.
 *
 * The over-reach risk is the mirror image: a namespace is only a namespace when its base identifier
 * is NOT a bound variable (the runtime path depends on the base being undefined). Several tests here
 * pin that down, since a false rejection would break legal 1.0 scripts.
 */
class VB05StaticVersionGateTest {

    // Stateless, so the global-registry closure trap (see B34LogicalShortCircuitTest) is harmless.
    private val lookup: Map<String, (List<UDM>) -> UDM> = mapOf(
        "validate.required" to { args -> args.firstOrNull() ?: UDM.Scalar.nullValue() }
    )

    private fun run(src: String): UDM {
        val tokens = Lexer(src).tokenize()
        val program = when (val r = Parser(tokens, src).parse()) {
            is ParseResult.Success -> r.program
            is ParseResult.Failure -> throw AssertionError("parse failed: ${r.errors}")
        }
        val interpreter = Interpreter()
        interpreter.setStdlibLookup(lookup)
        return interpreter.execute(program, UDM.Object.of("orderId" to UDM.Scalar(42))).toUDM()
    }

    private fun assertGated(src: String) {
        val e = assertThrows<RuntimeError> { run(src) }
        assertTrue(
            e.message!!.contains("requires %utlx 1.1"),
            "expected the namespace gate to fire, got: ${e.message}",
        )
    }

    // ── The VB05 defect: the call is present but never reached ──

    @Test fun `a 1_0 script is rejected for a validate call in an untaken branch`() {
        // orderId is 42, so the validate branch never runs. This used to succeed with exit 0.
        assertGated(
            """%utlx 1.0
input json
output json
---
if (${'$'}input.orderId > 1000) (${'$'}input |> validate.required(["orderId"]))
else { status: "no validation needed" }"""
        )
    }

    @Test fun `a 1_0 script is rejected for a validate call in a lambda that never runs`() {
        // map over an EMPTY array: the lambda body is never evaluated, so the runtime gate cannot
        // see it. Only the static walk can — which also proves the walk descends into Lambda bodies.
        assertGated(
            """%utlx 1.0
input json
output json
---
{ rows: map([], x => (${'$'}input |> validate.required(["orderId"]))) }"""
        )
    }

    @Test fun `a 1_0 script is rejected for a validate call inside a never-entered catch block`() {
        assertGated(
            """%utlx 1.0
input json
output json
---
try { "fine" } catch (e) { ${'$'}input |> validate.required(["orderId"]) }"""
        )
    }

    @Test fun `a 1_0 script is rejected for a validate call in an uncalled function`() {
        // The lambda is bound but never applied, so its body never executes — again invisible to
        // the runtime gate. (A `let` whose VALUE is the call would not test anything: let values are
        // evaluated eagerly, so the runtime gate catches that one on its own.)
        assertGated(
            """%utlx 1.0
input json
output json
---
let check = (x) => (${'$'}input |> validate.required(["orderId"])) in { status: "ok" }"""
        )
    }

    // ── Unchanged behaviour ──

    @Test fun `a 1_0 script is still rejected when the call is plainly reached`() {
        assertGated(
            """%utlx 1.0
input json
output json
---
${'$'}input |> validate.required(["orderId"])"""
        )
    }

    @Test fun `a 1_1 script passes the gate`() {
        run(
            """%utlx 1.1
input json
output json
---
${'$'}input |> validate.required(["orderId"])"""
        )
    }

    // ── Forward compatibility: the gate must stay version-generic ──

    @Test fun `a namespace gated at 1_1 stays available to a higher declared version`() {
        // If version comparison ever regressed, a future %utlx 2.0 script would be DENIED the 1.1
        // namespaces — the gate failing in the opposite direction. Asserted on the rule directly
        // (not through a script) because 2.0 is not an accepted engine version yet.
        assertTrue(LanguageNamespaces.isAvailableAt("validate.required", "2.0"))
        assertTrue(LanguageNamespaces.isAvailableAt("validate.required", "1.1"))
        assertTrue(!LanguageNamespaces.isAvailableAt("validate.required", "1.0"))
        // An unregistered namespace is ungated, not implicitly gated — adding `infer.*` at 2.0 is a
        // one-line data change in NAMESPACE_MIN_VERSION, with no change to either gate.
        assertTrue(LanguageNamespaces.isAvailableAt("infer.probability", "1.0"))
    }

    // ── Over-reach guards: a shadowed base is NOT a namespace ──

    @Test fun `a let-bound variable named validate is not the namespace`() {
        val r = run(
            """%utlx 1.0
input json
output json
---
let validate = { required: "just an object" } in { echo: validate.required }"""
        )
        assertTrue(r.toString().contains("just an object"), "shadowed access should evaluate: $r")
    }

    @Test fun `a lambda parameter named validate is not the namespace`() {
        // A real CALL, so the gate's FunctionCall branch is exercised: `validate` is a lambda
        // parameter, hence a bound variable, hence not the 1.1 namespace. This still fails at
        // RUNTIME (the parameter is the number 1, not an object), which is correct and beside the
        // point — what matters is that it is NOT rejected by the version gate.
        val e = runCatching {
            run(
                """%utlx 1.0
input json
output json
---
{ rows: map([1], validate => validate.required(["orderId"])) }"""
            )
        }.exceptionOrNull()
        assertTrue(
            e == null || !e.message!!.contains("requires %utlx"),
            "the gate must not fire for a shadowed base, got: ${e?.message}",
        )
    }

    @Test fun `an object-literal let binding named validate is not the namespace`() {
        run(
            """%utlx 1.0
input json
output json
---
{ let validate = { required: 1 }, out: validate.required }"""
        )
    }
}
