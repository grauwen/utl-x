# JWS + JWT + JCS Integration Guide

## Understanding the Relationship

These three RFC standards work together in the authentication and signature ecosystem:

```
┌─────────────────────────────────────────────────┐
│  JWT (RFC 7519) - JSON Web Token                │
│  ├─ Claims: sub, exp, iat, aud, iss, etc.      │
│  └─ Use Case: Authentication/Authorization      │
└──────────────────┬──────────────────────────────┘
                   │ uses ↓
┌─────────────────────────────────────────────────┐
│  JWS (RFC 7515) - JSON Web Signature            │
│  ├─ Structure: header.payload.signature         │
│  ├─ Algorithms: HS256, RS256, ES256, etc.       │
│  └─ Use Case: Digital signatures for JSON       │
└──────────────────┬──────────────────────────────┘
                   │ uses ↓
┌─────────────────────────────────────────────────┐
│  JCS (RFC 8785) - JSON Canonicalization         │
│  ├─ Keys sorted, whitespace removed             │
│  ├─ Deterministic representation                │
│  └─ Use Case: Consistent signatures             │
└─────────────────────────────────────────────────┘
```

---

## Quick Reference

| Aspect | JCS | JWS | JWT |
|--------|-----|-----|-----|
| **RFC** | 8785 | 7515 | 7519 |
| **Purpose** | Canonical JSON | Signature format | Auth tokens |
| **Output** | JSON string | Base64 token | JWS token |
| **Scope** | JSON formatting | Any JSON signing | Auth/authz only |
| **Use with others** | Used IN JWS | Container for JWT | Specific USE of JWS |

---

## UTL-X Functions Available

### JCS (JSON Canonicalization)
```kotlin
// From JSONCanonicalization.kt
canonicalizeJSON(json: UDM): String
jcs(json: UDM): String
canonicalJSONHash(json: UDM, algorithm: String): String
jsonEquals(json1: UDM, json2: UDM): Boolean
```

### JWS (JSON Web Signature) - Basic
```kotlin
// From JWSBasicFunctions.kt
decodeJWS(token: String): Map<String, Any?>
getJWSPayload(token: String): Map<String, Any?>
getJWSHeader(token: String): Map<String, Any?>
getJWSAlgorithm(token: String): String
isJWSFormat(token: String): Boolean
```

### JWT (JSON Web Token) - Basic
```kotlin
// From JWTFunctions.kt (if implemented)
decodeJWT(token: String): Map<String, Any?>
getJWTClaims(token: String): Map<String, Any?>
isJWTExpired(token: String): Boolean
```

---

## Complete Flow: Creating a Signed Token

### Step 1: Create Payload (JWT Claims)

```kotlin
val payload = UDM.Object(mutableMapOf(
    "sub" to UDM.Scalar("user123"),
    "name" to UDM.Scalar("Alice Smith"),
    "email" to UDM.Scalar("alice@example.com"),
    "role" to UDM.Scalar("admin"),
    "iat" to UDM.Scalar(System.currentTimeMillis() / 1000),
    "exp" to UDM.Scalar(System.currentTimeMillis() / 1000 + 3600) // 1 hour
))
```

### Step 2: Canonicalize Payload (JCS)

```kotlin
// Create deterministic representation
val canonicalPayload = canonicalizeJSON(payload)
// => {"email":"alice@example.com","exp":1735689600,"iat":1735686000,"name":"Alice Smith","role":"admin","sub":"user123"}
```

### Step 3: Create JWS Token (Requires Security Module)

```kotlin
// This would require stdlib-security module
val header = mapOf(
    "alg" to "HS256",
    "typ" to "JWT"
)

val token = createJWS(
    header = header,
    payload = canonicalPayload,
    secret = "your-secret-key"
)
// => eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJlbWFpbC...signature
```

### Step 4: Use Token

```kotlin
// Client includes in request
val authHeader = "Bearer $token"
```

---

## Complete Flow: Verifying a Signed Token

