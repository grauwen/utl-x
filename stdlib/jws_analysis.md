# JSON Web Signature (JWS) Implementation Analysis for UTL-X

## Executive Summary

**Recommendation:** Implement JWS functions in a **separate security module** (`stdlib-security`) rather than core stdlib.

---

## What is JWS?

JSON Web Signature (RFC 7515) provides a standardized way to digitally sign JSON content.

### JWS Structure

```
[Header].[Payload].[Signature]
   ↓        ↓         ↓
Base64   Base64    Base64
 URL      URL       URL
```

**Complete Example:**
```
eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0IiwibmFtZSI6IkpvaG4ifQ.signature_here
```

**Decoded:**
```json
// Header
{
  "alg": "HS256",
  "typ": "JWT"
}

// Payload
{
  "sub": "1234",
  "name": "John"
}

// Signature = sign(base64url(header) + "." + base64url(payload), secret)
```

---

## Arguments FOR Implementation

| Reason | Weight | Details |
|--------|--------|---------|
| **Industry standard** | 🟢🟢🟢 | RFC 7515 is universally adopted |
| **Integration need** | 🟢🟢🟢 | Essential for API security |
| **Complements JCS** | 🟢🟢 | Natural extension of canonicalization |
| **JWT foundation** | 🟢🟢 | Required for JWT (RFC 7519) |
| **Modern APIs** | 🟢🟢 | Oauth2, OpenID Connect use JWS |

## Arguments AGAINST Core Implementation

| Reason | Weight | Details |
|--------|--------|---------|
| **Security sensitive** | 🔴🔴🔴 | Crypto is dangerous if wrong |
| **Heavy dependencies** | 🔴🔴 | Requires robust crypto libraries |
| **Maintenance burden** | 🔴🔴 | Security patches, algorithm updates |
| **Complex algorithms** | 🔴🔴 | RS256, ES256, PS256, etc. |
| **Key management** | 🔴 | Private keys, key rotation, JWKS |

---

## Recommended Approach: Tiered Implementation

### ✅ Tier 1: JWS Parsing (Core - Read-Only)

**Include in core stdlib** - Safe, read-only operations:

```kotlin
// stdlib/src/main/kotlin/org/apache/utlx/stdlib/jws/JWSFunctions.kt

/**
 * Decodes JWS token WITHOUT signature verification
 * SECURITY WARNING: Does not verify signature
 */
fun decodeJWS(token: String): Map<String, Any?> {
    val parts = token.split(".")
    if (parts.size != 3) throw IllegalArgumentException("Invalid JWS format")
    
    return mapOf(
        "header" to decodeBase64UrlJSON(parts[0]),
        "payload" to decodeBase64UrlJSON(parts[1]),
        "signature" to parts[2],
        "verified" to false  // Explicitly mark unverified
    )
}

/**
 * Extracts payload from JWS WITHOUT verification
 * SECURITY WARNING: Use only with trusted tokens
 */
fun getJWSPayload(token: String): Map<String, Any?> {
    return decodeJWS(token)["payload"] as Map<String, Any?>
}

/**
 * Gets JWS algorithm from header
 */
fun getJWSAlgorithm(token: String): String {
    val header = decodeJWS(token)["header"] as Map<String, Any?>
    return header["alg"] as String
}
```

### ⚠️ Tier 2: JWS Creation & Verification (Security Module)

**Separate module** - Security-sensitive operations:

```kotlin
// stdlib-security/src/main/kotlin/org/apache/utlx/stdlib/jws/JWSSignature.kt

/**
 * Creates a JWS token with signature
 * Requires stdlib-security module
 */
fun createJWS(
    payload: Map<String, Any?>,
    secret: String,
    algorithm: String = "HS256"
): String {
    // Full implementation with proper crypto
}

/**
 * Verifies JWS signature and returns payload
 */
fun verifyJWS(
    token: String,
    secret: String,
    algorithm: String? = null  // Auto-detect from header
): Map<String, Any?> {
    // Full verification with signature check
}

/**
 * Verifies JWS with public key (RS256, ES256)
 */
fun verifyJWSWithPublicKey(
    token: String,
    publicKey: PublicKey,
    algorithm: String? = null
): Map<String, Any?>

/**
 * Verifies JWS with JWKS (JSON Web Key Set)
 */
fun verifyJWSWithJWKS(
    token: String,
    jwksUrl: String,
    kid: String? = null  // Key ID from header
): Map<String, Any?>
```

