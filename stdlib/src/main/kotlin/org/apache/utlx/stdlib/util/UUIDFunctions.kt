/**
 * UUID Generation Functions for UTL-X Standard Library
 * 
 * Location: stdlib/src/main/kotlin/org/apache/utlx/stdlib/util/UUIDFunctions.kt
 * 
 * Provides UUID generation in multiple versions:
 * - UUID v4: Random UUID (existing in CoreFunctions)
 * - UUID v7: Time-ordered UUID (NEW - RFC 9562)
 * 
 * UUID v7 Benefits:
 * - Time-ordered: UUIDs sort chronologically
 * - Database-friendly: Better for B-tree indexes
 * - Monotonic: Generally increasing values
 * - Industry standard: Adopted by Stripe, GitHub, modern databases
 * 
 * Use Cases:
 * - Database primary keys
 * - Transaction IDs
 * - Event IDs in event sourcing
 * - API request IDs
 * 
 * Example:
 *   {
 *     // Old style (random, not sortable)
 *     id_v4: generate-uuid(),
 *     
 *     // New style (time-ordered, sortable)
 *     id_v7: generate-uuid-v7()
 *   }
 */

package org.apache.utlx.stdlib.util

import org.apache.utlx.core.udm.UDM
import java.nio.ByteBuffer
import java.security.SecureRandom
import java.time.Instant
import java.util.UUID

object UUIDFunctions {
    
    private val secureRandom = SecureRandom()
    
    /**
     * Generate UUID v4 (random)
     * Standard random UUID using cryptographically strong random number generator
     * 
     * @return String UUID in format: xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx
     * 
     * Example:
     *   generateUuidV4()
     *   // Returns: "550e8400-e29b-41d4-a716-446655440000"
     * 
     * Note: This is the same as the existing CoreFunctions::generateUuid
     */
    fun generateUuidV4(args: List<UDM>): UDM {
        val uuid = UUID.randomUUID().toString()
        return UDM.fromNative(uuid)
    }
    
    /**
     * Generate UUID v7 (time-ordered)
     * RFC 9562 compliant UUID with millisecond-precision timestamp prefix
     * 
     * Format: tttttttt-tttt-7xxx-yxxx-xxxxxxxxxxxx
     * - t: 48-bit timestamp (milliseconds since Unix epoch)
     * - 7: version indicator
     * - y: variant indicator (10xx in binary)
     * - x: random bits
     * 
     * @param args[0] (optional) timestamp - Custom timestamp in milliseconds
     * @return String UUID v7
     * 
     * Example:
     *   generateUuidV7()
     *   // Returns: "01890a5d-ac96-7000-8000-123456789abc"
     *   
     *   // With custom timestamp
     *   generateUuidV7(1609459200000)  // 2021-01-01 00:00:00 UTC
     */
    fun generateUuidV7(args: List<UDM>): UDM {
        val timestampMs = if (args.isNotEmpty()) {
            args[0].asLong()
        } else {
            Instant.now().toEpochMilli()
        }
        
        val uuid = createUuidV7(timestampMs)
        return UDM.fromNative(uuid)
    }
    
    /**
     * Generate batch of UUID v7s with monotonic guarantee
     * Ensures UUIDs are strictly increasing even if generated in same millisecond
     * 
     * @param args[0] count - Number of UUIDs to generate
     * @return Array of UUID v7 strings
     * 
     * Example:
     *   generateUuidV7Batch(100)
     *   // Returns: ["01890a5d-...", "01890a5d-...", ...]
     */
    fun generateUuidV7Batch(args: List<UDM>): UDM {
        require(args.isNotEmpty()) { "generateUuidV7Batch requires count argument" }
        
        val count = args[0].asInt()
        require(count > 0) { "Count must be positive" }
        
        val baseTimestamp = Instant.now().toEpochMilli()
        val uuids = mutableListOf<String>()
        
        // Generate with monotonic counter to ensure uniqueness
        for (i in 0 until count) {
            // Add microsecond-level offset for ordering
            val timestamp = baseTimestamp + (i / 1000)
            val uuid = createUuidV7(timestamp, counter = i % 1000)
            uuids.add(uuid)
        }
        
        return UDM.fromNative(uuids)
    }
    
    /**
     * Extract timestamp from UUID v7
     * 
     * @param args[0] uuid - UUID v7 string
     * @return Long timestamp in milliseconds
     * 
     * Example:
     *   extractTimestampFromUuidV7("01890a5d-ac96-7000-8000-123456789abc")
     *   // Returns: 1672531200000
     */
    fun extractTimestampFromUuidV7(args: List<UDM>): UDM {
        require(args.isNotEmpty()) { "extractTimestampFromUuidV7 requires uuid argument" }
        
        val uuidString = args[0].asString()
        val timestamp = extractTimestamp(uuidString)
        
        return UDM.fromNative(timestamp)
    }
    
