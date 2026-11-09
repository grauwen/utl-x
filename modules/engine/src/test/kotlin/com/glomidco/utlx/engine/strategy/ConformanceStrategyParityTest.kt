package com.glomidco.utlx.engine.strategy

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.glomidco.utlx.core.lexer.Lexer
import com.glomidco.utlx.core.parser.ParseResult
import com.glomidco.utlx.core.parser.Parser
import com.glomidco.utlx.engine.config.TransformConfig
import com.glomidco.utlx.engine.strategy.compiled.ASTCompiler
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * **The conformance corpus, run through every execution strategy.**
 *
 * The guide states that "a transformation that works in one executable works identically in all
 * three... the conformance suite validates behavior across executables". It did not. The Python
 * conformance runner shells out to the `utlx` CLI, which executes through `Interpreter`, and the
 * transform command has no strategy option — so the whole 556-test corpus exercised **one**
 * execution path. TEMPLATE is that same path (it wraps `Interpreter`), while **COPY** and
 * **COMPILED** — the latter being what `utlxe` runs and the engine's default — had never seen a
 * single fixture.
 *
 * Three bugs came out of that gap, each fixed in the interpreter while the compiled path silently
 * diverged:
 *
 *  - **B34** `&&`/`||` returned the deciding operand compiled, a coerced boolean interpreted.
 *  - **B35** literals and arithmetic were made exact in the interpreter; COMPILED stayed lossy.
 *  - **EB03** `"5" == 5` was true compiled, false interpreted — seven comparisons disagreed.
 *
 * Each needed a hand-written parity test, and each was found by probing rather than by a suite.
 * This test replaces that discipline with coverage: every future core semantic change is checked
 * against the whole corpus on all three strategies, without anyone having to remember.
 *
 * ### This is a DIFFERENTIAL test, not an expectation test
 *
 * It never reads the fixtures' `expected:` blocks. Whether the output is *correct* is the Python
 * suite's job and is already covered; the question here is only whether the strategies **agree with
 * each other**. That makes the assertion a plain string comparison, which is both stronger for this
 * purpose and immune to the trap that a `format: json` fixture is compared by parsed value — where
 * `100.10` and `100.1` are indistinguishable.
 *
 * ### Why the compiled-coverage count is not optional
 *
 * `CompiledStrategy` and `CopyStrategy` both **fall back to the interpreter without complaint** when
 * `ASTCompiler.compile` returns null. So a parity run can pass while measuring the interpreter three
 * times over and proving nothing — exactly the hazard `B34CompiledShortCircuitTest` records in its
 * own header. [reportCoverage] therefore counts how many fixtures genuinely compile and asserts a
 * floor, so that narrowing `isCompilable` shows up as a failure instead of as silently thinner
 * coverage.
 */
class ConformanceStrategyParityTest {

    /**
     * [path] is the identity, not [name]: three fixtures in the corpus share a `name:` with another
     * file (`length_basic`, `toString_basic`, `transform_auto_43d9da56`), so keying the baseline or
     * the tallies by name would silently conflate them.
     */
    data class Fixture(val name: String, val path: String, val source: String, val payload: String) {
        override fun toString() = path
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("fixtures")
    fun `all strategies agree`(fx: Fixture) {
        val template = runVia("TEMPLATE", fx) { TemplateStrategy() }
        val copy = runVia("COPY", fx) { CopyStrategy() }
        val compiled = runVia("COMPILED", fx) { CompiledStrategy() }

        val divergence = describeDivergence(fx, template, copy, compiled)
        val known = KNOWN_DIVERGENCES[fx.path]

        if (known != null) {
            // A baseline entry that has started agreeing must be removed, or the list rots into a
            // blanket exemption and the next real divergence hides behind it.
            assertTrue(
                divergence != null,
                "${fx.path} is listed as a known divergence (${known.detail}) but the strategies " +
                    "now agree — delete the entry from KNOWN_DIVERGENCES"
            )
            return
        }

        assertTrue(divergence == null, "${fx.path}: $divergence")
    }

