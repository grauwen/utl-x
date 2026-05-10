package org.apache.utlx.engine.config

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MaxInputSizeTest {

    @Test
    fun `parse null returns null`() {
        assertNull(parseSizeToBytes(null))
    }

    @Test
    fun `parse empty returns null`() {
        assertNull(parseSizeToBytes(""))
        assertNull(parseSizeToBytes("  "))
    }

    @Test
    fun `parse bytes`() {
        assertEquals(1024L, parseSizeToBytes("1024"))
        assertEquals(1024L, parseSizeToBytes("1024B"))
    }

    @Test
    fun `parse KB`() {
        assertEquals(1024L, parseSizeToBytes("1KB"))
        assertEquals(102400L, parseSizeToBytes("100KB"))
    }

    @Test
    fun `parse MB`() {
        assertEquals(5 * 1024 * 1024L, parseSizeToBytes("5MB"))
        assertEquals(10 * 1024 * 1024L, parseSizeToBytes("10MB"))
        assertEquals(25 * 1024 * 1024L, parseSizeToBytes("25MB"))
    }

    @Test
    fun `parse GB`() {
        assertEquals(1024L * 1024 * 1024, parseSizeToBytes("1GB"))
    }

    @Test
    fun `parse case insensitive`() {
        assertEquals(5 * 1024 * 1024L, parseSizeToBytes("5mb"))
        assertEquals(5 * 1024 * 1024L, parseSizeToBytes("5Mb"))
        assertEquals(100 * 1024L, parseSizeToBytes("100kb"))
    }

    @Test
    fun `parse with spaces`() {
        assertEquals(5 * 1024 * 1024L, parseSizeToBytes("5 MB"))
        assertEquals(100 * 1024L, parseSizeToBytes(" 100KB "))
    }

    @Test
    fun `parse invalid returns null`() {
        assertNull(parseSizeToBytes("abc"))
        assertNull(parseSizeToBytes("MB"))
        assertNull(parseSizeToBytes("5XB"))
    }
}
