# JSON Canonicalization (JCS) Integration Guide

## Overview

JSON Canonicalization Scheme (JCS), defined in **RFC 8785**, provides a standardized way to produce unique, deterministic JSON representations. This is essential for:

- **Digital Signatures** - JWS (JSON Web Signatures)
- **Webhook Verification** - HMAC signature validation
- **Content Integrity** - Hash-based verification
- **Cache Keys** - Deterministic identifiers
- **Data Comparison** - Reliable JSON equality

**Module:** `org.apache.utlx.stdlib.json.JSONCanonicalization`

**RFC Reference:** [RFC 8785 - JSON Canonicalization Scheme](https://www.rfc-editor.org/rfc/rfc8785)

---

## Quick Start

### Basic Canonicalization

```kotlin
import org.apache.utlx.stdlib.json.canonicalizeJSON
import org.apache.utlx.core.udm.UDM

// Create JSON object
val data = UDM.Object(mutableMapOf(
    "b" to UDM.Scalar(2),
    "a" to UDM.Scalar(1),
    "c" to UDM.Scalar(3)
))

// Canonicalize
val canonical = canonicalizeJSON(data)
// Result: {"a":1,"b":2,"c":3}
```

### Using in UTL-X Transformations

```utlx
%utlx 1.0
input json
output json
---

// Webhook signature verification
let canonical = canonicalizeJSON(input.payload)
let signature = hmacSHA256(canonical, env.WEBHOOK_SECRET)

if (signature == input.signature) {
  {
    status: "verified",
    data: input.payload
  }
} else {
  {
    status: "invalid",
    error: "Signature mismatch"
  }
}
```

---

## Core Functions

### 1. `canonicalizeJSON(json)`

Primary function for JSON canonicalization.

**Signature:**
```kotlin
fun canonicalizeJSON(json: UDM): String
```

**Parameters:**
- `json: UDM` - JSON object, array, or scalar value

**Returns:** Canonical JSON string per RFC 8785

**Example:**
```kotlin
val obj = UDM.Object(mutableMapOf(
    "user" to UDM.Object(mutableMapOf(
        "lastName" to UDM.Scalar("Smith"),
        "firstName" to UDM.Scalar("John")
    )),
    "id" to UDM.Scalar(123)
))

canonicalizeJSON(obj)
// => {"id":123,"user":{"firstName":"John","lastName":"Smith"}}
```

**Rules Applied:**
1. Object keys sorted lexicographically (Unicode code point order)
2. All whitespace removed
3. Numbers normalized (no leading zeros, minimal representation)
4. Strings properly escaped per JSON spec
5. No trailing commas

---

### 2. `jcs(json)` - Short Alias

Convenient short form of `canonicalizeJSON`.

**Example:**
```kotlin
val canonical = jcs(myData)
```

---

### 3. `canonicalJSONHash(json, algorithm)`

Canonicalizes JSON and computes cryptographic hash in one step.

**Signature:**
```kotlin
fun canonicalJSONHash(
    json: UDM, 
    algorithm: String = "SHA-256"
): String
```

**Parameters:**
- `json: UDM` - JSON to canonicalize and hash
- `algorithm: String` - Hash algorithm (default: SHA-256)
  - Supported: SHA-1, SHA-256, SHA-384, SHA-512, MD5

**Returns:** Hex-encoded hash string

**Example:**
```kotlin
// Generate content-addressable identifier
val contentId = canonicalJSONHash(document, "SHA-256")

// Create cache key
val cacheKey = canonicalJSONHash(queryParams)

// Verify integrity
val expectedHash = canonicalJSONHash(receivedData)
if (expectedHash != providedHash) {
    throw SecurityException("Data tampering detected")
}
```

---

### 4. `jsonEquals(json1, json2)`

Compares two JSON values for semantic equality using canonicalization.

**Signature:**
```kotlin
fun jsonEquals(json1: UDM, json2: UDM): Boolean
```

**Why Use This?**
- Handles different key ordering
- Handles equivalent number representations (1.0 vs 1)
- More reliable than direct comparison

**Example:**
```kotlin
val obj1 = parseJSON("""{"b": 2, "a": 1}""")
val obj2 = parseJSON("""{"a": 1, "b": 2}""")

jsonEquals(obj1, obj2)  // => true (semantically equivalent)

// Number equivalence
jsonEquals(
    UDM.Scalar(1.0),
    UDM.Scalar(1)
)  // => true
```

---

## RFC 8785 Rules in Detail

### Object Key Sorting

Keys are sorted lexicographically by Unicode code point:

```kotlin
// Input
{
  "zebra": 1,
  "apple": 2,
  "Zebra": 3    // Capital Z comes first
}

// Output
{"Zebra":3,"apple":2,"zebra":1}
```

**Note:** Capital letters (U+0041-U+005A) come before lowercase (U+0061-U+007A).

### Number Normalization

```kotlin
// Integers without decimal point
1.0     => 1
42.0    => 42
-100.0  => -100

// Decimals without trailing zeros
1.50    => 1.5
2.250   => 2.25

// No leading zeros (except 0.x)
0.5     => 0.5   (not .5)

// Scientific notation for large/small
1e25    => 1e+25
1e-10   => 1e-10

// Special cases
0       => 0
-0.0    => 0   (no negative zero)
```

**Rejected Values:**
```kotlin
Double.NaN       // IllegalArgumentException
Double.INFINITY  // IllegalArgumentException
```

### String Escaping

```kotlin
// Special characters
"\""    => \"
"\\"    => \\
"\n"    => \n
"\t"    => \t
"\r"    => \r
"\b"    => \b
"\f"    => \f

// Control characters (U+0000 to U+001F)
"\u0000"  => \u0000
"\u001F"  => \u001f

// Unicode above U+001F remains literal
"€"       => €
"你好"    => 你好
```

### Whitespace Removal

All whitespace removed between structural elements:

```kotlin
// Input (pretty-printed)
{
  "name": "Alice",
  "age": 30
}

// Output (canonical)
{"age":30,"name":"Alice"}
```

---

## Use Case Examples

### 1. Webhook Signature Verification

**Scenario:** Verify HMAC signature of webhook payload.

```kotlin
// UTL-X Transformation
%utlx 1.0
input json
output json
---

function verifyWebhookSignature(
    payload: Object,
    signature: String,
    secret: String
): Boolean {
    let canonical = canonicalizeJSON(payload)
    let expectedSignature = hmacSHA256(canonical, secret)
    return expectedSignature == signature
}

// Main transformation
let isValid = verifyWebhookSignature(
    input.payload,
    input.headers["X-Signature"],
    env.WEBHOOK_SECRET
)

if (isValid) {
    {
        status: "verified",
        processedData: transformPayload(input.payload)
    }
} else {
    {
        status: "invalid",
        error: "Signature verification failed"
    }
}
```

**Real-World Application:**
- GitHub webhooks
- Stripe webhooks
- Twilio webhooks
- Any HMAC-based webhook verification

---

### 2. JWT Payload Signing (JWS)

**Scenario:** Create JSON Web Signature (JWS).

```kotlin
// Create JWT payload
val payload = UDM.Object(mutableMapOf(
    "sub" to UDM.Scalar("1234567890"),
    "name" to UDM.Scalar("John Doe"),
    "iat" to UDM.Scalar(1516239022),
    "admin" to UDM.Scalar(true)
))

// Canonicalize for signing
val canonical = canonicalizeJSON(payload)
// => {"admin":true,"iat":1516239022,"name":"John Doe","sub":"1234567890"}

// Create signature
val signature = signRS256(canonical, privateKey)

// Build JWS
val jws = buildJWS(header, canonical, signature)
```

**Benefits:**
- Deterministic signature
- Order-independent verification
- Standard compliance

---

### 3. Cache Key Generation

**Scenario:** Generate deterministic cache keys for API queries.

```kotlin
// Query parameters in any order
val query1 = UDM.Object(mutableMapOf(
    "page" to UDM.Scalar(1),
    "limit" to UDM.Scalar(10),
    "sort" to UDM.Scalar("name")
))

val query2 = UDM.Object(mutableMapOf(
    "limit" to UDM.Scalar(10),
    "sort" to UDM.Scalar("name"),
    "page" to UDM.Scalar(1)
))

// Same cache key despite different order
val key1 = canonicalJSONHash(query1)
val key2 = canonicalJSONHash(query2)
assert(key1 == key2)

// Use for caching
val cacheKey = "query:" + canonicalJSONHash(queryParams)
cache.get(cacheKey) ?: executeQuery(queryParams).also {
    cache.set(cacheKey, it)
}
```

---

### 4. Document Integrity Verification

**Scenario:** Verify document hasn't been tampered with.

```kotlin
// Original document
val document = parseJSON("""
{
    "title": "Contract",
    "version": 1,
    "content": "Terms and conditions...",
    "parties": ["Alice", "Bob"]
}
""")

// Compute integrity hash
val originalHash = canonicalJSONHash(document, "SHA-256")

// Store hash separately (e.g., blockchain, signature, database)
storeIntegrityHash(originalHash)

// Later: verify integrity
val currentHash = canonicalJSONHash(receivedDocument, "SHA-256")
if (currentHash != storedHash) {
    throw SecurityException("Document has been modified!")
}
```

**Applications:**
- Blockchain transactions
- Digital notarization
- Audit trails
- Version control

---

### 5. API Response Comparison

**Scenario:** Detect API response changes.

```kotlin
// Previous API response
val previousResponse = fetchFromCache("api_response")

// Current API response
val currentResponse = callAPI()

// Compare semantically (ignoring key order, whitespace)
if (!jsonEquals(previousResponse, currentResponse)) {
    // API response changed - invalidate cache, trigger alerts
    invalidateCache()
    alertOnAPIChange(diff(previousResponse, currentResponse))
}
```

---

### 6. Deterministic Signing for Distributed Systems

**Scenario:** Multiple nodes must produce identical signatures.

```kotlin
// Node A
val dataA = UDM.Object(mutableMapOf(
    "timestamp" to UDM.Scalar(1609459200),
    "transaction" to UDM.Scalar("tx_123"),
    "amount" to UDM.Scalar(100.50)
))
val signatureA = signData(canonicalizeJSON(dataA), nodeAKey)

// Node B (different object creation order)
val dataB = UDM.Object(mutableMapOf(
    "amount" to UDM.Scalar(100.50),
    "timestamp" to UDM.Scalar(1609459200),
    "transaction" to UDM.Scalar("tx_123")
))
val signatureB = signData(canonicalizeJSON(dataB), nodeBKey)

// Both nodes produce identical canonical form
assert(canonicalizeJSON(dataA) == canonicalizeJSON(dataB))
```

**Applications:**
- Distributed ledgers
- Multi-signature schemes
- Consensus protocols
- Replicated databases

---

## Integration Patterns

### Pattern 1: Transformation Pipeline

```utlx
%utlx 1.0
input json
output json
---

// Step 1: Transform data
let transformed = {
    id: input.userId,
    name: input.userName,
    email: input.userEmail,
    timestamp: now()
}

// Step 2: Canonicalize for signature
let canonical = canonicalizeJSON(transformed)

// Step 3: Sign
let signature = hmacSHA256(canonical, secret)

// Step 4: Output with signature
{
    data: transformed,
    signature: signature,
    canonicalForm: canonical
}
```

---

### Pattern 2: Conditional Processing

```utlx
// Only process if signature is valid
let canonical = canonicalizeJSON(input.data)
let isValid = verifySignature(canonical, input.signature)

if (isValid) {
    processData(input.data)
} else {
    {
        error: "Invalid signature",
        received: canonical,
        expectedHash: hash(canonical)
    }
}
```

---

### Pattern 3: Idempotency Keys

```utlx
// Generate idempotency key from request
let idempotencyKey = canonicalJSONHash({
    endpoint: input.endpoint,
    method: input.method,
    body: input.body,
    headers: selectHeaders(input.headers)  // Subset of headers
})

// Check if already processed
if (cache.has(idempotencyKey)) {
    cache.get(idempotencyKey)  // Return cached response
} else {
    let result = processRequest(input)
    cache.set(idempotencyKey, result)
    result
}
```

---

## Performance Considerations

### Benchmarks

Approximate performance (JVM, modern hardware):

| Input Size | Canonicalization Time | Hash Time (SHA-256) |
|------------|----------------------|---------------------|
| 1 KB | ~0.1 ms | ~0.05 ms |
| 10 KB | ~0.5 ms | ~0.2 ms |
| 100 KB | ~3 ms | ~1 ms |
| 1 MB | ~25 ms | ~8 ms |

### Optimization Tips

1. **Cache canonical forms** if the same data is processed multiple times
2. **Use `canonicalJSONHash()` directly** instead of separate canonicalize + hash
3. **Consider streaming** for very large documents (not yet implemented)
4. **Validate input size** before canonicalization

```kotlin
// Efficient: hash in one step
val hash = canonicalJSONHash(data)

// Less efficient: two steps
val canonical = canonicalizeJSON(data)
val hash = sha256(canonical)
```

---

## Testing and Validation

### RFC 8785 Compliance Testing

```kotlin
@Test
fun `RFC 8785 test vector 1`() {
    val input = UDM.Object(mutableMapOf(
        "numbers" to UDM.Array(listOf(
            UDM.Scalar(333333333.33333329),
            UDM.Scalar(1E30),
            UDM.Scalar(4.5),
            UDM.Scalar(6),
            UDM.Scalar(2E-3)
        )),
        "string" to UDM.Scalar("\u20ac$\u000F\u000aA'\u0042\u0022\u005c\\\"/"),
        "literals" to UDM.Array(listOf(
            UDM.Scalar(null),
            UDM.Scalar(true),
            UDM.Scalar(false)
        ))
    ))
    
    val expected = """{"literals":[null,true,false],"numbers":[333333333.3333333,1e+30,4.5,6,0.002],"string":"€${'$'}\u000f\nA'B\"\\\\/"}"""
    
    assertEquals(expected, canonicalizeJSON(input))
}
```

### Validation Utilities

```kotlin
// Check if string is valid canonical JSON
isCanonicalJSON(jsonString)

// Get size in bytes
canonicalJSONSize(data)

// Debug information
canonicalizationDebugInfo(data)
```

---

## Common Pitfalls

### ❌ Don't: Use for Pretty-Printing

```kotlin
// ❌ WRONG: Canonicalization removes whitespace
val prettyJSON = canonicalizeJSON(data)  // No whitespace!
```

**Solution:** Use JSON serializer with pretty-print option

---

### ❌ Don't: Assume Original Order Preserved

```kotlin
// ❌ WRONG: Object keys will be reordered
val input = """{"z": 1, "a": 2}"""
val canonical = canonicalizeJSON(parseJSON(input))
// Result: {"a":2,"z":1}  // Keys sorted!
```

---

### ❌ Don't: Use for Non-JSON Data

```kotlin
// ❌ WRONG: NaN and Infinity not allowed
canonicalizeJSON(UDM.Scalar(Double.NaN))  // Exception!
```

---

### ✅ Do: Cache Canonical Forms

```kotlin
// ✅ GOOD: Cache if used multiple times
val canonical = canonicalizeJSON(data)
val signature1 = signHMAC(canonical, secret1)
val signature2 = signHMAC(canonical, secret2)
val hash = sha256(canonical)
```

---

## Security Considerations

### ⚠️ Important Security Notes

1. **Canonicalization ≠ Validation**
   - JCS produces deterministic form
   - Does NOT validate JSON schema or business rules
   - Always validate data separately

2. **Hash Algorithm Selection**
   - Use SHA-256 or stronger for security
   - Avoid MD5 and SHA-1 for new applications
   - Consider SHA-512 for high-security needs

3. **Signature Verification**
   - Always use constant-time comparison for signatures
   - Don't log or expose signatures in errors
   - Implement rate limiting on verification endpoints

4. **Input Size Limits**
   - Set maximum input size to prevent DoS
   - Consider memory usage for large documents
   - Implement timeouts for canonicalization

```kotlin
// Good: Validate before processing
if (inputSize > MAX_ALLOWED_SIZE) {
    throw SecurityException("Input too large")
}

val canonical = canonicalizeJSON(data)

// Good: Constant-time comparison
if (!constantTimeEquals(signature, expectedSignature)) {
    throw SecurityException("Invalid signature")
}
```

---

## Migration Guide

### From Manual Serialization

**Before:**
```kotlin
val json = """{"b":${obj.b},"a":${obj.a}}"""  // Manual ordering
```

**After:**
```kotlin
val canonical = canonicalizeJSON(obj)  // Automatic RFC compliance
```

---

### From DataWeave

**DataWeave (no built-in canonicalization):**
```dataweave
%dw 2.0
output application/json
---
// Manual key sorting required
payload orderBy $
```

**UTL-X:**
```utlx
canonicalizeJSON(payload)  // RFC 8785 compliant
```

---

## References

- [RFC 8785 - JSON Canonicalization Scheme (JCS)](https://www.rfc-editor.org/rfc/rfc8785)
- [RFC 7515 - JSON Web Signature (JWS)](https://www.rfc-editor.org/rfc/rfc7515)
- [ECMA-262 - ECMAScript Language Specification](https://tc39.es/ecma262/)

---

## Appendix: Full API Reference

```kotlin
// Primary functions
fun canonicalizeJSON(json: UDM): String
fun jcs(json: UDM): String

// Hash utilities
fun canonicalJSONHash(json: UDM, algorithm: String = "SHA-256"): String

// Comparison
fun jsonEquals(json1: UDM, json2: UDM): Boolean

// Validation
fun isCanonicalJSON(canonicalJSON: String): Boolean
fun canonicalJSONSize(json: UDM): Int
fun canonicalizationDebugInfo(json: UDM): String
```

---

## Support

For issues, feature requests, or questions:
- GitHub Issues: [github.com/grauwen/utl-x/issues](https://github.com/grauwen/utl-x/issues)
- Documentation: [utl-x.org/docs/json-canonicalization](https://utl-x.org/docs/json-canonicalization)
- RFC 8785: [rfc-editor.org/rfc/rfc8785](https://www.rfc-editor.org/rfc/rfc8785)