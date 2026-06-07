package org.apache.utlx.core.interpreter

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertNotNull

/**
 * Verifies the post-B25 split of responsibilities inside the interpreter:
 *
 *  - StandardLibraryImpl registers ONLY the functions that must run on the interpreter itself:
 *    the higher-order builtins (map/filter/reduce/find/findIndex), which evaluate UTL-X lambda AST,
 *    and first/last, which return *input elements* (routing them through the canonical stdlib lookup
 *    would round-trip the element through UDM<->RuntimeValue and lose Int/XML fidelity).
 *
 *  - Every pure value function (upper, abs, sum, round, parseNumber, ...) was removed from
 *    StandardLibraryImpl so the single canonical implementation in the `stdlib` module — reached via
 *    the reflection-free lookup (Interpreter.setStdlibLookup) — is the only source of truth. A bare
 *    interpreter with no lookup configured must therefore NOT know those names.
 */
class StandardLibraryIntegrationTest {

    @Test
    fun `interpreter-owned higher-order and element functions are registered`() {
        val env = Environment()
        StandardLibraryImpl().registerAll(env)

        listOf("map", "filter", "reduce", "find", "findIndex", "first", "last").forEach { name ->
            assertNotNull(env.get(name), "$name should be registered by StandardLibraryImpl")
        }
    }

    @Test
    fun `pure value functions are no longer duplicated in the interpreter`() {
        val env = Environment()
        StandardLibraryImpl().registerAll(env)

        // These now resolve exclusively through the canonical stdlib lookup, so on a bare env
        // (no lookup set) they are undefined — get() throws "Undefined variable".
        listOf("upper", "lower", "trim", "sum", "count", "abs", "round", "toString", "parseNumber")
            .forEach { name ->
                assertFails("$name should no longer be registered directly by StandardLibraryImpl") {
                    env.get(name)
                }
            }
    }

    @Test
    fun `first and last return input elements`() {
        val env = Environment()
        StandardLibraryImpl().registerAll(env)

        val arr = RuntimeValue.ArrayValue(
            listOf(
                RuntimeValue.NumberValue(1.0),
                RuntimeValue.NumberValue(2.0),
                RuntimeValue.NumberValue(3.0)
            )
        )
        val first = StandardLibraryImpl.nativeFunctions["first"]!!(listOf(arr))
        assertEquals(1.0, (first as RuntimeValue.NumberValue).value)
        val last = StandardLibraryImpl.nativeFunctions["last"]!!(listOf(arr))
        assertEquals(3.0, (last as RuntimeValue.NumberValue).value)
    }
}