---

## Integration with JCS

JWS and JCS work together:

```kotlin
// Step 1: Canonicalize payload using JCS
val payload = mapOf(
    "sub" to "user123",
    "name" to "Alice",
    "exp" to 1735689600
)
val canonical = canonicalizeJSON(toUDM(payload))

// Step 2: Create JWS
val jws = createJWS(payload, secret, "HS256")

// The JWS implementation uses canonical JSON internally
// for deterministic signatures
```

---

## Supported Algorithms

### HMAC (Symmetric - Secret Key)
- **HS256** - HMAC with SHA-256 (✅ Must support)
- **HS384** - HMAC with SHA-384 (⚠️ Should support)
- **HS512** - HMAC with SHA-512 (⚠️ Should support)

### RSA (Asymmetric - Public/Private Key)
- **RS256** - RSA with SHA-256 (✅ Must support)
- **RS384** - RSA with SHA-384 (⚠️ Should support)
- **RS512** - RSA with SHA-512 (⚠️ Should support)

### ECDSA (Asymmetric - Elliptic Curve)
- **ES256** - ECDSA with P-256 and SHA-256 (✅ Must support)
- **ES384** - ECDSA with P-384 and SHA-384 (⚠️ Should support)
- **ES512** - ECDSA with P-521 and SHA-512 (⚠️ Should support)

### RSA-PSS (Asymmetric - Probabilistic Signature Scheme)
- **PS256** - RSA-PSS with SHA-256 (⚠️ Optional)
- **PS384** - RSA-PSS with SHA-384 (⚠️ Optional)
- **PS512** - RSA-PSS with SHA-512 (⚠️ Optional)

### None Algorithm
- **none** - No signature (❌ DANGEROUS - should reject by default)

---

## File Organization

```
stdlib/
├── src/main/kotlin/org/apache/utlx/stdlib/
│   └── jws/
│       ├── JWSBasicFunctions.kt          ✅ CORE (decode only)
│       └── README.md                      (security warnings)
│
stdlib-security/                           ⚠️ OPTIONAL MODULE
├── src/main/kotlin/org/apache/utlx/stdlib/jws/
│   ├── JWSSignature.kt                    (sign & verify)
│   ├── JWSAlgorithms.kt                   (algorithm implementations)
│   ├── JWKSProvider.kt                    (JWKS support)
│   └── KeyManagement.kt                   (key handling)
│
└── build.gradle.kts
    dependencies {
        implementation("com.nimbusds:nimbus-jose-jwt:9.37.3")
        // OR
        implementation("com.auth0:java-jwt:4.4.0")
    }
```

---

## Use Cases

### Use Case 1: API Token Verification (Read-Only)

```utlx
import { getJWSPayload, getJWSAlgorithm } from "std/jws"

let token = input.headers.Authorization.replace("Bearer ", "")

// Decode to check structure (no verification)
let payload = getJWSPayload(token)
let algorithm = getJWSAlgorithm(token)

if (algorithm != "HS256") {
  error("Unsupported algorithm")
}

// Route based on payload
{
  userId: payload.sub,
  endpoint: routeForUser(payload.sub)
}
```

### Use Case 2: Creating Signed Tokens (Security Module)

```utlx
import { createJWS } from "std/jws/security"

let payload = {
  sub: user.id,
  name: user.name,
  email: user.email,
  exp: now() + 3600,  // 1 hour
  iat: now()
}

let token = createJWS(payload, env.JWT_SECRET, "HS256")

{
  access_token: token,
  token_type: "Bearer",
  expires_in: 3600
}
```

### Use Case 3: Verifying Signatures (Security Module)

```utlx
import { verifyJWS } from "std/jws/security"

let token = input.headers.Authorization.replace("Bearer ", "")

try {
  let payload = verifyJWS(token, env.JWT_SECRET)
  
  // Token is valid
  {
    authenticated: true,
    user: payload
  }
} catch (err) {
  // Invalid signature or expired
  {
    authenticated: false,
    error: err.message
  }
}
```

### Use Case 4: JWKS Verification (Security Module)

