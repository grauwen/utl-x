package com.glomidco.utlx.stdlib

import org.junit.jupiter.api.Test
import kotlin.test.assertTrue
import kotlin.test.assertFalse

/**
 * F21 — `exportRegistry(languageVersion)` is version-aware: the `validate.*` namespace (category
 * "Validation") is hidden from the IDE function list below `%utlx 1.1`. The no-arg / "1.0" call is
 * backward-compatible (base list, no validate); "1.1" includes it. Ungated functions always show.
 */
class ExportRegistryVersionTest {

    private fun hasValidation(reg: FunctionRegistry) = reg.functions.any { it.category == "Validation" }

    @Test fun `default and 1_0 exclude validate (backward compatible)`() {
        assertFalse(hasValidation(StandardLibrary.exportRegistry()), "no-arg must exclude validate.*")
        assertFalse(hasValidation(StandardLibrary.exportRegistry("1.0")), "1.0 must exclude validate.*")
    }

    @Test fun `1_1 includes validate`() {
        assertTrue(hasValidation(StandardLibrary.exportRegistry("1.1")), "1.1 must include validate.*")
    }

    @Test fun `ungated functions appear regardless of version, and 1_1 is a superset`() {
        val base = StandardLibrary.exportRegistry("1.0")
        val full = StandardLibrary.exportRegistry("1.1")
        assertTrue(full.totalFunctions > base.totalFunctions, "1.1 adds validate.* on top of the base")
        assertTrue(base.functions.any { it.category != "Validation" }, "base still has ordinary functions")
    }
}