### Step 1: Extract Token from Request

```utlx
let authHeader = input.headers.Authorization
let token = authHeader.replace("Bearer ", "")
```

### Step 2: Validate Format (JWS)

```utlx
if (!isJWSFormat(token)) {
    error("Invalid token format")
}
```

### Step 3: Check Algorithm

```utlx
let algorithm = getJWSAlgorithm(token)

if (algorithm == "none") {
    error("Unsigned tokens not allowed")
}

if (algorithm != "HS256") {
    error("Unsupported algorithm")
}
```

### Step 4: Decode (Without Verification - for inspection)

```utlx
// Get payload without verification (for routing, logging)
let payload = getJWSPayload(token)
let userId = payload.sub
let userRole = payload.role

// Log for debugging (not production!)
log("Token received for user: " + userId)
```

### Step 5: Verify Signature (Requires Security Module)

```utlx
// This requires stdlib-security module
let verifiedPayload = verifyJWS(token, env.JWT_SECRET, "HS256")

// Now we know signature is valid
{
    authenticated: true,
    user: {
        id: verifiedPayload.sub,
        name: verifiedPayload.name,
        role: verifiedPayload.role
    }
}
```

### Step 6: Check Expiration (JWT)

```utlx
if (isJWTExpired(token)) {
    {
        authenticated: false,
        error: "Token expired"
    }
}
```

---

## Use Case Examples

### Example 1: API Gateway - Token Inspection (No Verification)

**Scenario:** Route requests based on token claims without verifying signature (upstream already verified).

```utlx
%utlx 1.0
input json
output json
---

// Token already verified by upstream API gateway
let token = input.headers.Authorization.replace("Bearer ", "")

// Decode for routing (safe - already verified upstream)
let payload = getJWSPayload(token)
let userRole = payload.role

// Route based on role
let endpoint = match userRole {
    "admin" => "https://admin-api.example.com",
    "user" => "https://user-api.example.com",
    _ => "https://default-api.example.com"
}

{
    targetEndpoint: endpoint,
    userId: payload.sub,
    userRole: userRole
}
```

---

### Example 2: Webhook Signature with JCS

**Scenario:** Create and verify webhook signatures using canonical JSON.

```utlx
// Creating webhook (sender side)
let webhookPayload = {
    event: "user.created",
    timestamp: now(),
    data: {
        userId: "12345",
        email: "user@example.com"
    }
}

// Canonicalize for deterministic signature
let canonical = canonicalizeJSON(webhookPayload)

// Create signature
let signature = hmacSHA256(canonical, env.WEBHOOK_SECRET)

// Send webhook
{
    payload: webhookPayload,
    signature: signature
}
```

```utlx
// Verifying webhook (receiver side)
let receivedPayload = input.payload
let receivedSignature = input.signature

// Canonicalize received payload
let canonical = canonicalizeJSON(receivedPayload)

// Verify signature
let expectedSignature = hmacSHA256(canonical, env.WEBHOOK_SECRET)

if (expectedSignature == receivedSignature) {
    processWebhook(receivedPayload)
} else {
    {
        error: "Invalid signature",
        status: 401
    }
}
```

---

### Example 3: Token Refresh Flow

**Scenario:** Implement token refresh with JWS.

```utlx
// Check if token is expired
let accessToken = input.headers.Authorization.replace("Bearer ", "")

if (isJWTExpired(accessToken)) {
    // Check refresh token
    let refreshToken = input.body.refresh_token
    
    if (!isJWSFormat(refreshToken)) {
        error("Invalid refresh token format")
    }
    
    // Verify refresh token (requires security module)
    let refreshPayload = verifyJWS(refreshToken, env.REFRESH_SECRET)
    
    // Create new access token
    let newPayload = {
        sub: refreshPayload.sub,
        name: refreshPayload.name,
        exp: now() + 3600,  // 1 hour
        iat: now()
    }
    
    let newAccessToken = createJWS(newPayload, env.JWT_SECRET, "HS256")
    
    {
        access_token: newAccessToken,
        token_type: "Bearer",
        expires_in: 3600
    }
} else {
    // Token still valid
    {
        message: "Token still valid",
        expires_in: getJWTClaim(accessToken, "exp") - now()
    }
}
```

