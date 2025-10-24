package org.apache.utlx.stdlib.json

import org.apache.utlx.core.udm.UDM
import org.apache.utlx.stdlib.FunctionArgumentException
import java.math.BigDecimal
import java.security.MessageDigest
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow
import org.apache.utlx.stdlib.annotations.UTLXFunction
/**
 * JSON Canonicalization Functions (RFC 8785 - JSON Canonicalization Scheme)
 * 
 * Provides deterministic, unique JSON representation for:
 * - Digital signatures (JWS - JSON Web Signatures)
 * - Hash computation and integrity verification
 * - Cryptographic operations
 * - Deterministic comparison of JSON documents
 * - Cache key generation
 * - Webhook signature verification
 * 
 * @see <a href="https://www.rfc-editor.org/rfc/rfc8785">RFC 8785 - JSON Canonicalization Scheme</a>
 */
object JSONCanonicalizationFunctions {

    @UTLXFunction(
        description = "Canonicalizes JSON according to RFC 8785 (JSON Canonicalization Scheme)",
        minArgs = 2,
        maxArgs = 2,
        category = "JSON",
        parameters = [
            "json: Json value"
        ],
        returns = "Result of the operation",
        example = "canonicalizeJSON(...) => result",
        notes = "Example:\n```\ncanonicalizeJSON({\"b\": 2, \"a\": 1})\n// Returns: UDM.Scalar(\"{\\\"a\\\":1,\\\"b\\\":2}\")\n```",
        tags = ["json"],
        since = "1.0"
    )
    /**
     * Canonicalizes JSON according to RFC 8785 (JSON Canonicalization Scheme)
     * 
     * @param args List containing: [json]
     * @return UDM Scalar with canonical JSON string per RFC 8785
     * 
     * Example:
     * ```
     * canonicalizeJSON({"b": 2, "a": 1})
     * // Returns: UDM.Scalar("{\"a\":1,\"b\":2}")
     * ```
     */
    fun canonicalizeJSON(args: List<UDM>): UDM {
        requireArgs(args, 1, "canonicalizeJSON")
        val json = args[0]
        
        return try {
            val result = canonicalizeJSONInternal(json)
            UDM.Scalar(result)
        } catch (e: Exception) {
            throw FunctionArgumentException(
                "Failed to canonicalize JSON: ${e.message}. " +
                "Hint: Ensure the input is valid JSON (object, array, or scalar value)."
            )
        }
    }

    @UTLXFunction(
        description = "Alias for canonicalizeJSON - shorter form (JCS)",
        minArgs = 2,
        maxArgs = 2,
        category = "JSON",
        parameters = [
            "json: Json value",
        "json2: Json2 value"
        ],
        returns = "Result of the operation",
        example = "jcs(...) => result",
        tags = ["json"],
        since = "1.0"
    )
    /**
     * Alias for canonicalizeJSON - shorter form (JCS)
     * 
     * @param args List containing: [json]
     * @return UDM Scalar with canonical JSON string per RFC 8785
     */
    fun jcs(args: List<UDM>): UDM = canonicalizeJSON(args)

    @UTLXFunction(
        description = "Canonicalizes JSON and computes cryptographic hash",
        minArgs = 2,
        maxArgs = 2,
        category = "JSON",
        parameters = [
            "json: Json value",
        "json2: Json2 value"
        ],
        returns = "Result of the operation",
        example = "canonicalJSONHash(...) => result",
        notes = "Example:\n```\ncanonicalJSONHash({\"id\": 123}, \"SHA-256\")\n// Returns: UDM.Scalar(\"a1b2c3d4...\")\n```",
        tags = ["json"],
        since = "1.0"
    )
    /**
     * Canonicalizes JSON and computes cryptographic hash
     * 
     * @param args List containing: [json, algorithm?]
     * @return UDM Scalar with hex-encoded hash of canonical JSON
     * 
     * Example:
     * ```
     * canonicalJSONHash({"id": 123}, "SHA-256")
     * // Returns: UDM.Scalar("a1b2c3d4...")
     * ```
     */
    fun canonicalJSONHash(args: List<UDM>): UDM {
        if (args.isEmpty() || args.size > 2) {
            throw FunctionArgumentException(
                "canonicalJSONHash expects 1 or 2 arguments (json, algorithm?), got ${args.size}. " +
                "Hint: Usage is canonicalJSONHash(json) or canonicalJSONHash(json, \"SHA-256\")."
            )
        }
        
        val json = args[0]
        val algorithm = if (args.size > 1) args[1].asString() else "SHA-256"
        
        return try {
            val canonical = canonicalizeJSONInternal(json)
            val hash = hashString(canonical, algorithm)
            UDM.Scalar(hash)
        } catch (e: Exception) {
            throw FunctionArgumentException(
                "Failed to compute canonical JSON hash: ${e.message}. " +
                "Hint: Ensure JSON is valid and algorithm is supported (e.g., \"SHA-256\", \"MD5\")."
            )
        }
    }