    /**
     * Check if UUID is version 7
     * 
     * @param args[0] uuid - UUID string to check
     * @return Boolean true if UUID v7
     * 
     * Example:
     *   isUuidV7("01890a5d-ac96-7000-8000-123456789abc")  // true
     *   isUuidV7("550e8400-e29b-41d4-a716-446655440000")  // false (v4)
     */
    fun isUuidV7(args: List<UDM>): UDM {
        require(args.isNotEmpty()) { "isUuidV7 requires uuid argument" }
        
        val uuidString = args[0].asString()
        val isV7 = isVersionSeven(uuidString)
        
        return UDM.fromNative(isV7)
    }
    
    /**
     * Get UUID version number
     * 
     * @param args[0] uuid - UUID string
     * @return Integer version number (4, 7, etc.)
     * 
     * Example:
     *   getUuidVersion("01890a5d-ac96-7000-8000-123456789abc")  // 7
     *   getUuidVersion("550e8400-e29b-41d4-a716-446655440000")  // 4
     */
    fun getUuidVersion(args: List<UDM>): UDM {
        require(args.isNotEmpty()) { "getUuidVersion requires uuid argument" }
        
        val uuidString = args[0].asString()
        val version = extractVersion(uuidString)
        
        return UDM.fromNative(version)
    }
    
    /**
     * Validate UUID format
     * 
     * @param args[0] uuid - String to validate
     * @return Boolean true if valid UUID
     * 
     * Example:
     *   isValidUuid("01890a5d-ac96-7000-8000-123456789abc")  // true
     *   isValidUuid("not-a-uuid")  // false
     */
    fun isValidUuid(args: List<UDM>): UDM {
        require(args.isNotEmpty()) { "isValidUuid requires uuid argument" }
        
        val uuidString = args[0].asString()
        val isValid = try {
            UUID.fromString(uuidString)
            true
        } catch (e: IllegalArgumentException) {
            false
        }
        
        return UDM.fromNative(isValid)
    }
    
    // ============================================================================
    // Private Helper Functions
    // ============================================================================
    
    /**
     * Create UUID v7 from timestamp and optional counter
     * 
     * UUID v7 Layout (128 bits total):
     * - 48 bits: Unix timestamp in milliseconds
     * - 4 bits: version (0111 = 7)
     * - 12 bits: random or counter
     * - 2 bits: variant (10)
     * - 62 bits: random
     */
    private fun createUuidV7(timestampMs: Long, counter: Int = 0): String {
        // Prepare 16 bytes for UUID
        val bytes = ByteArray(16)
        
        // 1. Timestamp (48 bits = 6 bytes) - Most significant bits
        bytes[0] = ((timestampMs shr 40) and 0xFF).toByte()
        bytes[1] = ((timestampMs shr 32) and 0xFF).toByte()
        bytes[2] = ((timestampMs shr 24) and 0xFF).toByte()
        bytes[3] = ((timestampMs shr 16) and 0xFF).toByte()
        bytes[4] = ((timestampMs shr 8) and 0xFF).toByte()
        bytes[5] = (timestampMs and 0xFF).toByte()
        
        // 2. Version and random/counter (16 bits = 2 bytes)
        // First 4 bits = version (0111 = 7), next 12 bits = counter or random
        val versionAndCounter = if (counter > 0) {
            0x7000 or (counter and 0x0FFF)
        } else {
            0x7000 or (secureRandom.nextInt(0x1000))
        }
        bytes[6] = ((versionAndCounter shr 8) and 0xFF).toByte()
        bytes[7] = (versionAndCounter and 0xFF).toByte()
        
        // 3. Variant and random (64 bits = 8 bytes)
        // First 2 bits = variant (10), remaining 62 bits = random
        val randomBytes = ByteArray(8)
        secureRandom.nextBytes(randomBytes)
        
        // Set variant bits (10xxxxxx in first byte)
        randomBytes[0] = ((randomBytes[0].toInt() and 0x3F) or 0x80).toByte()
        
        // Copy random bytes
        System.arraycopy(randomBytes, 0, bytes, 8, 8)
        
        // Format as UUID string
        return formatUuidBytes(bytes)
    }
    
    /**
     * Format byte array as UUID string
     */
    private fun formatUuidBytes(bytes: ByteArray): String {
        require(bytes.size == 16) { "UUID requires 16 bytes" }
        
        return String.format(
            "%02x%02x%02x%02x-%02x%02x-%02x%02x-%02x%02x-%02x%02x%02x%02x%02x%02x",
            bytes[0], bytes[1], bytes[2], bytes[3],
            bytes[4], bytes[5],
            bytes[6], bytes[7],
            bytes[8], bytes[9],
            bytes[10], bytes[11], bytes[12], bytes[13], bytes[14], bytes[15]
        )
    }
    
