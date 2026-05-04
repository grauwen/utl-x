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
