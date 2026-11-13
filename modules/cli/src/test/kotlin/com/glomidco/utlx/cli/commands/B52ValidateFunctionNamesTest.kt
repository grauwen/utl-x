// modules/cli/src/test/kotlin/com/glomidco/utlx/cli/commands/B52ValidateFunctionNamesTest.kt
package com.glomidco.utlx.cli.commands

import com.glomidco.utlx.cli.CommandResult
import com.glomidco.utlx.stdlib.StandardLibrary
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * B52 — **`utlx validate` reported every stdlib call as an undefined name.**
 *
 * The `FunctionCall` branch of the type checker opened with `inferType(expr.function, env)`,
 * run first and unconditionally, so a named callee went through *variable* resolution and was
 * recorded as "Undefined variable" before the stdlib lookup ever ran. `upper("a")` and a genuine
 * typo produced the identical warning, which made the whole type-warning section noise: every
 * real script emitted dozens of them, so nobody could see the one that mattered.
 *
 * Why this test lives in `:modules:cli` and not next to the fix:
 *
 *  - The dependency direction is `stdlib → core`, so `TypeChecker` *cannot* see the dispatch
 *    registry. It takes the callable names as a constructor parameter and stays **silent** when
 *    that set is empty — otherwise `--strict` would start failing on the ~750 functions core has
 *    no signature for. `B52FunctionCallTypeCheckTest` in `:modules:core` covers that contract
 *    with a hand-written set.
 *  - Supplying the *authoritative* set is the CLI's job (`ValidateCommand`). So "a real function
 *    produces no warning" is only true end-to-end here, and only here can it be asserted against
 *    `StandardLibrary.getAllFunctionNames()` rather than against a list that drifts.
 *
 * The conformance suite cannot host this: its runner hard-codes `transform` (simple-runner.py,
 * `cmd = [str(utlx_cli), 'transform', transform_file]`) and has no command dimension, so no
 * fixture can invoke `validate` at all.
 */
class B52ValidateFunctionNamesTest {

    @TempDir
    lateinit var tempDir: Path

    private var n = 0

    /** Run `utlx validate` in-process and return (result, everything it printed). */
    private fun validate(body: String, vararg flags: String): Pair<CommandResult, String> {
        val script = tempDir.resolve("b52-${n++}.utlx").toFile()
        script.writeText("%utlx 1.0\ninput json\noutput json\n---\n$body\n")
        val buffer = ByteArrayOutputStream()
        val saved = System.out
        try {
            System.setOut(PrintStream(buffer, true, "UTF-8"))
            val result = ValidateCommand.execute(arrayOf(script.absolutePath, *flags))
            return result to buffer.toString("UTF-8")
        } finally {
            System.setOut(saved)
        }
    }

    private fun undefinedLines(output: String) =
        output.lines().map { it.trim() }.filter { it.contains("Undefined") }

    // ── guard against a vacuous harness ──

    @Test
    fun `the harness really runs the type checker`() {
        // If validate were a no-op, or stdout were not captured, every assertion below would
        // pass trivially. This is the one case that must produce output.
        val (_, output) = validate("{ r: totallyMadeUpFunction(1) }")
        assertEquals(
            listOf("⚠ 5:6 - Undefined function: totallyMadeUpFunction"),
            undefinedLines(output)
        )
    }

    // ── the bug itself ──

    @Test
    fun `a spread of real stdlib functions produces no undefined-name warning`() {
        val (result, output) = validate(
            """{ a: upper("x"), b: count([1,2]), c: sum([1,2]),
               |  d: join(["a","b"], ","), e: lower(trim("  X  ")) }""".trimMargin()
        )
        assertEquals(emptyList(), undefinedLines(output),
            "a call to a registered function must not be reported as undefined")
        assertTrue(result is CommandResult.Success)
    }