    /**
     * Extract timestamp from UUID v7
     */
    private fun extractTimestamp(uuidString: String): Long {
        val uuid = UUID.fromString(uuidString)
        
        // Get most significant bits (contains timestamp)
        val msb = uuid.mostSignificantBits
        
        // Extract 48-bit timestamp from most significant 48 bits
        val timestamp = msb ushr 16
        
        return timestamp
    }
    
    /**
     * Extract version number from UUID
     */
    private fun extractVersion(uuidString: String): Int {
        val uuid = UUID.fromString(uuidString)
        return uuid.version()
    }
    
    /**
     * Check if UUID is version 7
     */
    private fun isVersionSeven(uuidString: String): Boolean {
        return try {
            val uuid = UUID.fromString(uuidString)
            uuid.version() == 7
        } catch (e: IllegalArgumentException) {
            false
        }
    }
    
    /**
     * Parse UUID string to byte array
     */
    private fun parseUuidToBytes(uuidString: String): ByteArray {
        val uuid = UUID.fromString(uuidString)
        val buffer = ByteBuffer.wrap(ByteArray(16))
        buffer.putLong(uuid.mostSignificantBits)
        buffer.putLong(uuid.leastSignificantBits)
        return buffer.array()
    }
}

// ============================================================================
// INTEGRATION INTO Functions.kt
// 
// Add to stdlib/src/main/kotlin/org/apache/utlx/stdlib/Functions.kt:
// ============================================================================

/*

import org.apache.utlx.stdlib.util.UUIDFunctions

// In registerCoreFunctions() or create new registerUUIDFunctions():
private fun registerUUIDFunctions() {
    // UUID generation
    register("generate-uuid-v4", UUIDFunctions::generateUuidV4)
    register("generate-uuid-v7", UUIDFunctions::generateUuidV7)
    register("generate-uuid-v7-batch", UUIDFunctions::generateUuidV7Batch)
    
    // UUID utilities
    register("extract-timestamp-from-uuid-v7", UUIDFunctions::extractTimestampFromUuidV7)
    register("is-uuid-v7", UUIDFunctions::isUuidV7)
    register("get-uuid-version", UUIDFunctions::getUuidVersion)
    register("is-valid-uuid", UUIDFunctions::isValidUuid)
}

// Alternative: Add to existing CoreFunctions registration
private fun registerCoreFunctions() {
    register("if", CoreFunctions::ifThenElse)
    register("coalesce", CoreFunctions::coalesce)
    register("generate-uuid", CoreFunctions::generateUuid)  // Keep existing (v4)
    register("default", CoreFunctions::default)
    
    // Add UUID v7 functions
    register("generate-uuid-v7", UUIDFunctions::generateUuidV7)
    register("generate-uuid-v7-batch", UUIDFunctions::generateUuidV7Batch)
    register("extract-timestamp-from-uuid-v7", UUIDFunctions::extractTimestampFromUuidV7)
    register("is-uuid-v7", UUIDFunctions::isUuidV7)
    register("get-uuid-version", UUIDFunctions::getUuidVersion)
    register("is-valid-uuid", UUIDFunctions::isValidUuid)
}

// Don't forget to call it in registerAllFunctions():
private fun registerAllFunctions() {
    // ... existing registrations ...
    registerCoreFunctions()  // Will include UUID v7 functions
    // OR
    registerUUIDFunctions()  // If you prefer separate registration
}

*/

// ============================================================================
// USAGE EXAMPLES
// ============================================================================

/*

// Example 1: Basic UUID v7 generation
{
  transactionId: generate-uuid-v7(),
  customerId: generate-uuid-v7()
}

// Example 2: Sortable IDs for database
{
  events: [
    {id: generate-uuid-v7(), event: "login", time: now()},
    {id: generate-uuid-v7(), event: "purchase", time: now()},
    {id: generate-uuid-v7(), event: "logout", time: now()}
  ]
  // IDs will sort chronologically!
}

// Example 3: Extracting timestamp from UUID v7
let orderId = generate-uuid-v7()
{
  orderId: orderId,
  orderTimestamp: extract-timestamp-from-uuid-v7(orderId),
  isV7: is-uuid-v7(orderId)
}

// Example 4: Batch generation for imports
{
  newCustomerIds: generate-uuid-v7-batch(1000)
}

// Example 5: Migration from v4 to v7
{
  // Old way (v4 - random)
  oldStyleId: generate-uuid(),
  
  // New way (v7 - time-ordered)
  newStyleId: generate-uuid-v7(),
  
  // Comparison
  comparison: {
    v4Version: get-uuid-version(generate-uuid()),      // Returns: 4
    v7Version: get-uuid-version(generate-uuid-v7()),   // Returns: 7
    v7Sortable: true,
    v4Sortable: false
  }
}

*/
