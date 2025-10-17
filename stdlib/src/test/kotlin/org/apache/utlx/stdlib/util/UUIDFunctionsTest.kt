package org.apache.utlx.stdlib.util

import org.apache.utlx.core.udm.UDM
import org.apache.utlx.stdlib.FunctionArgumentException
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertNotEquals
import java.util.UUID

class UUIDFunctionsTest {

    @Test
    fun testGenerateUuidV4() {
        val result1 = UUIDFunctions.generateUuidV4(listOf())
        
        assertTrue(result1 is UDM.Scalar)
        val uuid1 = (result1 as UDM.Scalar).value as String
        
        // Check UUID format (36 characters with hyphens)
        assertEquals(36, uuid1.length)
        assertTrue(uuid1.contains("-"))
        assertTrue(uuid1.matches(Regex("[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}")))
        
        // Check version (4th position should be '4')
        assertEquals('4', uuid1[14])
        
        // Check variant (19th position should be 8, 9, a, or b)
        assertTrue(uuid1[19] in "89ab")
        
        // Generate another UUID and ensure they're different
        val result2 = UUIDFunctions.generateUuidV4(listOf())
        val uuid2 = (result2 as UDM.Scalar).value as String
        
        assertNotEquals(uuid1, uuid2)
        
        // Verify it's a valid UUID
        UUID.fromString(uuid1) // Should not throw
        UUID.fromString(uuid2) // Should not throw
    }

    @Test
    fun testGenerateUuidV7() {
        val result1 = UUIDFunctions.generateUuidV7(listOf())
        
        assertTrue(result1 is UDM.Scalar)
        val uuid1 = (result1 as UDM.Scalar).value as String
        
        // Check UUID format
        assertEquals(36, uuid1.length)
        assertTrue(uuid1.contains("-"))
        assertTrue(uuid1.matches(Regex("[0-9a-f]{8}-[0-9a-f]{4}-7[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}")))
        
        // Check version (4th position should be '7')
        assertEquals('7', uuid1[14])
        
        // Check variant (19th position should be 8, 9, a, or b)
        assertTrue(uuid1[19] in "89ab")
        
        // Generate another UUID and check chronological ordering
        Thread.sleep(1) // Ensure different timestamp
        val result2 = UUIDFunctions.generateUuidV7(listOf())
        val uuid2 = (result2 as UDM.Scalar).value as String
        
        assertNotEquals(uuid1, uuid2)
        
        // UUID v7 should be sortable by time (later UUID should be greater)
        assertTrue(uuid2 > uuid1, "UUID v7 should be chronologically ordered")
        
        // Verify it's a valid UUID
        UUID.fromString(uuid1) // Should not throw
        UUID.fromString(uuid2) // Should not throw
    }

    @Test
    fun testIsValidUuid() {
        // Test valid UUID v4
        val validUuidV4 = "550e8400-e29b-41d4-a716-446655440000"
        val result1 = UUIDFunctions.isValidUuid(listOf(UDM.Scalar(validUuidV4)))
        assertTrue(result1 is UDM.Scalar)
        assertEquals(true, (result1 as UDM.Scalar).value)
        
        // Test valid UUID v7
        val validUuidV7 = "01891fe5-e0ea-7c2e-8b4a-0c02b1c9c123"
        val result2 = UUIDFunctions.isValidUuid(listOf(UDM.Scalar(validUuidV7)))
        assertTrue(result2 is UDM.Scalar)
        assertEquals(true, (result2 as UDM.Scalar).value)
        
        // Test invalid UUID (wrong format)
        val invalidUuid = "not-a-uuid"
        val result3 = UUIDFunctions.isValidUuid(listOf(UDM.Scalar(invalidUuid)))
        assertTrue(result3 is UDM.Scalar)
        assertEquals(false, (result3 as UDM.Scalar).value)
        
        // Test invalid UUID (wrong length)
        val shortUuid = "550e8400-e29b-41d4-a716"
        val result4 = UUIDFunctions.isValidUuid(listOf(UDM.Scalar(shortUuid)))
        assertTrue(result4 is UDM.Scalar)
        assertEquals(false, (result4 as UDM.Scalar).value)
        
        // Test empty string
        val result5 = UUIDFunctions.isValidUuid(listOf(UDM.Scalar("")))
        assertTrue(result5 is UDM.Scalar)
        assertEquals(false, (result5 as UDM.Scalar).value)
    }

    @Test
    fun testGetUuidVersion() {
        // Test UUID v4
        val uuidV4 = "550e8400-e29b-41d4-a716-446655440000"
        val result1 = UUIDFunctions.getUuidVersion(listOf(UDM.Scalar(uuidV4)))
        assertTrue(result1 is UDM.Scalar)
        assertEquals(4, (result1 as UDM.Scalar).value)
        
        // Test UUID v7
        val uuidV7 = "01891fe5-e0ea-7c2e-8b4a-0c02b1c9c123"
        val result2 = UUIDFunctions.getUuidVersion(listOf(UDM.Scalar(uuidV7)))
        assertTrue(result2 is UDM.Scalar)
        assertEquals(7, (result2 as UDM.Scalar).value)
        
        // Test generated UUID v4
        val generatedV4 = UUIDFunctions.generateUuidV4(listOf())
        val versionResult = UUIDFunctions.getUuidVersion(listOf(generatedV4))
        assertEquals(4, (versionResult as UDM.Scalar).value)
        
        // Test generated UUID v7
        val generatedV7 = UUIDFunctions.generateUuidV7(listOf())
        val versionResult7 = UUIDFunctions.getUuidVersion(listOf(generatedV7))
        assertEquals(7, (versionResult7 as UDM.Scalar).value)
    }