---

### Example 4: Multi-Tenant Token Routing

**Scenario:** Route requests based on tenant ID in token.

```utlx
let token = input.headers.Authorization.replace("Bearer ", "")

// Decode to get tenant
let payload = getJWSPayload(token)
let tenantId = payload.tenant_id

// Route to tenant-specific service
let tenantEndpoint = getTenantEndpoint(tenantId)

// Forward request with original token
{
    method: input.method,
    url: tenantEndpoint + input.path,
    headers: {
        Authorization: input.headers.Authorization,
        X-Tenant-ID: tenantId
    },
    body: input.body
}
```

---

### Example 5: Token Claims Transformation

**Scenario:** Extract and transform token claims for downstream services.

```utlx
let token = input.headers.Authorization.replace("Bearer ", "")
let payload = getJWSPayload(token)

// Transform claims for internal service
{
    user: {
        id: payload.sub,
        email: payload.email,
        displayName: payload.name,
        roles: payload.roles || [],
        metadata: {
            tenantId: payload.tenant_id,
            accountType: payload.account_type,
            createdAt: payload.iat,
            expiresAt: payload.exp
        }
    },
    request: {
        method: input.method,
        path: input.path,
        query: input.query
    }
}
```

---

## Security Best Practices

### ✅ DO

```utlx
// ✅ Check token format
if (!isJWSFormat(token)) {
    error("Invalid token format")
}

// ✅ Validate algorithm
let algorithm = getJWSAlgorithm(token)
if (algorithm not in ["HS256", "RS256"]) {
    error("Unsupported algorithm")
}

// ✅ Verify signature (with security module)
let payload = verifyJWS(token, secret, "HS256")

// ✅ Check expiration
if (isJWTExpired(token)) {
    error("Token expired")
}

// ✅ Use canonical JSON for signatures
let canonical = canonicalizeJSON(payload)
let signature = hmacSHA256(canonical, secret)
```

### ❌ DON'T

```utlx
// ❌ Don't trust decoded payload without verification
let payload = decodeJWS(token)  // NOT verified!
if (payload.role == "admin") {
    // DANGER: Attacker can forge this!
}

// ❌ Don't accept "none" algorithm
if (getJWSAlgorithm(token) == "none") {
    // DANGER: No signature!
}

// ❌ Don't use decoded token for authorization
let userId = getJWSPayload(token).sub
deleteUser(userId)  // DANGER: Not verified!

// ❌ Don't log sensitive token data
log("Token: " + token)  // Exposes secret!
```

---

## Integration Checklist

### For Core Functions (Safe - No Signature Verification)

- [x] JCS implementation (`canonicalizeJSON`)
- [x] JWS decoding (`decodeJWS`, `getJWSPayload`)
- [x] JWT basic functions (`decodeJWT`, `getJWTClaims`)
- [x] Format validation (`isJWSFormat`)
- [x] Algorithm inspection (`getJWSAlgorithm`)
- [x] Security warnings in documentation

### For Security Module (Signature Verification)

- [ ] Choose crypto library (Nimbus JOSE+JWT recommended)
- [ ] Implement `createJWS()` (token creation)
- [ ] Implement `verifyJWS()` (signature verification)
- [ ] Implement HMAC algorithms (HS256, HS384, HS512)
- [ ] Implement RSA algorithms (RS256, RS384, RS512)
- [ ] Implement ECDSA algorithms (ES256, ES384, ES512)
- [ ] JWKS support (`verifyJWSWithJWKS`)
- [ ] Key management utilities
- [ ] Security audit
- [ ] Comprehensive testing

---

## Dependencies