    /** Null when all three agree; otherwise a description of how they do not. */
    private fun describeDivergence(
        fx: Fixture,
        template: Result<String>,
        copy: Result<String>,
        compiled: Result<String>
    ): String? {
        // Agreement on failure counts too. Without this, a divergence could hide as "one errored,
        // the other two agreed" — which is itself a divergence.
        val failed = listOf("TEMPLATE" to template, "COPY" to copy, "COMPILED" to compiled)
            .filter { it.second.isFailure }
        if (failed.isNotEmpty()) {
            if (failed.size == 3) return null          // all refuse: consistent
            return "strategies disagree on whether it even runs — failed on " +
                failed.joinToString { it.first } +
                ", first error: ${failed.first().second.exceptionOrNull()?.message}"
        }

        // Report BOTH strategies, not the first one that differs. Returning early here hid
        // COMPILED divergences behind COPY's on any fixture where both were wrong — which is how
        // "COMPILED never produced different output" came to look true when it was not.
        val t = template.getOrThrow()
        val copyDiffers = copy.getOrThrow() != t
        val compiledDiffers = compiled.getOrThrow() != t
        if (!copyDiffers && !compiledDiffers) return null

        val who = when {
            copyDiffers && compiledDiffers -> "COPY and COMPILED"
            copyDiffers -> "COPY"
            else -> "COMPILED"
        }
        return buildString {
            append("$who output differs from TEMPLATE\n  TEMPLATE: ${t.take(240)}")
            if (copyDiffers) append("\n  COPY    : ${copy.getOrThrow().take(240)}")
            if (compiledDiffers) append("\n  COMPILED: ${compiled.getOrThrow().take(240)}")
        }
    }

    private fun runVia(name: String, fx: Fixture, make: () -> ExecutionStrategy): Result<String> {
        // Deliberately no `inputs`: both strategies prefer `transformConfig.inputs.first().name`
        // over the name declared in the %utlx header, so supplying one would override whatever the
        // fixture declares. Leaving it empty lets each strategy use the header, which is what the
        // engine does for a transformation loaded from source.
        val cfg = TransformConfig(strategy = name)
        val strategy = make()
        return runCatching {
            strategy.initialize(fx.source, cfg)
            strategy.execute(fx.payload).output
        }.also { runCatching { strategy.shutdown() } }
    }

