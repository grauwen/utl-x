# JSON Canonicalization (JCS) Integration Checklist

## Files Created

### ✅ Implementation
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

**See also:** [JCS Integration Guide](docs/jcs_integration_guide.md)

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
