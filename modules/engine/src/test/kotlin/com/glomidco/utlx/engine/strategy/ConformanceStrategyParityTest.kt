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
 *  - **B36** `"5" == 5` was true compiled, false interpreted — seven comparisons disagreed.
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

        val t = template.getOrThrow()
        if (copy.getOrThrow() != t) {
            return "COPY output differs from TEMPLATE\n  TEMPLATE: ${t.take(300)}\n" +
                "  COPY    : ${copy.getOrThrow().take(300)}"
        }
        if (compiled.getOrThrow() != t) {
            return "COMPILED output differs from TEMPLATE\n  TEMPLATE: ${t.take(300)}\n" +
                "  COMPILED: ${compiled.getOrThrow().take(300)}"
        }
        return null
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
             * COPY and COMPILED cannot run it at all, because they parse input with
             * `parseInputPublic`, which does not reproduce what the full `transform()` pipeline
             * does. Concentrated in the schema-describing formats (xsd, protobuf, jsch, avro,
             * osch), whose UDM carries synthetic `%types` / `%namespace` keys.
             */
            COMPILED_INPUT_SHAPE("COPY/COMPILED see a different input UDM and fail on null"),

            /** `ASTCompiler` emits two methods called `lambda_0` — invalid bytecode. */
            ASTCOMPILER_DUPLICATE_LAMBDA("ASTCompiler: duplicate lambda method name"),

            /**
             * The compiled path cannot resolve a name declared **outside** the expression it is
             * compiling — a user-defined function, or a `let` bound in an enclosing scope.
             * `compileIdentifier` treats any unbound name as a named input, so the lookup returns
             * null and the generated code then dies in a non-null parameter check
             * (`RuntimeOps.getIndex, parameter target`) or reports `Undefined function`.
             *
             * This is a missing capability rather than bad addressing (contrast B40), and the right
             * first move is for `isCompilable` to **reject** it so the strategy falls back cleanly
             * instead of emitting code that throws.
             */
            COMPILED_UNRESOLVED_NAME("COPY/COMPILED cannot resolve an out-of-scope name"),

            /** Runs on TEMPLATE only, for a reason not yet classified. */
            RUNS_ONLY_ON_TEMPLATE("only TEMPLATE can run it"),

            /** COMPILED produces different output. **Currently empty** — and worth keeping so. */
            COMPILED_OUTPUT("COMPILED produces different output from TEMPLATE"),
        }

        /**
         * Fixtures that do **not** yet agree across strategies, measured rather than assumed.
         *
         * This is a baseline, not an exemption: a fixture that starts diverging fails, and a
         * fixture here that *stops* diverging also fails, so a fix cannot land without the list
         * shrinking. That second direction is what keeps the inventory honest.
         */
        private val KNOWN_DIVERGENCES: Map<String, Divergence> = mapOf(
            // B40 reclassified these. The Map-interface crash is gone from all seven; one agreed
            // outright (stdlib/array/lookupBy_basic, delisted) and the rest were masking two
            // other problems.
            "formats/jsch/transformation/generate_api_documentation.yaml" to Divergence.COPY_OUTPUT,
            "formats/jsch/transformation/generate_validation_rules.yaml" to Divergence.COPY_OUTPUT,
            "formats/jsch/transformation/generate_typescript_interface.yaml" to Divergence.COMPILED_UNRESOLVED_NAME,
            "stdlib/array/groupBy_lookup_basic.yaml" to Divergence.COMPILED_UNRESOLVED_NAME,
            "stdlib/array/groupBy_lookup_missing_key.yaml" to Divergence.COMPILED_UNRESOLVED_NAME,
            "stdlib/array/groupBy_lookup_with_transform.yaml" to Divergence.COMPILED_UNRESOLVED_NAME,
            // B39 reclassified these: the duplicate-lambda fix let them compile, and what was
            // hiding behind that failure is now visible. Three others agreed outright and are gone.
            "tutorial-examples/csv_orders_grouping_pipe.yaml" to Divergence.COPY_OUTPUT,
            "tutorial-examples/csv_orders_grouping_semicolon.yaml" to Divergence.COPY_OUTPUT,
            "tutorial-examples/csv_orders_grouping_tab.yaml" to Divergence.COPY_OUTPUT,
            "auto-captured/json-to-xml/transform_auto_b900b260.yaml" to Divergence.COPY_OUTPUT,
            "auto-captured/stdlib/array/map_auto_856bfe76.yaml" to Divergence.COPY_OUTPUT,
            "auto-captured/stdlib/array/sum_auto_a4eced76.yaml" to Divergence.COPY_OUTPUT,
            "edge-cases/array_index_bounds.yaml" to Divergence.RUNS_ONLY_ON_TEMPLATE,
            "examples/basic/json_to_xml_conversion.yaml" to Divergence.COPY_OUTPUT,
            "examples/basic/try_catch_basic.yaml" to Divergence.RUNS_ONLY_ON_TEMPLATE,
            "examples/intermediate/function_composition.yaml" to Divergence.RUNS_ONLY_ON_TEMPLATE,
            "examples/intermediate/function_definitions_in_objects.yaml" to Divergence.RUNS_ONLY_ON_TEMPLATE,
            "examples/intermediate/spread_operator.yaml" to Divergence.COPY_OUTPUT,
            "examples/intermediate/try_catch_with_error_variable.yaml" to Divergence.COPY_OUTPUT,
            "examples/real-world/sap_integration.yaml" to Divergence.COPY_OUTPUT,
            "examples/xml-encoding/encoding_precedence_rules.yaml" to Divergence.COPY_OUTPUT,
            "examples/xml-encoding/multi_input_default_encoding.yaml" to Divergence.COPY_OUTPUT,
            "examples/xml-encoding/multi_input_explicit_encoding.yaml" to Divergence.COPY_OUTPUT,
            "examples/xml-encoding/multi_input_no_encoding.yaml" to Divergence.COPY_OUTPUT,
            "formats/avro/basic/f08_avro_to_proto_passthrough.yaml" to Divergence.COMPILED_INPUT_SHAPE,
            "formats/avro/basic/f08_usdl_dual_access.yaml" to Divergence.COMPILED_INPUT_SHAPE,
            "formats/avro/basic/round_trip_basic.yaml" to Divergence.COMPILED_INPUT_SHAPE,
            "formats/avro/basic/round_trip_inverse.yaml" to Divergence.COMPILED_INPUT_SHAPE,
            "formats/avro/real-world/api_schema.yaml" to Divergence.COMPILED_INPUT_SHAPE,
            "formats/avro/real-world/order_system.yaml" to Divergence.COMPILED_INPUT_SHAPE,
            "formats/jsch/basic/f08_usdl_dual_access.yaml" to Divergence.COMPILED_INPUT_SHAPE,
            "formats/jsch/basic/parse_2020_12.yaml" to Divergence.COMPILED_INPUT_SHAPE,
            "formats/jsch/basic/parse_draft_07.yaml" to Divergence.COPY_OUTPUT,
            "formats/jsch/basic/round_trip_basic.yaml" to Divergence.COMPILED_INPUT_SHAPE,
            "formats/jsch/basic/round_trip_inverse.yaml" to Divergence.COMPILED_INPUT_SHAPE,
            "formats/jsch/composition/ref_definitions.yaml" to Divergence.COPY_OUTPUT,
            "formats/jsch/real-world/api_user_profile.yaml" to Divergence.COPY_OUTPUT,
            "formats/jsch/real-world/customer_order.yaml" to Divergence.COPY_OUTPUT,
            "formats/jsch/real-world/payment_transaction.yaml" to Divergence.COPY_OUTPUT,
            "formats/osch/basic/f08_usdl_dual_access.yaml" to Divergence.COMPILED_INPUT_SHAPE,
            "formats/protobuf/basic/all_primitive_types.yaml" to Divergence.COMPILED_INPUT_SHAPE,
            "formats/protobuf/basic/f08_usdl_dual_access.yaml" to Divergence.COMPILED_INPUT_SHAPE,
            "formats/protobuf/basic/parse_basic_message.yaml" to Divergence.COMPILED_INPUT_SHAPE,
            "formats/protobuf/basic/parse_enum.yaml" to Divergence.COMPILED_INPUT_SHAPE,
            "formats/protobuf/basic/round_trip_basic.yaml" to Divergence.COMPILED_INPUT_SHAPE,
            "formats/protobuf/basic/round_trip_proto_to_usdl.yaml" to Divergence.COMPILED_INPUT_SHAPE,
            "formats/protobuf/complex-types/map_fields.yaml" to Divergence.COMPILED_INPUT_SHAPE,
            "formats/protobuf/complex-types/nested_messages.yaml" to Divergence.COMPILED_INPUT_SHAPE,
            "formats/protobuf/complex-types/oneof_fields.yaml" to Divergence.COMPILED_INPUT_SHAPE,
            "formats/protobuf/complex-types/repeated_fields.yaml" to Divergence.COMPILED_INPUT_SHAPE,
            "formats/protobuf/complex-types/reserved_fields.yaml" to Divergence.COMPILED_INPUT_SHAPE,
            "formats/protobuf/real-world/ecommerce_order.yaml" to Divergence.COMPILED_INPUT_SHAPE,
            "formats/protobuf/real-world/iot_telemetry.yaml" to Divergence.COMPILED_INPUT_SHAPE,
            "formats/protobuf/real-world/user_profile.yaml" to Divergence.COMPILED_INPUT_SHAPE,
            "formats/protobuf/validation/enum_first_value_zero.yaml" to Divergence.COPY_OUTPUT,
            "formats/protobuf/validation/field_number_ranges.yaml" to Divergence.COMPILED_INPUT_SHAPE,
            "formats/protobuf/validation/multi_type_schema.yaml" to Divergence.COMPILED_INPUT_SHAPE,
            "formats/protobuf/validation/package_namespace.yaml" to Divergence.COPY_OUTPUT,
            "formats/tsch/basic/f08_usdl_dual_access.yaml" to Divergence.COPY_OUTPUT,
            "formats/xml/attributes/attribute_from_expression.yaml" to Divergence.COPY_OUTPUT,
            "formats/xml/attributes/attribute_special_chars.yaml" to Divergence.COPY_OUTPUT,
            "formats/xml/attributes/attribute_with_text.yaml" to Divergence.COPY_OUTPUT,
            "formats/xml/attributes/attributes_with_nested_elements.yaml" to Divergence.COPY_OUTPUT,
            "formats/xml/attributes/multiple_attributes.yaml" to Divergence.COPY_OUTPUT,
            "formats/xml/attributes/non_string_attribute_values.yaml" to Divergence.COPY_OUTPUT,
            "formats/xml/attributes/single_attribute_output.yaml" to Divergence.COPY_OUTPUT,
            "formats/xml/attributes/xml_to_xml_attribute_transformation.yaml" to Divergence.COPY_OUTPUT,
            "formats/xml/f09_hash_text_alias.yaml" to Divergence.COPY_OUTPUT,
            "formats/xsd/basic/f08_roundtrip_xsd_jsch_xsd.yaml" to Divergence.COMPILED_INPUT_SHAPE,
            "formats/xsd/basic/f08_usdl_dual_access.yaml" to Divergence.COPY_OUTPUT,
            "formats/xsd/basic/f10_decimal_roundtrip.yaml" to Divergence.COPY_OUTPUT,
            "formats/xsd/basic/parse_xsd_1_0.yaml" to Divergence.COPY_OUTPUT,
            "formats/xsd/basic/parse_xsd_1_1.yaml" to Divergence.COPY_OUTPUT,
            "formats/xsd/basic/round_trip_basic.yaml" to Divergence.COMPILED_INPUT_SHAPE,
            "formats/xsd/features/annotations.yaml" to Divergence.COMPILED_INPUT_SHAPE,
            "formats/xsd/patterns/detect_russian_doll.yaml" to Divergence.COPY_OUTPUT,
            "formats/xsd/patterns/detect_salami_slice.yaml" to Divergence.COPY_OUTPUT,
            "formats/xsd/patterns/detect_swiss_army_knife.yaml" to Divergence.COPY_OUTPUT,
            "formats/xsd/patterns/detect_venetian_blind.yaml" to Divergence.COPY_OUTPUT,
            "formats/xsd/patterns/pattern_preservation_russian_doll.yaml" to Divergence.COMPILED_INPUT_SHAPE,
            "formats/xsd/patterns/russian_doll.yaml" to Divergence.COMPILED_INPUT_SHAPE,
            "formats/xsd/patterns/russian_doll_convert.yaml" to Divergence.COMPILED_INPUT_SHAPE,
            "formats/xsd/patterns/russian_doll_round_trip.yaml" to Divergence.COMPILED_INPUT_SHAPE,
            "formats/xsd/patterns/venetian_blind.yaml" to Divergence.COPY_OUTPUT,
            "formats/xsd/real-world/ecommerce_product_catalog.yaml" to Divergence.COPY_OUTPUT,
            "formats/xsd/real-world/financial_invoice.yaml" to Divergence.COPY_OUTPUT,
            "formats/xsd/real-world/hr_employee_schema.yaml" to Divergence.COPY_OUTPUT,
            "formats/xsd/scope/global_elements.yaml" to Divergence.COPY_OUTPUT,
            "formats/xsd/scope/global_types.yaml" to Divergence.COPY_OUTPUT,
            "formats/xsd/transformation/extract_element_names.yaml" to Divergence.COMPILED_INPUT_SHAPE,
            "formats/xsd/transformation/generate_field_documentation.yaml" to Divergence.COMPILED_INPUT_SHAPE,
            "formats/xsd/transformation/map_to_database_schema.yaml" to Divergence.COMPILED_INPUT_SHAPE,
            "language/version/version_unsupported.yaml" to Divergence.RUNS_ONLY_ON_TEMPLATE,
            "stdlib/array/nestBy_basic.yaml" to Divergence.COPY_OUTPUT,
            "stdlib/datetime/now_basic.yaml" to Divergence.COPY_OUTPUT,
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
        private const val MIN_COMPILED = 328

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
         * Did this transformation reach bytecode, or fall back?
         *
         * Asked of `ASTCompiler` directly rather than inferred from logs, matching how the B35 and
         * B36 parity tests establish the same fact.
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
