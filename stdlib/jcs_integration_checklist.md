# JSON Canonicalization (JCS) Integration Checklist

## Files Created

### ✅ Implementation
- **File:** `stdlib/src/main/kotlin/org/apache/utlx/stdlib/json/JSONCanonicalization.kt`
- **Status:** Complete implementation with RFC 8785 compliance
- **Functions:**
  - `canonicalizeJSON()` - Primary function
  - `jcs()` - Short alias
  - `canonicalJSONHash()` - Canonicalize + hash
  - `jsonEquals()` - Semantic comparison
  - Helper utilities

### ✅ Tests
- **File:** `stdlib/src/test/kotlin/org/apache/utlx/stdlib/json/JSONCanonicalizationTest.kt`
- **Status:** Comprehensive test suite
- **Coverage:**
  - RFC 8785 test vectors
  - Number canonicalization
  - String escaping
  - Object/array handling
  - Edge cases
  - Real-world examples

### ✅ Documentation
- **File:** `stdlib/docs/jcs_integration_guide.md`
- **Contents:**
  - Quick start guide
  - API reference
  - Use case examples
  - Performance considerations
  - Security guidelines

---

## Integration Steps

### Step 1: Add to Project Structure ✅

Place files in UTL-X project:
```
stdlib/
├── src/
│   ├── main/kotlin/org/apache/utlx/stdlib/json/
│   │   └── JSONCanonicalization.kt          ← NEW
│   └── test/kotlin/org/apache/utlx/stdlib/json/
│       └── JSONCanonicalizationTest.kt      ← NEW
└── docs/
    └── jcs_integration_guide.md             ← NEW
```

### Step 2: Update Build Configuration

Add to `stdlib/build.gradle.kts`:

```kotlin
dependencies {
    // No additional dependencies needed for pure implementation
    
    // OR if using external library:
    implementation("io.github.erdtman:java-json-canonicalization:1.1")
}
```

### Step 3: Run Tests

```bash
# Run JSON canonicalization tests
./gradlew :stdlib:test --tests JSONCanonicalizationTest

# Verify all tests pass
./gradlew :stdlib:test
```

### Step 4: Update Documentation Index

Add to `stdlib/stlib_complete_reference.md`:

```markdown
## JSON Functions

### Canonicalization (RFC 8785)

- `canonicalizeJSON(json)` - Canonicalize JSON per RFC 8785
- `jcs(json)` - Short alias for canonicalizeJSON
- `canonicalJSONHash(json, algorithm)` - Canonicalize and hash
- `jsonEquals(json1, json2)` - Semantic equality comparison

**See also:** [JCS Integration Guide](docs/jcs_integration_guide.md)
```

### Step 5: Add to Function Registry

Update `stdlib/src/main/kotlin/org/apache/utlx/stdlib/Functions.kt`:

```kotlin
// Import JSON canonicalization functions
import org.apache.utlx.stdlib.json.canonicalizeJSON
import org.apache.utlx.stdlib.json.jcs
import org.apache.utlx.stdlib.json.canonicalJSONHash
import org.apache.utlx.stdlib.json.jsonEquals

// Register in function map
val standardLibrary = mapOf(
    // ... existing functions ...
    
    "canonicalizeJSON" to ::canonicalizeJSON,
    "jcs" to ::jcs,
    "canonicalJSONHash" to ::canonicalJSONHash,
    "jsonEquals" to ::jsonEquals
)
```

---

## Verification Checklist

### ✅ Functionality
- [ ] All tests pass
- [ ] RFC 8785 test vectors validated
- [ ] Number canonicalization works correctly
- [ ] String escaping handles all cases
- [ ] Object key sorting is lexicographic
- [ ] Array order preserved
- [ ] Hash functions work with all algorithms

### ✅ Integration
- [ ] Functions accessible from UTL-X scripts
- [ ] Works with UDM types
- [ ] Compatible with existing JSON functions
- [ ] No naming conflicts
- [ ] Documentation complete

### ✅ Performance
- [ ] Sub-millisecond for small objects (<1KB)
- [ ] No memory leaks
- [ ] Handles large documents (tested up to 1MB)
- [ ] No stack overflow on deep nesting

### ✅ Security
- [ ] NaN and Infinity rejected
- [ ] Input validation in place
- [ ] No code injection vulnerabilities
- [ ] Proper error handling
- [ ] Safe for untrusted input

---

## Usage Examples

### Example 1: Webhook Verification