    @Test
    fun testExtractTimestampFromUuidV7() {
        // Test UUID v7 (has timestamp)
        val uuidV7Result = UUIDFunctions.generateUuidV7(listOf())
        val timestampResult = UUIDFunctions.extractTimestampFromUuidV7(listOf(uuidV7Result))
        
        assertTrue(timestampResult is UDM.Scalar)
        val timestamp = (timestampResult as UDM.Scalar).value as Long
        
        // Should be recent timestamp (within last minute)
        val now = System.currentTimeMillis()
        assertTrue(timestamp > now - 60000 && timestamp <= now)
    }

    @Test
    fun testIsUuidV7() {
        // Test UUID v7
        val uuidV7Result = UUIDFunctions.generateUuidV7(listOf())
        val isV7Result = UUIDFunctions.isUuidV7(listOf(uuidV7Result))
        
        assertTrue(isV7Result is UDM.Scalar)
        assertEquals(true, (isV7Result as UDM.Scalar).value)
        
        // Test UUID v4
        val uuidV4Result = UUIDFunctions.generateUuidV4(listOf())
        val isNotV7Result = UUIDFunctions.isUuidV7(listOf(uuidV4Result))
        
        assertTrue(isNotV7Result is UDM.Scalar)
        assertEquals(false, (isNotV7Result as UDM.Scalar).value)
    }

    @Test
    fun testGenerateUuidV7Batch() {
        val batchSize = 5
        val result = UUIDFunctions.generateUuidV7Batch(listOf(UDM.Scalar(batchSize)))
        
        assertTrue(result is UDM.Array)
        val batch = result as UDM.Array
        assertEquals(batchSize, batch.elements.size)
        
        // Check all are valid UUIDs and are v7
        for (element in batch.elements) {
            assertTrue(element is UDM.Scalar)
            val uuid = (element as UDM.Scalar).value as String
            assertEquals(36, uuid.length)
            assertEquals('7', uuid[14]) // Version should be 7
        }
        
        // Check they're in chronological order (should be increasing)
        val uuids = batch.elements.map { (it as UDM.Scalar).value as String }
        for (i in 0 until uuids.size - 1) {
            assertTrue(uuids[i] <= uuids[i + 1], "UUID v7 batch should be in chronological order")
        }
    }

    @Test
    fun testChronologicalOrderingUuidV7() {
        // Generate multiple UUID v7s with small delays
        val uuids = mutableListOf<String>()
        
        repeat(5) {
            val result = UUIDFunctions.generateUuidV7(listOf())
            uuids.add((result as UDM.Scalar).value as String)
            Thread.sleep(1) // Small delay to ensure different timestamps
        }
        
        // Check that they're in chronological order
        for (i in 0 until uuids.size - 1) {
            assertTrue(uuids[i] < uuids[i + 1], "UUID v7 should maintain chronological order")
        }
    }

    @Test
    fun testInvalidArguments() {
        // Test functions that expect no arguments
        assertThrows<FunctionArgumentException> {
            UUIDFunctions.generateUuidV4(listOf(UDM.Scalar("arg")))
        }
        
        assertThrows<FunctionArgumentException> {
            UUIDFunctions.generateUuidV7(listOf(UDM.Scalar("arg")))
        }
        
        // Test functions that expect one argument
        assertThrows<FunctionArgumentException> {
            UUIDFunctions.isValidUuid(listOf())
        }
        
        assertThrows<FunctionArgumentException> {
            UUIDFunctions.getUuidVersion(listOf())
        }
        
        assertThrows<FunctionArgumentException> {
            UUIDFunctions.isValidUuid(listOf(UDM.Scalar("uuid"), UDM.Scalar("extra")))
        }
        
        // Test functions that expect one argument
        assertThrows<FunctionArgumentException> {
            UUIDFunctions.generateUuidV7Batch(listOf())
        }
        
        // Test with wrong argument types
        assertThrows<FunctionArgumentException> {
            UUIDFunctions.isValidUuid(listOf(UDM.Array(listOf())))
        }
    }

    @Test
    fun testInvalidUuidOperations() {
        val invalidUuid = "not-a-uuid"
        
        // Test getting version of invalid UUID
        assertThrows<FunctionArgumentException> {
            UUIDFunctions.getUuidVersion(listOf(UDM.Scalar(invalidUuid)))
        }
        
        // Test extracting timestamp from invalid UUID
        assertThrows<FunctionArgumentException> {
            UUIDFunctions.extractTimestampFromUuidV7(listOf(UDM.Scalar(invalidUuid)))
        }
    }

    @Test
    fun testEdgeCases() {
        // Test null input
        assertThrows<FunctionArgumentException> {
            UUIDFunctions.isValidUuid(listOf(UDM.Scalar(null)))
        }
        
        // Test empty string
        val emptyResult = UUIDFunctions.isValidUuid(listOf(UDM.Scalar("")))
        assertEquals(false, (emptyResult as UDM.Scalar).value)
        
        // Test UUID without hyphens as input
        val noHyphensUuid = "550e8400e29b41d4a716446655440000"
        val validResult = UUIDFunctions.isValidUuid(listOf(UDM.Scalar(noHyphensUuid)))
        assertEquals(false, (validResult as UDM.Scalar).value) // Should be false without hyphens
        
        // Test invalid batch size
        assertThrows<FunctionArgumentException> {
            UUIDFunctions.generateUuidV7Batch(listOf(UDM.Scalar(-1)))
        }
    }
}