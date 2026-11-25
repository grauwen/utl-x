package com.glomidco.utlx.stdlib

import com.glomidco.utlx.core.lexer.Lexer
import com.glomidco.utlx.core.parser.ParseResult
import com.glomidco.utlx.core.parser.Parser
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * UTL-X keeps **two independent lists of its own functions**, and nothing used to compare them.
 *
 * `register("name", Obj::method)` is what makes a name callable. `utlx functions` reads something
 * else entirely: a snapshot generated at build time by `:stdlib:generateFunctionRegistry`, which
 * reflects over `@UTLXFunction` annotations. Nothing tied the two together, so they drifted — and
 * drifted in **both** directions, which is why this test asserts two invariants rather than one.
 *
 * **Direction 1 — callable but unadvertised (B67's regression, then B68).** An annotation binds to
 * the *next* declaration, so inserting a private helper between an annotation and its function
 * silently reassigns it:
 *
 *     @UTLXFunction(…describes round…)
 *     private fun exactOrNull(…)      // ← takes the annotation
 *     fun round(args)                 // ← now has none
 *
 * `round(2.675, 2)` still evaluated correctly while `utlx functions info round` answered
 * *"Function 'round' not found"*. No count caught it because the annotation was not lost but
 * *moved*: the exported total stayed at exactly 705 either way. B68 then found 36 more in that
 * state — four whole objects in `AdvancedUtilities.kt` carried no annotations at all.
 *
 * **Direction 2 — advertised but not callable (B68's mirror defect).** The exporter published
 * `member.name`, the *Kotlin method* name, so wherever the registered spelling differed the
 * catalogue named something that does not exist: it listed `createQName`, while the callable name
 * is `createQname` and `createQName` gave *"Undefined function"*. Eleven were like that, including
 * three — `getURLPath`, `logBase`, `reverseString` — where the method name collided with a
 * *different* function of the same name in another object (`URLFunctions::getPath` vs
 * `CriticalObjectFunctions::getPath`), so the catalogue showed one name twice and the other never.
 * `@UTLXFunction(name = …)` now carries the registered spelling, because the registered name is
 * the only one a user can type.
 *
 * Both directions are now empty, so this test states the real invariant instead of a sample. It is
 * worth preferring over any count: a count is exactly what both bugs walked straight past.
 */
class CoreFunctionsAreDiscoverableTest {

    /**
     * `validate.*` is the `%utlx 1.1` namespace and is version-gated out of the default catalogue
     * (F21). It is also catalogued under the **bare** method name — "assert", not "validate.assert"
     * — so it cannot participate in a name-for-name comparison at any version. Excluded on both
     * sides rather than special-cased per name.
     */
    private fun isVersionGated(name: String) = name.startsWith("validate.")

    /**
     * The BUILT-IN names, not the live map. `registerFunction` is a public runtime extension
     * point, and `FunctionsTest` uses it — so by the time this class runs in a full suite the live
     * map may also hold `customTest`, which the build-time catalogue could not possibly contain.
     * Comparing against the live map made this test pass alone and fail in the suite, which is the
     * worst of both. The catalogue only ever holds built-ins, so that is what it is compared to.
     */
    private val callable = StandardLibrary.getBuiltInFunctionNames().filterNot(::isVersionGated).toSet()
    private val catalogue = StandardLibrary.exportRegistry().functions.map { it.name }

    @Test
    fun `every callable function appears in the catalogue`() {
        val missing = (callable - catalogue.toSet()).sorted()
        assertTrue(missing.isEmpty(),
            "these ${missing.size} functions work but `utlx functions info <name>` cannot find them. " +
            "Usually an @UTLXFunction annotation bound to the wrong declaration (a helper inserted " +
            "between annotation and function), or a registered object with no annotations at all: $missing")
    }

    @Test
    fun `every catalogued function is callable`() {
        val phantom = (catalogue.toSet() - callable).sorted()
        assertTrue(phantom.isEmpty(),
            "the catalogue advertises these ${phantom.size} names but calling one gives " +
            "\"Undefined function\". Usually the Kotlin method name differs from the registered " +
            "name — give the annotation an explicit `name = \"<registered spelling>\"`: $phantom")
    }

    @Test
    fun `a private helper never reaches the catalogue`() {
        // The other half of direction 1: when round's annotation moved, a PRIVATE helper took its
        // place in the exported list. A private implementation detail must never be advertised.
        for (helper in listOf("exactOrNull", "nonFiniteState", "requireArgs", "getTypeDescription")) {
            assertTrue(helper !in catalogue, "'$helper' is a private helper and must not be catalogued")
        }
    }

    @Test
    fun `every catalogued name can actually be called in a script`() {
        // Registration is NOT callability, and conflating the two is how `if` slipped through.
        // `register("if", CoreFunctions::ifThenElse)` put `if` in the live function map, so the
        // two tests above were satisfied — but `if` is a lexer keyword whose if-expression rule
        // the parser claims first, so `if(c, a, b)` dies with "Expected ')' after condition".
        // It had been dead for its whole life and the catalogue advertised it anyway.
        //
        // A keyword blacklist would be the wrong check: `map`, `filter` and `reduce` are keywords
        // too and parse perfectly well as calls (`map($input.items, i => i.p)` is in every demo).
        // Only the parser can answer this, so ask the parser.
        val unparseable = catalogue.distinct().filterNot { name ->
            val src = "%utlx 1.0\ninput json\noutput json\n---\n$name(1)"
            runCatching { Parser(Lexer(src).tokenize(), src).parse() is ParseResult.Success }
                .getOrDefault(false)
        }
        assertTrue(unparseable.isEmpty(),
            "the catalogue advertises these ${unparseable.size} names, but a script calling one " +
            "does not parse — the name collides with language syntax, so it can never be called " +
            "no matter what it is registered as: $unparseable")
    }

    @Test
    fun `no new ambiguous catalogue entries`() {
        // B69: nine names are catalogued more than once, because two objects annotate the same
        // method name while only one of them is registered under it (plus `hasKey`, where a
        // registerAlias collides with an annotated method). `utlx functions info concat` therefore
        // returns three different signatures, only one of which describes what actually runs.
        //
        // Fixing those is B69, not B68: four of them (findIndex, parseDate, parseInt, distinctBy)
        // are registered TWICE from different objects, so the choice of which to keep changes
        // behaviour. Ratcheted here so the number cannot grow in the meantime.
        // `concat` left this set in 1.4.5 (B71): it was three entries for one polymorphic
        // function, and splitting it gave the array form its own name, `arrayConcat`.
        val known = setOf("contains", "distinctBy", "findIndex", "hasKey",
                          "isEmpty", "parseDate", "parseInt", "titleCase")
        val duplicated = catalogue.groupingBy { it }.eachCount().filterValues { it > 1 }.keys
        assertEquals(known, duplicated,
            "the set of ambiguous catalogue entries changed. New ones are a regression; if you " +
            "FIXED one, remove it from `known` here and from B69.")
    }
}