```utlx
let canonical = canonicalizeJSON(webhookPayload)
let signature = hmacSHA256(canonical, secret)

if (signature == receivedSignature) {
    processWebhook(webhookPayload)
}
```

### Example 2: Cache Keys

```kotlin
val cacheKey = canonicalJSONHash(queryParams)
cache.getOrPut(cacheKey) {
    executeQuery(queryParams)
}
```

### Example 3: Document Integrity

```kotlin
val hash = canonicalJSONHash(document, "SHA-256")
storeIntegrityHash(documentId, hash)
```

---

## Related Functions

These functions work well with JCS:

### Encoding Functions
- `base64Encode()` - Encode canonical JSON for transport
- `urlEncode()` - URL-safe encoding
- `hexEncode()` - Hex encoding

### Crypto Functions
- `hmacSHA256()` - HMAC signing
- `sha256()` - Hash computation
- `signRS256()` - RSA signatures

### JSON Functions
- `parseJSON()` - Parse JSON strings to UDM
- `toJSON()` - Serialize UDM to JSON

---

## Future Enhancements (Optional)

Consider adding in future versions:

### Streaming Canonicalization
For very large documents:
```kotlin
fun canonicalizeJSONStream(input: InputStream): OutputStream
```

### Custom Comparators
For application-specific sorting:
```kotlin
fun canonicalizeJSON(
    json: UDM,
    keyComparator: Comparator<String>
): String
```

### Format Conversion
Direct canonicalization from other formats:
```kotlin
fun canonicalizeXMLAsJSON(xml: String): String
fun canonicalizeYAMLAsJSON(yaml: String): String
```

### Performance Metrics
Built-in instrumentation:
```kotlin
data class CanonicalizationMetrics(
    val canonicalizationTimeMs: Long,
    val inputSize: Int,
    val outputSize: Int,
    val objectCount: Int,
    val arrayCount: Int
)

fun canonicalizeJSONWithMetrics(json: UDM): Pair<String, CanonicalizationMetrics>
```

---

## Troubleshooting

### Issue: NaN/Infinity Errors

**Problem:**
```
IllegalArgumentException: NaN and Infinity are not valid JSON numbers
```

**Solution:**
Validate and filter data before canonicalization:
```kotlin
fun sanitizeNumber(n: Double): Double {
    return when {
        n.isNaN() -> 0.0
        n.isInfinite() -> if (n > 0) Double.MAX_VALUE else -Double.MAX_VALUE
        else -> n
    }
}
```

### Issue: Large Memory Usage

**Problem:** OutOfMemoryError with large documents

**Solution:** Implement size limits:
```kotlin
const val MAX_JSON_SIZE = 10_000_000 // 10MB

if (jsonString.length > MAX_JSON_SIZE) {
    throw IllegalArgumentException("JSON too large")
}
```

### Issue: Incorrect Signatures

**Problem:** Signature verification fails

**Debug Steps:**
1. Log canonical form: `println(canonicalizeJSON(data))`
2. Verify algorithm matches: Both sides use same hash
3. Check encoding: UTF-8 for both canonicalization and signing
4. Compare byte-by-byte: Use hex dump to debug

---

## Additional Resources

### Documentation
- [JCS Integration Guide](docs/jcs_integration_guide.md)
- [RFC 8785](https://www.rfc-editor.org/rfc/rfc8785)
- [UTL-X Stdlib Reference](stlib_complete_reference.md)

### Examples
- [Webhook Examples](examples/webhooks/)
- [JWT Examples](examples/jwt/)
- [Cache Key Examples](examples/caching/)

### Tools
- [JCS Playground](https://jsoncanonical.org)
- [RFC 8785 Test Vectors](https://www.rfc-editor.org/rfc/rfc8785#appendix-B)

---

## Completion Status

- [x] Implementation complete
- [x] Tests complete
- [x] Documentation complete
- [ ] Integration into UTL-X runtime
- [ ] User acceptance testing
- [ ] Performance benchmarking
- [ ] Security audit

---

## Next Steps

1. **Review implementation** - Code review by team
2. **Merge to main branch** - After approval
3. **Update changelog** - Add to release notes
4. **Announce feature** - Documentation, blog post
5. **Monitor usage** - Track adoption and issues

---

## Contact

For questions or issues:
- **GitHub Issues:** [github.com/grauwen/utl-x/issues](https://github.com/grauwen/utl-x/issues)
- **Documentation:** Project README
- **Email:** Project maintainers