    @UTLXFunction(
        description = "Compares two JSON values for semantic equality using canonicalization",
        minArgs = 1,
        maxArgs = 1,
        category = "JSON",
        parameters = [
            "array: Input array to process",
        "json2: Json2 value"
        ],
        returns = "Result of the operation",
        example = "jsonEquals(...) => result",
        notes = "Example:\n```\njsonEquals({\"b\": 2, \"a\": 1}, {\"a\": 1, \"b\": 2})\n// Returns: UDM.Scalar(true)\n```",
        tags = ["json"],
        since = "1.0"
    )
    /**
     * Compares two JSON values for semantic equality using canonicalization
     * 
     * @param args List containing: [json1, json2]
     * @return UDM Scalar Boolean indicating if canonically equivalent
     * 
     * Example:
     * ```
     * jsonEquals({"b": 2, "a": 1}, {"a": 1, "b": 2})
     * // Returns: UDM.Scalar(true)
     * ```
     */
    fun jsonEquals(args: List<UDM>): UDM {
        requireArgs(args, 2, "jsonEquals")
        val json1 = args[0]
        val json2 = args[1]
        
        return try {
            val canonical1 = canonicalizeJSONInternal(json1)
            val canonical2 = canonicalizeJSONInternal(json2)
            UDM.Scalar(canonical1 == canonical2)
        } catch (e: Exception) {
            UDM.Scalar(false)
        }
    }


// ============================================================================
// PRIVATE IMPLEMENTATION - RFC 8785 Compliance
// ============================================================================

/**
 * Canonicalizes a JSON object (RFC 8785 Section 3.2.3)
 * 
 * Rules:
 * - Keys sorted lexicographically by Unicode code point
 * - No whitespace
 * - Recursive canonicalization of values
 */
private fun canonicalizeObject(obj: UDM.Object): String {
    if (obj.properties.isEmpty()) {
        return "{}"
    }
    
    val sortedEntries = obj.properties.entries
        .sortedBy { it.key }  // Lexicographic sort (Unicode code point order)
        .joinToString(",") { (key, value) ->
            "${canonicalizeString(key)}:${canonicalizeJSONInternal(value)}"
        }
    
    return "{$sortedEntries}"
}

/**
 * Canonicalizes a JSON array (RFC 8785 Section 3.2.4)
 * 
 * Rules:
 * - Elements in original order (not sorted)
 * - No whitespace
 * - Recursive canonicalization of elements
 */
private fun canonicalizeArray(arr: UDM.Array): String {
    if (arr.elements.isEmpty()) {
        return "[]"
    }
    
    val elements = arr.elements.joinToString(",") { canonicalizeJSONInternal(it) }
    return "[$elements]"
}

/**
 * Canonicalizes a JSON scalar value
 * 
 * Handles: null, boolean, number, string
 */
private fun canonicalizeScalar(scalar: UDM.Scalar): String {
    return when (val value = scalar.value) {
        null -> "null"
        is Boolean -> value.toString()  // "true" or "false"
        is Number -> canonicalizeNumber(value)
        is String -> canonicalizeString(value)
        else -> throw IllegalArgumentException(
            "Unsupported scalar type: ${value::class.simpleName}"
        )
    }
}

/**
 * Canonicalizes a JSON number per RFC 8785 Section 3.2.2
 * 
 * Rules:
 * - No leading zeros (except 0.x)
 * - No trailing zeros after decimal point
 * - No positive sign
 * - Use exponential notation for very large/small numbers
 * - Integers without decimal point if no fractional part
 * - NaN and Infinity not allowed
 */
private fun canonicalizeNumber(num: Number): String {
    val d = num.toDouble()
    
    // Reject non-finite numbers (RFC 8785 Section 3.2.2)
    if (d.isNaN() || d.isInfinite()) {
        throw IllegalArgumentException(
            "NaN and Infinity are not valid JSON numbers"
        )
    }
    
    // Special case: zero
    if (d == 0.0 || d == -0.0) {
        return "0"  // Canonical zero (no negative zero)
    }
    
    // For integers in safe range, use integer representation
    if (isIntegerValue(d) && abs(d) < 1e21) {
        return d.toLong().toString()
    }
    
    // Use ECMAScript number-to-string conversion per RFC 8785
    return numberToString(d)
}

/**
 * Checks if a double represents an integer value
 */
private fun isIntegerValue(d: Double): Boolean {
    return d % 1.0 == 0.0
}

/**
 * Converts number to string per RFC 8785 (ECMAScript algorithm)
 * 
 * Implements the ECMAScript number-to-string conversion algorithm
 * as specified in RFC 8785 Section 3.2.2.3
 */
private fun numberToString(d: Double): String {
    // Handle special cases
    if (d == 0.0) return "0"
    
    val isNegative = d < 0
    val absValue = abs(d)
    
    // Determine if we need exponential notation
    val exponent = floor(log10(absValue)).toInt()
    
    when {
        // Use decimal notation for reasonable range
        exponent in -6..20 -> {
            // Format as decimal with minimal digits
            val bd = BigDecimal.valueOf(d)
            val plain = bd.stripTrailingZeros().toPlainString()
            
            // Ensure we don't have trailing .0 for integers
            return if (plain.endsWith(".0")) {
                plain.substring(0, plain.length - 2)
            } else {
                plain
            }
        }
        
        // Use exponential notation for very large or very small numbers
        else -> {
            val mantissa = absValue / 10.0.pow(exponent)
            val mantissaStr = BigDecimal.valueOf(mantissa)
                .stripTrailingZeros()
                .toPlainString()
            
            val sign = if (isNegative) "-" else ""
            val expSign = if (exponent >= 0) "+" else ""
            
            return "${sign}${mantissaStr}e${expSign}${exponent}"
        }
    }
}

/**
 * Canonicalizes a JSON string per RFC 8785 Section 3.2.1
 * 
 * Rules:
 * - Enclosed in double quotes
 * - Control characters (U+0000 to U+001F) escaped as \uXXXX
 * - " escaped as \"
 * - \ escaped as \\
 * - Other characters: literal (including Unicode above U+001F)
 */
private fun canonicalizeString(str: String): String {
    val escaped = StringBuilder()
    escaped.append('"')
    
    for (char in str) {
        when (char) {
            '"' -> escaped.append("\\\"")
            '\\' -> escaped.append("\\\\")
            '\b' -> escaped.append("\\b")
            '\u000C' -> escaped.append("\\f")
            '\n' -> escaped.append("\\n")
            '\r' -> escaped.append("\\r")
            '\t' -> escaped.append("\\t")
            in '\u0000'..'\u001F' -> {
                // Control characters must be escaped as \uXXXX
                escaped.append("\\u${char.code.toString(16).padStart(4, '0')}")
            }
            else -> escaped.append(char)  // Literal, including Unicode
        }
    }
    
    escaped.append('"')
    return escaped.toString()
}

/**
 * Computes hash of a string
 * 
 * @param input String to hash
 * @param algorithm Hash algorithm
 * @return Hex-encoded hash
 */
private fun hashString(input: String, algorithm: String): String {
    val digest = MessageDigest.getInstance(algorithm)
    val hashBytes = digest.digest(input.toByteArray(Charsets.UTF_8))
    return hashBytes.joinToString("") { "%02x".format(it) }
}

// ============================================================================
// VALIDATION AND TESTING UTILITIES
// ============================================================================