### Core (No Dependencies)
```kotlin
// Pure Kotlin implementation
// Uses only:
// - java.util.Base64 (JDK)
// - JSON parser (internal)
```

### Security Module (Requires Crypto Library)
```kotlin
// build.gradle.kts (stdlib-security)
dependencies {
    implementation("com.nimbusds:nimbus-jose-jwt:9.37.3")
    // OR
    implementation("com.auth0:java-jwt:4.4.0")
}
```

---

## File Organization

```
stdlib/
├── src/main/kotlin/org/apache/utlx/stdlib/
│   ├── json/
│   │   └── JSONCanonicalization.kt        ✅ JCS (RFC 8785)
│   │
│   ├── jws/
│   │   └── JWSBasicFunctions.kt           ✅ JWS decode (RFC 7515)
│   │
│   └── jwt/
│       └── JWTFunctions.kt                ✅ JWT decode (RFC 7519)
│
stdlib-security/                           ⚠️ OPTIONAL MODULE
└── src/main/kotlin/org/apache/utlx/stdlib/
    ├── jws/
    │   ├── JWSSignature.kt                ⚠️ Sign & verify
    │   ├── JWSAlgorithms.kt               ⚠️ Algorithm implementations
    │   └── JWKSProvider.kt                ⚠️ JWKS support
    │
    └── jwt/
        └── JWTVerification.kt             ⚠️ JWT verification
```

---

## Testing Strategy

### Unit Tests
```kotlin
// Test JCS
@Test fun `canonicalizeJSON sorts keys`()
@Test fun `canonicalizeJSON removes whitespace`()

// Test JWS decode
@Test fun `decodeJWS parses valid token`()
@Test fun `decodeJWS rejects invalid format`()

// Test JWT
@Test fun `isJWTExpired detects expired tokens`()
```

### Integration Tests
```kotlin
@Test fun `end-to-end token creation and verification`() {
    // 1. Create payload
    val payload = createTestPayload()
    
    // 2. Canonicalize
    val canonical = canonicalizeJSON(payload)
    
    // 3. Create JWS (requires security module)
    val token = createJWS(canonical, secret, "HS256")
    
    // 4. Decode
    val decoded = decodeJWS(token)
    
    // 5. Verify (requires security module)
    val verified = verifyJWS(token, secret, "HS256")
    
    assertEquals(payload, verified)
}
```

---

## Summary

| Component | Status | Module | Use Case |
|-----------|--------|--------|----------|
| **JCS** | ✅ Implemented | Core | Canonical JSON for signatures |
| **JWS Decode** | ✅ Implemented | Core | Token inspection (read-only) |
| **JWT Decode** | ✅ Implemented | Core | Claims extraction (read-only) |
| **JWS Sign** | ⚠️ Planned | Security | Token creation |
| **JWS Verify** | ⚠️ Planned | Security | Signature verification |
| **JWKS** | 🔮 Future | Security | Public key retrieval |

**Next Steps:**
1. ✅ Integrate JCS into UTL-X runtime
2. ✅ Integrate JWS basic functions
3. ⚠️ Design security module architecture
4. ⚠️ Implement signature verification
5. ⚠️ Security audit before release

---

## Resources

- [RFC 8785 - JSON Canonicalization Scheme (JCS)](https://www.rfc-editor.org/rfc/rfc8785)
- [RFC 7515 - JSON Web Signature (JWS)](https://www.rfc-editor.org/rfc/rfc7515)
- [RFC 7519 - JSON Web Token (JWT)](https://www.rfc-editor.org/rfc/rfc7519)
- [JWT.io](https://jwt.io) - Token debugger
- [Nimbus JOSE+JWT](https://connect2id.com/products/nimbus-jose-jwt)

---

## Contact

For questions about implementation:
- GitHub Issues: [github.com/grauwen/utl-x/issues](https://github.com/grauwen/utl-x/issues)
- Security concerns: security@utl-x.org
- General questions: docs@utl-x.org