```utlx
import { verifyJWSWithJWKS } from "std/jws/security"

let token = input.headers.Authorization.replace("Bearer ", "")

// Verify using public keys from JWKS endpoint
let payload = verifyJWSWithJWKS(
  token,
  "https://auth.example.com/.well-known/jwks.json"
)

{
  authenticated: true,
  claims: payload
}
```

---

## Security Considerations

### ⚠️ Critical Security Rules

**1. Decoding ≠ Verification**
```utlx
// ❌ DANGEROUS: Does not verify signature
let payload = decodeJWS(untrustedToken)

// ✅ SAFE: Verifies signature (requires security module)
let payload = verifyJWS(untrustedToken, secret)
```

**2. Algorithm Verification**
```kotlin
// ❌ DANGEROUS: Accept any algorithm
fun verifyJWS(token: String, secret: String): Map<String, Any?> {
    val algorithm = getJWSAlgorithm(token)  // Attacker controls this!
    // ... verify with that algorithm
}

// ✅ SAFE: Specify allowed algorithms
fun verifyJWS(
    token: String, 
    secret: String,
    allowedAlgorithms: Set<String> = setOf("HS256")
): Map<String, Any?> {
    val algorithm = getJWSAlgorithm(token)
    if (algorithm !in allowedAlgorithms) {
        throw SecurityException("Algorithm $algorithm not allowed")
    }
    // ... verify
}
```

**3. "none" Algorithm Attack**
```kotlin
// ✅ ALWAYS reject "none" by default
if (algorithm == "none") {
    throw SecurityException("'none' algorithm not allowed")
}
```

**4. Key Confusion Attack**
```kotlin
// ❌ DANGEROUS: Using same key for different algorithms
verifyJWS(token, key, algorithm = "HS256")  // HMAC with secret
verifyJWS(token, key, algorithm = "RS256")  // RSA with public key

// ✅ SAFE: Separate keys for symmetric/asymmetric
verifyJWSWithSecret(token, secret, "HS256")
verifyJWSWithPublicKey(token, publicKey, "RS256")
```

---

## Dependencies

### Option 1: Nimbus JOSE+JWT (Recommended)

```kotlin
// build.gradle.kts (stdlib-security)
dependencies {
    implementation("com.nimbusds:nimbus-jose-jwt:9.37.3")
}
```

**Pros:**
- Comprehensive RFC support (JWS, JWE, JWK, JWKS)
- Well-maintained, security-focused
- Used by major projects (Spring Security, etc.)
- Excellent documentation

**Cons:**
- Larger dependency
- More complex API

### Option 2: Auth0 java-jwt

```kotlin
dependencies {
    implementation("com.auth0:java-jwt:4.4.0")
}
```

**Pros:**
- Simpler API
- Smaller footprint
- Good for basic JWT use cases

**Cons:**
- Less comprehensive (JWS-focused)
- Limited JWE support

### Option 3: Pure Implementation

**Pros:**
- No external dependencies
- Full control

**Cons:**
- High security risk
- Significant development effort
- Ongoing maintenance burden
- ❌ **NOT RECOMMENDED** for crypto

---

## Implementation Complexity

### Basic (Decode Only) - Low Complexity ✅

```kotlin
fun decodeJWS(token: String): Map<String, Any?> {
    val parts = token.split(".")
    return mapOf(
        "header" to base64UrlDecodeJSON(parts[0]),
        "payload" to base64UrlDecodeJSON(parts[1]),
        "signature" to parts[2]
    )
}
```

**Complexity:** ~50 lines of code  
**Risk:** Low (read-only)  
**Dependencies:** None

### Full Implementation - High Complexity ⚠️

```kotlin
fun verifyJWS(token: String, secret: String, alg: String): Map<String, Any?> {
    // Parse token
    // Validate algorithm
    // Handle different signature algorithms (HMAC, RSA, ECDSA)
    // Key management
    // Timing-safe comparison
    // Error handling
    // Algorithm parameter validation
    // ...
}
```

**Complexity:** ~500+ lines of code  
**Risk:** High (security-critical)  
**Dependencies:** Crypto libraries required

---

## Comparison with Other Languages

| Language/Library | JWS Support | Approach |
|-----------------|-------------|----------|
| **DataWeave** | ❌ No | Use external services |
| **JSONata** | ❌ No | Not in scope |
| **Node.js** | ✅ Yes | `jsonwebtoken` library |
| **Python** | ✅ Yes | `PyJWT` library |
| **Java** | ✅ Yes | Multiple libraries |
| **Go** | ✅ Yes | `golang-jwt/jwt` |