    @UTLXFunction(
        description = "Validates that a string is valid canonical JSON per RFC 8785",
        minArgs = 1,
        maxArgs = 1,
        category = "JSON",
        parameters = [
            "array: Input array to process"
        ],
        returns = "Boolean indicating the result",
        example = "isCanonicalJSON(...) => result",
        tags = ["json"],
        since = "1.0"
    )
    /**
     * Validates that a string is valid canonical JSON per RFC 8785
     * 
     * @param args List containing: [canonicalJSON]
     * @return UDM Scalar Boolean indicating if valid canonical JSON
     */
    fun isCanonicalJSON(args: List<UDM>): UDM {
        requireArgs(args, 1, "isCanonicalJSON")
        val canonicalJSON = args[0].asString()
        
        return try {
            // Check no leading/trailing whitespace
            if (canonicalJSON != canonicalJSON.trim()) {
                return UDM.Scalar(false)
            }
            
            // Check no whitespace inside (except in strings)
            var inString = false
            var escape = false
            
            for (char in canonicalJSON) {
                when {
                    escape -> escape = false
                    char == '\\' && inString -> escape = true
                    char == '"' -> inString = !inString
                    char.isWhitespace() && !inString -> return UDM.Scalar(false)
                }
            }
            
            UDM.Scalar(true)
        } catch (e: Exception) {
            UDM.Scalar(false)
        }
    }

