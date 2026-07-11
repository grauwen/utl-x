package com.glomidco.utlx.bundle

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

/**
 * IF19: direct tests for the safe-segment naming guard — the security boundary that keeps a
 * request name from escaping the bundle root (Bundle Format §3 + the REST-API constraint).
 */
class BundleNamesTest {

    @Test
    fun `accepts ordinary names verbatim, including spaces and leading digits`() {
        assertEquals("order-ack", BundleNames.requireSafeSegment("order-ack"))
        assertEquals("00-enterprise-order", BundleNames.requireTransformationName("00-enterprise-order"))
        assertEquals("order.json", BundleNames.requireSafeSegment("order.json"))
        assertEquals("with space", BundleNames.requireSafeSegment("with space")) // spaces are legal in filenames
    }

    @Test
    fun `rejects path separators`() {
        assertThrows(IllegalArgumentException::class.java) { BundleNames.requireSafeSegment("a/b") }
        assertThrows(IllegalArgumentException::class.java) { BundleNames.requireSafeSegment("a\\b") }
        assertThrows(IllegalArgumentException::class.java) { BundleNames.requireSafeSegment("../evil") }
        assertThrows(IllegalArgumentException::class.java) { BundleNames.requireSafeSegment("nested/../escape") }
    }

    @Test
    fun `rejects the dot and dot-dot segments`() {
        assertThrows(IllegalArgumentException::class.java) { BundleNames.requireSafeSegment(".") }
        assertThrows(IllegalArgumentException::class.java) { BundleNames.requireSafeSegment("..") }
    }

    @Test
    fun `rejects blank names`() {
        assertThrows(IllegalArgumentException::class.java) { BundleNames.requireSafeSegment("") }
        assertThrows(IllegalArgumentException::class.java) { BundleNames.requireSafeSegment("   ") }
    }

    @Test
    fun `rejects a NUL character`() {
        val withNul = "bad" + Char(0) + "name"
        assertThrows(IllegalArgumentException::class.java) { BundleNames.requireSafeSegment(withNul) }
    }
}
