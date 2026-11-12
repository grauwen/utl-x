package com.glomidco.utlx.stdlib

import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * B55 — **every name a `@UTLXFunction` example calls must be callable.**
 *
 * 41 annotations documented their function under a name it was never registered under —
 * `example = "base64-encode(\"Hello World\")"` while the registration is `base64Encode` — so every
 * one of those examples failed if a user copied it, on `main` as well as `development`. A further
 * 35 `requireArgs(...)` labels named the same dead spellings, so an arity error told the user a
 * function name that does not exist.
 *
 * ### Why nothing caught it
 *
 * The annotation's prose fields are free text that nothing executes. More specifically: the
 * generated catalogue **does not carry `example` at all** — 0 of its 704 entries have the field —
 * so the examples reach no artifact that is ever checked, and the build cannot notice. They exist
 * only in the Kotlin sources, which is why this test reads the sources rather than the catalogue.
 *
 * Same family as [B50JoinNamingTest]'s subject: B50 found the `notes` field quoting a name the
 * function no longer had, and audited `notes` across all 703 annotations — but not `example`,
 * which is how these survived that sweep.
 *
 * ### The assertion is against the registry, not a list
 *
 * As in `B52ValidateFunctionNamesTest`: a hand-written list of expected names drifts, and the
 * drift is the defect. `StandardLibrary.getAllFunctionNames()` is the same set the interpreter
 * dispatches on, so a name that passes here is a name that runs.
 */
class B55AnnotationExampleNamesTest {

    /** Walk up from the test's working directory to the stdlib sources. */
    private fun stdlibSources(): File? {
        var dir: File? = File(System.getProperty("user.dir")).absoluteFile
        while (dir != null) {
            for (candidate in listOf(File(dir, "src/main/kotlin"), File(dir, "stdlib/src/main/kotlin"))) {
                if (candidate.isDirectory && candidate.path.contains("stdlib")) return candidate
            }
            dir = dir.parentFile
        }
        return null
    }

    /**
     * Blank out `/* … */` blocks, preserving length and line structure.
     *
     * Not cosmetic: `UUIDFunctions.kt` carries an instructional block comment holding a dozen
     * `register(...)` lines that were never wired up (`generate-uuid`, seven UUID-v7 names). Count
     * those as registrations and this test certifies dead spellings as valid.
     */
    private fun maskBlockComments(src: String): String =
        Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL)
            .replace(src) { m -> m.value.map { if (it == '\n') '\n' else ' ' }.joinToString("") }

    /**
     * No exemptions, deliberately.
     *
     * Two annotations could not simply be corrected, because they sat on **unreachable** functions
     * whose camelCase name was registered to a *different* implementation — relabelling them would
     * have made dead code look correct and claim a live function's name:
     *
     * - `CoreFunctions.generateUuid`, the original v4 generator, orphaned when `UUIDFunctions` was
     *   added for v7 and `register("generateUuid", …)` was re-pointed at `generateUuidV4`.
     * - `ExtendedMathFunctions.parseFloat`, superseded by B25 in favour of
     *   `ConversionFunctions::parseFloat`, because the old `str.toDouble()` threw on grouping
     *   separators.
     *
     * Both functions were removed instead, each leaving a comment in place of the deleted code
     * recording where the name went and how the behaviour differs. So this test needs no baseline:
     * every example in the tree must name something callable. Keep it that way — an exemption list
     * here would be a place for the next one to hide.
     */
    private val callee = Regex("""^\s*([A-Za-z_][A-Za-z0-9_-]*)\s*\(""")
    private val exampleField = Regex("""example\s*=\s*"((?:[^"\\]|\\.)*)"""")
    private val requireArgsLabel = Regex("""requireArgs\([^,]+,\s*\d+\s*,\s*"([^"]+)"""")

    private fun sources(): List<Pair<String, String>> {
        val root = requireNotNull(stdlibSources()) {
            "stdlib sources not found by walking up from ${System.getProperty("user.dir")}"
        }
        return root.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .map { it.name to maskBlockComments(it.readText()) }
            .toList()
    }

    @Test
    fun `every example calls a registered function`() {
        val known = StandardLibrary.getAllFunctionNames()
        assertTrue(known.size > 500, "expected a populated registry, got ${known.size}")

        val offenders = mutableListOf<String>()
        var checked = 0
        for ((name, src) in sources()) {
            for (m in exampleField.findAll(src)) {
                val c = callee.find(m.groupValues[1])?.groupValues?.get(1) ?: continue
                checked++
                if (c !in known) offenders.add("$name: example = \"${m.groupValues[1].take(48)}\" → '$c'")
            }
        }
        // Vacuity guard: the sources are located by walking up, so a moved tree would yield
        // nothing and this test would pass while proving nothing.
        assertTrue(checked > 250, "expected to have checked most annotations, got $checked")
        assertEquals(emptyList(), offenders,
            "$checked examples checked; these name a function that is not registered, so copying " +
                "the example gives 'Undefined function'")
    }

    @Test
    fun `every requireArgs label is a registered function name`() {
        // The label is what an arity error prints. A dead spelling here sends the user looking for
        // a function that does not exist, which is worse than a bare message.
        val known = StandardLibrary.getAllFunctionNames()
        val offenders = mutableListOf<String>()
        var checked = 0
        for ((name, src) in sources()) {
            for (m in requireArgsLabel.findAll(src)) {
                val label = m.groupValues[1]
                // Some labels describe an operation rather than a function; only a label that
                // *looks* like a function name is held to this rule.
                if (!Regex("""^[a-z][A-Za-z0-9_-]*$""").matches(label)) continue
                checked++
                if (label !in known) offenders.add("$name: requireArgs(…, \"$label\")")
            }
        }
        assertTrue(checked > 100, "expected to have checked many labels, got $checked")
        assertEquals(emptyList(), offenders,
            "$checked labels checked; these name a function that is not registered")
    }

    @Test
    fun `no annotation example uses a kebab-case callee`() {
        // The 41 were all one scheme: an XPath-style naming convention that was planned, used in
        // documentation, and never registered. Zero of the 728 registered names contain a hyphen,
        // so a kebab callee is always wrong — pinned separately from the registry check so the
        // reason stays legible if the registry ever gains one.
        val kebab = mutableListOf<String>()
        for ((name, src) in sources()) {
            for (m in exampleField.findAll(src)) {
                val c = callee.find(m.groupValues[1])?.groupValues?.get(1) ?: continue
                if ("-" in c) kebab.add("$name: '$c'")
            }
        }
        assertEquals(emptyList(), kebab,
            "kebab-case callees in examples; stdlib functions are camelCase")
    }
}
