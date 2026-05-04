# F11: Advanced Security Functions

**Status:** Open  
**Priority:** High (XMLDSig needed for Peppol case study)  
**Created:** May 2026

---

## Summary

UTL-X has basic security functions (JWS decode, AES encrypt/decrypt, hashing, masking). This feature covers the missing capabilities needed for production integration scenarios.

## What Already Exists

| Capability | Functions | Status |
|-----------|-----------|--------|
| JWT/JWS decode & inspect | `decodeJWT`, `decodeJWS`, `getJWTClaim`, `getJWSHeader`, `getJWSPayload`, `isJWTExpired` + 12 more | Done |
| AES encryption | `encryptAES`, `decryptAES`, `encryptAES256`, `decryptAES256`, `generateKey`, `generateIV` | Done |
| Hashing | `sha256`, `sha512`, `sha1`, `sha384`, `md5`, `sha3_256`, `sha3_512`, `sha224` | Done |
| HMAC | `hmacSHA256`, `hmacSHA512`, `hmacSHA1`, `hmacMD5`, `hmacBase64`, `hmacSHA384` | Done |
| Masking | `mask` | Done |

## What's Missing

### 1. JWS Signing and Verification (Medium)

Currently UTL-X can decode and inspect JWS/JWT tokens but cannot:
- Create signed JWT tokens (`createJWT(claims, key, algorithm)`)
- Verify JWT signatures (`verifyJWT(token, key)`)
- Sign arbitrary data with JWS (`signJWS(payload, key, algorithm)`)

**Use case:** API gateway integration — validate incoming bearer tokens, create service-to-service tokens.

### 2. XMLDSig / XAdES Verification (High)

XML Digital Signatures are required for:
- Peppol e-invoicing (XAdES-BES signatures on UBL invoices)
- SOAP WS-Security message validation
- HL7 FHIR document verification

Proposed functions:
- `verifyXMLSignature(xml)` → boolean
- `getXMLSignatureInfo(xml)` → object (signer, algorithm, timestamp)
- `validateXAdES(xml, trustStore?)` → validation result

The Apache XML Security library (already a dependency for C14N) provides the foundation.

### 3. RSA Sign/Verify (Medium)

Asymmetric cryptography for:
- Digital signature creation and verification
- Data integrity proofs
- Certificate-based authentication

Proposed functions:
- `rsaSign(data, privateKey, algorithm?)` → signature
- `rsaVerify(data, signature, publicKey, algorithm?)` → boolean
- `rsaEncrypt(data, publicKey)` / `rsaDecrypt(data, privateKey)`

### 4. Key Vault Integration (Medium)

Retrieve secrets and keys from cloud secret managers at runtime:
- Azure Key Vault
- GCP Secret Manager
- AWS Secrets Manager

Proposed functions:
- `getSecret(vaultUrl, secretName)` → string
- `getKey(vaultUrl, keyName)` → key object

**Security note:** These functions access external systems and should be restricted in the CLI (like `env()` — see ch50 security notes). Only available in UTLXe with proper configuration.

## Implementation Priority

1. **XMLDSig/XAdES** — highest business value (Peppol compliance)
2. **JWS signing** — completes the JWT/JWS story
3. **RSA** — foundation for XMLDSig and general crypto
4. **Key Vault** — production deployment concern (EF-level)

## Kotlin Libraries and GraalVM Compatibility

### Libraries

| Feature | Library | Already a dependency? |
|---------|---------|:---:|
| JWS signing | Nimbus JOSE+JWT or `java.security` | No — add |
| XMLDSig/XAdES | Apache XML Security (`xmlsec`) | Yes — already used for C14N |
| RSA sign/verify | `java.security.Signature` (JDK built-in) | Yes — no dependency needed |
| Key Vault | Azure SDK / GCP SDK / AWS SDK | No — add per cloud |

### GraalVM Native Image Risk Assessment

UTL-X ships as a GraalVM native binary for the CLI. B17/B19 showed that GraalVM strips resources and reflection targets that the JVM handles transparently. Each F11 feature has different risk:

| Feature | GraalVM risk | Details |
|---------|:---:|---------|
| JWS signing | Medium | Nimbus JOSE+JWT uses reflection for JSON parsing. Would need `reflect-config.json` entries. Alternative: use `java.security` directly (lower risk). |
| XMLDSig/XAdES | High | Apache XML Security already caused B17 (resource bundles). Signature verification loads additional classes dynamically — key factories, transform algorithms, canonicalization providers. Will need extensive native-image resource and reflection config. Add XMLDSig to the 10-point build validation checklist. |
| RSA sign/verify | Low | `java.security.Signature` is well-supported in GraalVM native image. JDK crypto providers work out of the box. Bouncy Castle (if needed) requires provider registration. |
| Key Vault | **Not viable for native CLI** | Azure/GCP/AWS SDKs are massive (50+ MB), reflection-heavy, and pull in HTTP clients with complex initialization. These should be **JVM/engine-only** — available in UTLXe but NOT in the native CLI binary. |

### Architecture Constraint

Key Vault integration must be implemented in the engine module (`modules/engine`), NOT in core or stdlib. This keeps the native CLI binary lean and avoids pulling cloud SDK dependencies into the GraalVM native image.

For the CLI, secrets should be passed via environment variables (`env("API_KEY")`) or command-line arguments — not fetched from cloud vaults at transformation time.

### Build Validation

When implementing XMLDSig and JWS signing, extend the native binary validation checklist (`.github/workflows/native-build.yml`) with:

```bash
# XMLDSig verification (F11)
echo '<signed-xml/>' | $UTLX --from xml -e 'verifyXMLSignature($input)'

# JWS signing (F11)
echo '{"sub":"user"}' | $UTLX -e 'signJWS($input, "test-key", "HS256")'
```

This prevents a repeat of B17/B19 — native binary issues are caught at build time.

## Effort Estimate

| Task | Effort |
|------|--------|
| XMLDSig verification (Apache XML Security already available) | 2-3 days |
| JWS signing (JSON Web Key support needed) | 2 days |
| RSA sign/verify | 1-2 days |
| Key Vault integration (Azure SDK dependency) | 2-3 days |
| Tests and documentation | 2 days |
| **Total** | **9-12 days** |

---

*Feature F11. May 2026.*
