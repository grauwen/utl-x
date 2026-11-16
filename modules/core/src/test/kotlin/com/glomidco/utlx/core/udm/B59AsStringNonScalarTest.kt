package com.glomidco.utlx.core.udm

import com.glomidco.utlx.core.FunctionArgumentException
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * B59 — **`UDM.asString()` leaked the engine's internal representation into user data.**
 *
 * The non-scalar fallback was `this.toString()`, i.e. the Kotlin data-class rendering:
 *
 * ```
 * upperCase($input)  =>  "OBJECT(PROPERTIES={A=SCALAR(VALUE=1)}, ATTRIBUTES={}, NAME=NULL, ...)"
 * ```
 *
 * A downstream system received that as if it were a value. Three dozen stdlib files had written
 * their own strict `private fun UDM.asString()` to prevent exactly this — and every one was dead,
 * because in Kotlin a **member always wins over an extension**. The guards existed, were correct,
 * and never ran.
 *
 * Fixing it in those 36 files was never necessary: all ~289 call sites funnel through this one
 * member, so the guard belongs here.
 *
 * ### Two things this test pins
 *
 * 1. **Temporal values gain a text form rather than an error.** `DateTime`/`Date`/`LocalDateTime`/
 *    `Time` carry `toISOString()` but do **not** override `toString()`, so they hit the same
 *    broken fallback — `upperCase(now())` returned `"DATETIME(INSTANT=…)"`. Erroring on them
 *    would have been a regression dressed as a fix; they now render ISO.
 * 2. **Everything else fails loudly**, with [FunctionArgumentException] — the type the stdlib's own
 *    argument errors already use, so callers that catch it keep working. That choice is why the
 *    change cost zero test adjustments across 4931 tests.
 */
class B59AsStringNonScalarTest {

    // ── the leak ──

    @Test
    fun `an object no longer stringifies to its internal representation`() {
        val obj = UDM.Object(mapOf("a" to UDM.Scalar(1)))
        val e = assertFailsWith<FunctionArgumentException> { obj.asString() }
        assertTrue(
            "OBJECT(" !in e.message!!.uppercase() && "PROPERTIES=" !in e.message!!.uppercase(),
            "the message must not contain the dump it replaced: ${e.message}"
        )
        assertTrue(e.message!!.contains("object"), "it should name the kind: ${e.message}")
    }

    @Test
    fun `an array fails the same way`() {
        val e = assertFailsWith<FunctionArgumentException> {
            UDM.Array(listOf(UDM.Scalar(1))).asString()
        }
        assertTrue(e.message!!.contains("array"), "it should name the kind: ${e.message}")
    }

    @Test
    fun `the message offers a way forward`() {
        val e = assertFailsWith<FunctionArgumentException> {
            UDM.Object(mapOf("a" to UDM.Scalar(1))).asString()
        }
        // Both suggestions are verified to work on an object; a hint that does not is worse
        // than none (see CB06, where a hint promised relief the profile could not deliver).
        assertTrue(e.message!!.contains("toString()"), "suggest toString(): ${e.message}")
        assertTrue(e.message!!.contains("renderJson()"), "and renderJson(): ${e.message}")
    }

    @Test
    fun `a named object mentions its element name, which is what the author sees`() {
        val obj = UDM.Object(mapOf("a" to UDM.Scalar(1)), name = "Order")
        val e = assertFailsWith<FunctionArgumentException> { obj.asString() }
        assertTrue(e.message!!.contains("Order"), "a named element should be identifiable: ${e.message}")
    }

    // ── temporal values: a text form exists, so give it ──

    @Test
    fun `a DateTime renders as ISO rather than failing`() {
        assertEquals(
            "2026-01-15T10:30:00Z",
            UDM.DateTime(Instant.parse("2026-01-15T10:30:00Z")).asString()
        )
    }

    @Test
    fun `a Date renders as ISO`() {
        assertEquals("2026-01-15", UDM.Date(LocalDate.of(2026, 1, 15)).asString())
    }

    @Test
    fun `a Time renders as ISO`() {
        assertEquals("14:30", UDM.Time(LocalTime.of(14, 30)).asString())
    }

    // ── unchanged behaviour ──

    @Test
    fun `scalars are untouched`() {
        assertEquals("hi", UDM.Scalar("hi").asString())
        assertEquals("42", UDM.Scalar(42).asString())
        assertEquals("true", UDM.Scalar(true).asString())
        assertEquals("", UDM.Scalar(null).asString())
    }

    @Test
    fun `an XML text node still unwraps to its text`() {
        // asString() unwraps a `_text`-only object first; that path must keep working, since it
        // is how every XML leaf value is read.
        val leaf = UDM.Object(mapOf("_text" to UDM.Scalar("Alice")))
        assertEquals("Alice", leaf.asString())
    }

    @Test
    fun `B35 exact numeric rendering still applies`() {
        // A preserved decimal must not stringify through Double and lose its scale.
        assertEquals("10.50", UDM.Scalar(java.math.BigDecimal("10.50")).asString())
    }
}