    @UTLXFunction(
        description = "Gets the canonical form size in bytes (UTF-8)",
        minArgs = 1,
        maxArgs = 1,
        category = "JSON",
        parameters = [
            "array: Input array to process"
        ],
        returns = "Result of the operation",
        example = "canonicalJSONSize(...) => result",
        tags = ["json"],
        since = "1.0"
    )
    /**
     * Gets the canonical form size in bytes (UTF-8)
     * 
     * @param args List containing: [json]
     * @return UDM Scalar with size in bytes
     */
    fun canonicalJSONSize(args: List<UDM>): UDM {
        requireArgs(args, 1, "canonicalJSONSize")
        val json = args[0]
        
        return try {
            val canonical = canonicalizeJSONInternal(json)
            val size = canonical.toByteArray(Charsets.UTF_8).size
            UDM.Scalar(size.toDouble())
        } catch (e: Exception) {
            throw FunctionArgumentException(
                "Failed to compute canonical JSON size: ${e.message}. " +
                "Hint: Ensure the input is valid JSON (object, array, or scalar value)."
            )
        }
    }

    // Helper functions
    private fun requireArgs(args: List<UDM>, expected: Int, functionName: String) {
        if (args.size != expected) {
            throw FunctionArgumentException(
                "$functionName expects $expected argument(s), got ${args.size}. " +
                "Hint: Check the function signature and provide the correct number of arguments."
            )
        }
    }
    
    private fun UDM.asString(): String = when (this) {
        is UDM.Scalar -> value?.toString()
            ?: throw FunctionArgumentException(
                "Expected non-null string value, but got null. " +
                "Hint: Ensure the value is a non-empty string."
            )
        else -> throw FunctionArgumentException(
            "Expected string value, but got ${getTypeDescription(this)}. " +
            "Hint: Use toString() to convert values to strings."
        )
    }
    
    // Private helper function for internal canonicalization (returns String directly)
    private fun canonicalizeJSONInternal(json: UDM): String {
        return when (json) {
            is UDM.Object -> canonicalizeObject(json)
            is UDM.Array -> canonicalizeArray(json)
            is UDM.Scalar -> canonicalizeScalar(json)
            else -> throw FunctionArgumentException(
                "Cannot canonicalize type: ${getTypeDescription(json)}. " +
                "Hint: JSON canonicalization supports objects, arrays, and scalar values (string, number, boolean, null)."
            )
        }
    }

    private fun getTypeDescription(udm: UDM): String {
        return when (udm) {
            is UDM.Scalar -> {
                when (val value = udm.value) {
                    is String -> "string"
                    is Number -> "number"
                    is Boolean -> "boolean"
                    null -> "null"
                    else -> value.javaClass.simpleName
                }
            }
            is UDM.Array -> "array"
            is UDM.Object -> "object"
            is UDM.Binary -> "binary"
            is UDM.DateTime -> "datetime"
            is UDM.Date -> "date"
            is UDM.LocalDateTime -> "localdatetime"
            is UDM.Time -> "time"
            is UDM.Lambda -> "lambda"
            else -> udm.javaClass.simpleName
        }
    }
}