**Pattern:** Most languages use external libraries, not built-in.

---

## Recommended Implementation Strategy

### Phase 1: Core (Basic Decode) ✅

**Timeline:** 1-2 weeks  
**Files:**
- `JWSBasicFunctions.kt` (decode, parse, inspect)
- `JWSBasicTest.kt` (comprehensive tests)
- `jws_basic_guide.md` (documentation)

**Functions:**
```kotlin
decodeJWS(token: String)
getJWSPayload(token: String)
getJWSHeader(token: String)
getJWSAlgorithm(token: String)
isJWSFormat(token: String)
```

### Phase 2: Security Module (Sign & Verify) ⚠️

**Timeline:** 4-6 weeks  
**Files:**
- `JWSSignature.kt` (create, verify)
- `JWSAlgorithms.kt` (algorithm implementations)
- `JWKSProvider.kt` (JWKS support)
- `JWSSecurityTest.kt` (security tests)
- `jws_security_guide.md` (security documentation)

**Functions:**
```kotlin
createJWS(payload, secret, alg)
verifyJWS(token, secret, allowedAlgs)
verifyJWSWithPublicKey(token, publicKey, alg)
verifyJWSWithJWKS(token, jwksUrl, kid)
```

### Phase 3: Advanced Features (Optional) 🔮

**Timeline:** 2-4 weeks  
**Features:**
- JWE (JSON Web Encryption)
- JWK (JSON Web Key)
- Key rotation support
- Custom claim validation
- Token refresh mechanisms

---

## Decision Matrix

| Factor | Weight | Core Only | + Security Module | + Advanced |
|--------|--------|-----------|-------------------|------------|
| **Development effort** | High | Low | Medium | High |
| **Security risk** | High | Low | Medium | High |
| **User value** | High | Low | High | Medium |
| **Maintenance burden** | High | Low | Medium | High |
| **Dependencies** | Medium | None | 1-2 | 3-5 |
| **Testing complexity** | High | Low | High | Very High |

---

## Final Recommendation

### ✅ Implement: Basic JWS Functions (Core)

**Include in core stdlib:**
- `decodeJWS()` - Parse token structure
- `getJWSPayload()` - Extract payload (unverified)
- `getJWSAlgorithm()` - Get algorithm from header
- Prominent security warnings

**Rationale:**
- Low risk (read-only)
- Useful for inspection and routing
- No crypto dependencies
- Clear documentation prevents misuse

### ⚠️ Implement: JWS Signature Functions (Security Module)

**Separate security module:**
- `createJWS()` - Sign tokens
- `verifyJWS()` - Verify signatures
- `verifyJWSWithJWKS()` - JWKS support
- Full algorithm support (HS256, RS256, ES256)

**Rationale:**
- Essential for production use
- Opt-in security dependency
- Professional implementation required
- Use established library (Nimbus JOSE+JWT)

### 🔮 Future: Advanced Features

**Consider for later versions:**
- JWE (encryption)
- Custom claim validation
- Token refresh flows
- Key rotation

---

## Next Steps

1. ✅ **Review this analysis** with team
2. ✅ **Decide on scope** (core + security module?)
3. ⚠️ **Choose crypto library** (Nimbus JOSE+JWT recommended)
4. ⚠️ **Create basic functions** (decode-only, safe)
5. ⚠️ **Security module design** (if approved)
6. ⚠️ **Security audit** (before release)
7. ⚠️ **Documentation** (emphasis on security)

---

## Summary

| Question | Answer |
|----------|--------|
| **Should UTL-X have JWS?** | ✅ YES (tiered approach) |
| **In core stdlib?** | ⚠️ PARTIAL (decode only) |
| **Full implementation?** | ⚠️ SEPARATE MODULE (security) |
| **Use external library?** | ✅ YES (Nimbus JOSE+JWT) |
| **When to implement?** | ⚠️ After JCS is stable |
| **Security review needed?** | ✅ YES (mandatory) |

**Bottom line:** JWS is valuable for UTL-X but should be implemented carefully with proper separation between safe read-only operations (core) and security-critical operations (separate module).