    @Test
    fun `every callable function name is known to validate`() {
        // The authoritative set is the dispatch registry — the same thing the interpreter calls.
        // Anything dispatchable but unknown to the type checker is a false "Undefined function",
        // which is exactly the class of defect B52 was. Asserting against the registry rather
        // than a hand-written list is the point: a list drifts, and the drift is the bug.
        val identifier = Regex("^[A-Za-z_][A-Za-z0-9_]*$")
        val names = StandardLibrary.getAllFunctionNames().filter { identifier.matches(it) }.sorted()
        assertTrue(names.size > 500, "expected a populated registry, got ${names.size}")

        val falsePositives = mutableListOf<String>()
        val unparseable = mutableListOf<String>()
        for (name in names) {
            val (_, output) = validate("{ r: $name() }")
            if (output.contains("Expected") || output.contains("Parse")) {
                // A registered name that is also a reserved word cannot appear in call position,
                // so the script carries no type information and the name is out of scope here.
                unparseable.add(name)
                continue
            }
            if (output.contains("Undefined function: $name")) falsePositives.add(name)
        }

        assertEquals(emptyList(), falsePositives,
            "${names.size - unparseable.size} names checked; these dispatch at runtime " +
            "but validate calls them undefined")

        // Pinned exactly, because the skip branch is what could hollow this test out: if a change
        // made many names unparseable, the sweep above would pass by checking almost nothing.
        // `if` is registered yet uncallable as `if(...)` — a reserved word. That is a separate,
        // pre-existing wart, recorded in the B52 report; it is not what this test is guarding.
        assertEquals(listOf("if"), unparseable.sorted(),
            "the set of registered-but-uncallable names changed — if a name was added here, " +
            "the sweep above silently stopped covering it")
    }

    // ── what must still be reported ──

    @Test
    fun `a misspelled function is reported once, naming it, at the callee`() {
        val (result, output) = validate("{ r: totallyMadeUpFunction(1) }")
        assertEquals(
            listOf("⚠ 5:6 - Undefined function: totallyMadeUpFunction"),
            undefinedLines(output),
            "column must point at the callee, not the call or the property"
        )
        assertTrue(result is CommandResult.Success,
            "without --strict a type warning must not fail validation")
    }

    @Test
    fun `two misspellings are reported separately`() {
        val (_, output) = validate("{ a: fnOne(1), b: fnTwo(2) }")
        assertEquals(
            listOf(
                "⚠ 5:6 - Undefined function: fnOne",
                "⚠ 5:19 - Undefined function: fnTwo"
            ),
            undefinedLines(output)
        )
    }

    @Test
    fun `a misspelling inside a lambda body is still reported`() {
        val (_, output) = validate("{ r: map([1], (x) -> nosuchfn(x)) }")
        assertEquals(listOf("⚠ 5:22 - Undefined function: nosuchfn"), undefinedLines(output))
    }

    @Test
    fun `an undefined variable is still reported as a variable`() {
        // Resolving a named callee as a function first must not swallow genuine variable errors,
        // and the two diagnostics must stay distinguishable.
        val (_, output) = validate("{ r: someUnknownVar }")
        assertEquals(listOf("⚠ 5:6 - Undefined variable: someUnknownVar"), undefinedLines(output))
    }

    @Test
    fun `--strict turns the warning into a failure`() {
        val (result, output) = validate("{ r: totallyMadeUpFunction(1) }", "--strict")
        assertTrue(output.contains("Type Errors"), "under --strict it is an error, not a warning")
        assertTrue(result is CommandResult.Failure, "expected failure, got $result")
        assertEquals(1, (result as CommandResult.Failure).exitCode)
    }

    @Test
    fun `--strict still passes a script that only calls real functions`() {
        val (result, _) = validate("""{ a: upper("x"), b: count([1,2]) }""", "--strict")
        assertTrue(result is CommandResult.Success, "expected success, got $result")
    }

    // ── the regression the first fix introduced ──

    @Test
    fun `a let-bound lambda called in the same object is not undefined`() {
        // The ObjectLiteral branch ignored `letBindings` entirely and type-checked every property
        // against the *outer* environment, so once a named callee was resolved as a function
        // first, `f(21)` became "Undefined function: f". Fixed with a child scope.
        val (result, output) = validate("{ let f = (x) -> x * 2, r: f(21) }")
        assertEquals(emptyList(), undefinedLines(output))
        assertTrue(result is CommandResult.Success)
    }

    @Test
    fun `a let-bound lambda is in scope in a nested object`() {
        val (_, output) = validate("{ outer: { let h = (x) -> x, v: h(1) } }")
        assertEquals(emptyList(), undefinedLines(output))
    }

    @Test
    fun `a function-valued let at script level is in scope`() {
        val (_, output) = validate("let g = (x) -> x + 1\n{ r: g(1) }")
        assertEquals(emptyList(), undefinedLines(output))
    }

