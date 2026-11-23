package com.glomidco.utlx.stdlib

import org.junit.jupiter.api.Test
import kotlin.test.assertTrue

/**
 * A function can **work and be undiscoverable**, and nothing used to notice.
 *
 * `utlx functions` does not reflect over the live registry — it reads a snapshot generated at build
 * time by `:stdlib:generateFunctionRegistry`, which scans `@UTLXFunction` annotations. An annotation
 * binds to the **next declaration**, so inserting a private helper between an annotation and its
 * function silently reassigns it:
 *
 *     @UTLXFunction(…describes round…)
 *     private fun exactOrNull(…)      // ← takes the annotation
 *     fun round(args)                 // ← now has none
 *
 * That happened to `round` during the B67 fix. `round(2.675, 2)` still evaluated correctly, but
 * `utlx functions info round` answered *"Function 'round' not found"* — one of the most-used Math
 * functions, invisible. It was spotted by a human noticing the IDE's function builder and the CLI
 * disagreed, which is not a control.
 *
 * **Why no count caught it:** the annotation was not lost, it was *moved*. The exported total stayed
 * at exactly 705 before and after, with the private helper occupying `round`'s place. Any check
 * comparing totals would have passed.
 *
 * This is a canary, not a complete invariant. The full invariant — every callable name appears in
 * the catalogue — does not hold yet: 51 registered names are uncatalogued for pre-existing reasons
 * (alias spellings such as `geo-distance` beside `geoDistance`, and XPath-style hyphenated names).
 * Asserting that would fail for reasons unrelated to the bug it is meant to catch, so this test
 * names the functions whose absence is self-evidently wrong instead.
 */
class CoreFunctionsAreDiscoverableTest {

    /** Functions nobody would accept as missing from `utlx functions`. */
    private val mustBeDiscoverable = listOf(
        // Math — `round` is the one that actually broke
        "round", "abs", "ceil", "floor", "pow", "sqrt",
        // Array / aggregation
        "map", "filter", "reduce", "sum", "count", "sort", "flatten", "distinct",
        // String
        "upper", "lower", "trim", "split", "replace", "substring", "contains",
        // Type / conversion
        "toNumber", "toString", "isNumber", "isString", "keys", "values",
        // Object
        "merge",
    )

    @Test
    fun `every core function appears in the exported catalogue`() {
        val catalogued = StandardLibrary.exportRegistry().functions.map { it.name }.toSet()
        val missing = mustBeDiscoverable.filterNot { it in catalogued }
        assertTrue(missing.isEmpty(),
            "these work but are not in the catalogue, so `utlx functions info <name>` cannot find " +
            "them — most likely an @UTLXFunction annotation bound to the wrong declaration: $missing")
    }

    @Test
    fun `a private helper never reaches the catalogue`() {
        // The other half of the same bug: when the annotation moved, a PRIVATE helper took round's
        // place in the exported list. A private implementation detail must never be advertised.
        val catalogued = StandardLibrary.exportRegistry().functions.map { it.name }.toSet()
        for (helper in listOf("exactOrNull", "nonFiniteState", "requireArgs", "getTypeDescription")) {
            assertTrue(helper !in catalogued, "'$helper' is a private helper and must not be catalogued")
        }
    }
}