    companion object {
        /**
         * Why a fixture is currently allowed to diverge. Each value is a real defect or gap, not an
         * excuse — the list is the inventory of work, and the test's job is to stop it growing.
         */
        enum class Divergence(val detail: String) {
            /** COPY emits different output. The clearest case: XML attributes become child elements. */
            COPY_OUTPUT("COPY produces different output from TEMPLATE"),

            /**
             * A user-defined function (`function f(...) { ... }` / `let f = x => ...` used as a
             * callee) is not visible to the compiled path: `compile()` compiles `program.body` and
             * nothing else, so the call reports `Undefined function`. A missing capability, not a
             * coding error.
             */
            COMPILED_UNDEFINED_FUNCTION("COPY/COMPILED cannot see a user-defined function"),

            /** Runs on TEMPLATE only, for a reason not yet classified. */
            RUNS_ONLY_ON_TEMPLATE("only TEMPLATE can run it"),

            /** COMPILED produces different output while COPY agrees. */
            COMPILED_OUTPUT("COMPILED produces different output from TEMPLATE"),

            /**
             * **Both** compiled strategies produce different output. This is the shared
             * `ASTCompiler` being wrong rather than one strategy's own machinery, so it is the most
             * valuable class to look at: a single fix moves every entry.
             */
            BOTH_OUTPUT("COPY and COMPILED both differ from TEMPLATE"),
        }

        /**
         * Fixtures that do **not** yet agree across strategies, measured rather than assumed.
         *
         * This is a baseline, not an exemption: a fixture that starts diverging fails, and a
         * fixture here that *stops* diverging also fails, so a fix cannot land without the list
         * shrinking. That second direction is what keeps the inventory honest.
         */
        private val KNOWN_DIVERGENCES: Map<String, Divergence> = mapOf(
            "examples/basic/try_catch_basic.yaml" to Divergence.RUNS_ONLY_ON_TEMPLATE,
            "examples/intermediate/function_composition.yaml" to Divergence.COMPILED_UNDEFINED_FUNCTION,
            "examples/intermediate/function_definitions_in_objects.yaml" to Divergence.COMPILED_UNDEFINED_FUNCTION,
            "examples/intermediate/spread_operator.yaml" to Divergence.BOTH_OUTPUT,
            "examples/intermediate/try_catch_with_error_variable.yaml" to Divergence.BOTH_OUTPUT,
            "examples/xml-encoding/multi_input_default_encoding.yaml" to Divergence.BOTH_OUTPUT,
            "examples/xml-encoding/multi_input_explicit_encoding.yaml" to Divergence.BOTH_OUTPUT,
            "examples/xml-encoding/multi_input_no_encoding.yaml" to Divergence.BOTH_OUTPUT,
            "formats/jsch/transformation/generate_typescript_interface.yaml" to Divergence.COMPILED_UNDEFINED_FUNCTION,
            "formats/xsd/transformation/map_to_database_schema.yaml" to Divergence.RUNS_ONLY_ON_TEMPLATE,
            "language/version/version_unsupported.yaml" to Divergence.RUNS_ONLY_ON_TEMPLATE,
            "stdlib/array/nestBy_basic.yaml" to Divergence.BOTH_OUTPUT,
            "tutorial-examples/csv_orders_grouping_pipe.yaml" to Divergence.BOTH_OUTPUT,
            "tutorial-examples/csv_orders_grouping_semicolon.yaml" to Divergence.BOTH_OUTPUT,
            "tutorial-examples/csv_orders_grouping_tab.yaml" to Divergence.BOTH_OUTPUT,
            "tutorial-examples/csv_regional_europe_input.yaml" to Divergence.RUNS_ONLY_ON_TEMPLATE,
            "tutorial-examples/csv_regional_swiss_input.yaml" to Divergence.RUNS_ONLY_ON_TEMPLATE,

        )

        /**
         * Minimum number of fixtures whose transformation must actually compile to bytecode.
         *
         * Set from a measured run. It exists to make a drop visible: if someone narrows
         * `ASTCompiler.isCompilable`, the parity assertions above would all still pass — by
         * exercising the interpreter three times — and only this floor would notice.
         */
        // Lowered from 328 when the four non-deterministic fixtures stopped being driven at
        // all. That is a smaller denominator, not thinner coverage — the floor tracks how
        // many of the DRIVEN fixtures reach bytecode.
        private const val MIN_COMPILED = 325

        /** Fixtures that cannot be driven through `ExecutionStrategy.execute(input: String)` yet. */
        private val SKIP_REASONS = ConcurrentHashMap<String, String>()
        private val compiledOk = ConcurrentHashMap.newKeySet<String>()
        private val compiledFallback = ConcurrentHashMap.newKeySet<String>()

        private val yaml = ObjectMapper(YAMLFactory())
        private val json = ObjectMapper()

        /** Walk up from the test's working directory until the conformance corpus is found. */
        private fun testsRoot(): File? {
            var dir: File? = File(System.getProperty("user.dir")).absoluteFile
            while (dir != null) {
                val candidate = File(dir, "conformance-suite/utlx/tests")
                if (candidate.isDirectory) return candidate
                dir = dir.parentFile
            }
            return null
        }

        /**
         * Smallest corpus size that still means "the corpus was found".
         *
         * The loader locates the fixtures by walking up from the working directory, so a corpus
         * that moved or was not checked out would yield nothing — and a differential test over zero
         * fixtures passes while proving nothing. That is the same vacuity trap the compiled-coverage
         * floor guards against, one level up, so it gets the same treatment: an explicit failure
         * rather than a reliance on JUnit's "no arguments" default.
         */
        private const val MIN_FIXTURES = 355

        @JvmStatic
        fun fixtures(): List<Fixture> {
            val root = requireNotNull(testsRoot()) {
                "conformance corpus not found by walking up from ${System.getProperty("user.dir")} — " +
                    "expected a conformance-suite/utlx/tests directory"
            }
            val out = mutableListOf<Fixture>()

            root.walkTopDown().filter { it.isFile && it.extension == "yaml" }.sorted().forEach { file ->
                val rel = file.relativeTo(root).path
                val parsed = runCatching {
                    @Suppress("UNCHECKED_CAST")
                    yaml.readValue(file, Map::class.java) as Map<String, Any?>
                }
                val doc = parsed.getOrElse { e ->
                    SKIP_REASONS[rel] = "fixture YAML did not parse: " +
                        (e.message?.lineSequence()?.firstOrNull()?.take(90) ?: e::class.simpleName)
                    return@forEach
                }

                val name = (doc["name"] as? String) ?: rel
                val source = doc["transformation"] as? String
                if (source == null) {
                    SKIP_REASONS[rel] = "no transformation block"
                    return@forEach
                }
                if (doc.containsKey("inputs")) {
                    // Multi-input needs the engine's named-slot path; execute() takes one payload.
                    SKIP_REASONS[rel] = "multi-input"
                    return@forEach
                }

                @Suppress("UNCHECKED_CAST")
                val input = doc["input"] as? Map<String, Any?>
                if (input == null) {
                    SKIP_REASONS[rel] = "no input block"
                    return@forEach
                }
                // `data` appears in the corpus both as a `|` string block and as inline YAML.
                val payload = when (val d = input["data"]) {
                    is String -> d
                    null -> { SKIP_REASONS[rel] = "no input data"; return@forEach }
                    else -> runCatching { json.writeValueAsString(d) }.getOrNull()
                        ?: run { SKIP_REASONS[rel] = "input data not serialisable"; return@forEach }
                }

                if (isNondeterministic(source)) {
                    SKIP_REASONS[rel] = "non-deterministic (reads the clock or a random source)"
                    return@forEach
                }

                recordCompilability(rel, source)
                out.add(Fixture(name, rel, source, payload))
            }
            check(out.size >= MIN_FIXTURES) {
                "only ${out.size} fixtures loaded from $root, expected at least $MIN_FIXTURES — " +
                    "the corpus is missing, moved, or the loader stopped understanding it"
            }
            return out
        }

        /**
         * Functions whose result changes between calls, so the same fixture cannot be compared
         * across three separate executions.
         *
         * Grounded in the stdlib registry rather than guessed: `register("now")`,
         * `register("random")`, `register("currentDate")`, `register("currentTime")`.
         */
        private val NONDETERMINISTIC = Regex("""\b(now|random|currentDate|currentTime)\s*\(""")

        /**
         * A differential test runs each fixture once per strategy, so a transformation that reads
         * the clock differs from itself and would be reported as a divergence that is not one.
         *
         * Three fixtures were in the baseline for exactly this reason, which also made the test
         * **flaky in the other direction**: had two executions landed in the same millisecond the
         * outputs would have matched, and the baseline check would then have failed with "now agree
         * — delete the entry". The Python runner handles the same problem by validating that a
         * timestamp is merely *in range*; a strategy comparison cannot do that, so these are
         * skipped and named.
         */
        private fun isNondeterministic(source: String) = NONDETERMINISTIC.containsMatchIn(source)

        /**
         * Did this transformation reach bytecode, or fall back?
         *
         * Asked of `ASTCompiler` directly rather than inferred from logs, matching how the B35 and
         * EB03 parity tests establish the same fact.
         */
        private fun recordCompilability(path: String, source: String) {
            val program = runCatching {
                when (val r = Parser(Lexer(source).tokenize(), source).parse()) {
                    is ParseResult.Success -> r.program
                    is ParseResult.Failure -> null
                }
            }.getOrNull()
            val compiled = program != null && runCatching { ASTCompiler().compile(program) }.getOrNull() != null
            if (compiled) compiledOk.add(path) else compiledFallback.add(path)
        }

        @JvmStatic
        @AfterAll
        fun reportCoverage() {
            val total = compiledOk.size + compiledFallback.size
            if (total == 0) return
            println("─".repeat(78))
            println("conformance corpus x execution strategies")
            println("  fixtures driven       : $total")
            println("  skipped               : ${SKIP_REASONS.size}")
            SKIP_REASONS.values.groupingBy { it }.eachCount().toSortedMap()
                .forEach { (reason, n) -> println("      ${reason.padEnd(28)} $n") }
            println("  known divergences     : ${KNOWN_DIVERGENCES.size}")
            KNOWN_DIVERGENCES.values.groupingBy { it }.eachCount().entries
                .sortedByDescending { it.value }
                .forEach { (d, n) -> println("      ${d.name.padEnd(28)} $n") }
            println("  genuinely COMPILED    : ${compiledOk.size}")
            println("  fell back to interp.  : ${compiledFallback.size}")
            println("─".repeat(78))
            assertTrue(
                compiledOk.size >= MIN_COMPILED,
                "compiled coverage dropped to ${compiledOk.size}, floor is $MIN_COMPILED — " +
                    "the parity assertions would still pass by running the interpreter three times, " +
                    "so this floor is the only thing that notices"
            )
        }
    }
}