    @Test
    fun `a lambda bound by let and used higher-order is not undefined`() {
        val (_, output) = validate("{ let ap = (f, v) -> f(v), r: ap((x) -> x + 1, 2) }")
        assertEquals(emptyList(), undefinedLines(output))
    }

    /**
     * B53 — the 12 functions that carry a core signature, each in both spellings.
     *
     * `Any` was assignable to nothing, and everything from `$input` is typed `Any` without a
     * schema, so every one of these failed `--strict` on the input-derived form while passing on
     * the literal — and all of them run. The literal half is kept in the same test because it is
     * what stops an over-broad fix silencing real mismatches.
     */
    @Test
    fun `a signature accepts an input-derived argument as well as a literal`() {
        val pairs = listOf(
            "upper(\$input.s)" to "upper(\"a\")",
            "lower(\$input.s)" to "lower(\"A\")",
            "trim(\$input.s)" to "trim(\" a \")",
            "length(\$input.s)" to "length(\"ab\")",
            "abs(\$input.n)" to "abs(-1)",
            "round(\$input.n)" to "round(1.5)",
            "ceil(\$input.n)" to "ceil(1.2)",
            "floor(\$input.n)" to "floor(1.8)",
            "sum(\$input.a)" to "sum([1,2])",
            "count(\$input.a)" to "count([1])",
            "min(\$input.n, 1)" to "min(2, 1)",
            "max(\$input.n, 1)" to "max(2, 1)"
        )
        val offenders = mutableListOf<String>()
        for ((fromInput, fromLiteral) in pairs) {
            for (expr in listOf(fromInput, fromLiteral)) {
                val (result, _) = validate("{ r: $expr }", "--strict")
                if (result !is CommandResult.Success) offenders.add(expr)
            }
        }
        assertEquals(emptyList(), offenders,
            "these fail --strict although they run; a signature must accept a value the " +
                "checker cannot name")
    }

    @Test
    fun `a realistic script passes --strict`() {
        // The script from the B53 report. `filter` has a signature and `groupBy` does not, so
        // before the fix this failed while the groupBy equivalent passed — adding a signature
        // made validation worse.
        val (result, _) = validate(
            "{ r: map(filter(\$input.items, (i) -> i.p > 1), (i) -> i.p) }", "--strict")
        assertTrue(result is CommandResult.Success, "expected success, got $result")
    }

    @Test
    fun `--strict still rejects a genuine type mismatch`() {
        // The other half of B53: the fix must not silence real errors. These are literal
        // arguments, so `Any` never enters the question.
        for (bad in listOf("upper(42)", "abs(\"x\")", "\"abc\" - 1")) {
            val (result, _) = validate("{ r: $bad }", "--strict")
            assertTrue(result is CommandResult.Failure, "`$bad` should fail --strict, got $result")
        }
    }

    @Test
    fun `a let shadowing a stdlib function is not checked against the stdlib signature`() {
        // The first cut of the B52 fix consulted stdlib BEFORE the environment, so a binding that
        // shadows a stdlib name was type-checked against the wrong signature: this script runs
        // fine (the interpreter yields 3) but validate reported both "Function 'count' expects 1
        // arguments, got 2" and an argument type mismatch. A binding in scope wins, as it does at
        // runtime.
        val (result, output) = validate("{ let count = (a, b) -> a + b, r: count(1, 2) }")
        assertEquals(emptyList<String>(),
            output.lines().map { it.trim() }.filter { it.startsWith("⚠") || it.startsWith("✗ ") },
            "a shadowing let must not be checked against the stdlib signature it shadows")
        assertTrue(result is CommandResult.Success)
    }

    @Test
    fun `a let shadowing a stdlib function passes --strict`() {
        val (result, _) = validate(
            """{ let upper = (x) -> "S", r: upper("a") }""", "--strict")
        assertTrue(result is CommandResult.Success, "expected success, got $result")
    }

    @Test
    fun `a let binding does not leak out of the object that declares it`() {
        // The child scope must be a child, not a mutation of the parent.
        val (_, output) = validate("{ inner: { let k = 1, v: k }, outer: k }")
        assertEquals(listOf("⚠ 5:38 - Undefined variable: k"), undefinedLines(output))
    }
}
