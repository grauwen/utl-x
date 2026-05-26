# F13: Post-Quantum Cryptography (PQC)

**Status:** Future Enhancement
**Priority:** Low (no standards mandate yet)
**Depends on:** F11 (Advanced Security Functions)
**Created:** May 2026

---

## Summary

Add post-quantum cryptographic primitives to `stdlib/crypto`: key encapsulation (PQC-KEM) and digital signatures (PQC-Signature). These protect data transformations and integration messages against future quantum computing attacks.

## Background

NIST finalized three post-quantum standards in 2024:

| Standard | Algorithm | Purpose |
|----------|-----------|---------|
| FIPS 203 | **ML-KEM** (formerly Kyber) | Key Encapsulation Mechanism |
| FIPS 204 | **ML-DSA** (formerly Dilithium) | Digital Signatures |
| FIPS 205 | **SLH-DSA** (formerly SPHINCS+) | Stateless Hash-Based Signatures |

These replace or augment RSA and ECDSA in scenarios where quantum resistance is required.

## Relevance to UTL-X

UTL-X operates in enterprise integration scenarios where cryptographic operations are part of the data pipeline:

- **Peppol e-invoicing** will eventually require PQC migration (ETSI roadmap)
- **SOAP WS-Security** and **XMLDSig** signatures will need PQC algorithm support
- **JWS/JWT** tokens — IETF drafts exist for PQC-based JOSE algorithms
- **HL7 FHIR** document signing in healthcare
- **Financial messaging** (ISO 20022, SWIFT) — regulatory pressure for quantum readiness

UTL-X already handles classical crypto (F11). PQC is the natural next step in the same trust chain.

## Proposed Functions

### PQC-Signature (`pqc-signature-action`) — Dilithium / ML-DSA

Digital signatures using **ML-DSA (Dilithium)**:

```
pqcSign(data, privateKey, algorithm?)        -> signature (base64)
pqcVerify(data, signature, publicKey, algorithm?) -> boolean
pqcGenerateKeyPair(algorithm?)               -> { publicKey, privateKey }
```

**Supported algorithms:**
- `ML-DSA-44` (Dilithium2) — NIST security level 2 (default)
- `ML-DSA-65` (Dilithium3) — NIST security level 3
- `ML-DSA-87` (Dilithium5) — NIST security level 5
- `SLH-DSA-SHA2-128s` (SPHINCS+-SHA2-128s) — stateless hash-based (conservative)

**Use cases:**
- Sign transformation outputs before sending to downstream systems
- Verify signatures on incoming integration messages
- Non-repudiation in regulated data pipelines

### PQC-KEM (`pqc-kem-action`) — Kyber / ML-KEM

Key encapsulation for quantum-resistant key exchange using **ML-KEM (Kyber)**:

```
pqcEncapsulate(publicKey, algorithm?)        -> { ciphertext, sharedSecret }
pqcDecapsulate(ciphertext, privateKey, algorithm?) -> sharedSecret
pqcKEMGenerateKeyPair(algorithm?)            -> { publicKey, privateKey }
```

**Supported algorithms:**
- `ML-KEM-512` (Kyber-512) — NIST security level 1
- `ML-KEM-768` (Kyber-768) — NIST security level 3 (default)
- `ML-KEM-1024` (Kyber-1024) — NIST security level 5

**Use cases:**
- Establish shared secrets for encrypted message exchange between integration endpoints
- Key agreement in multi-party data transformation pipelines
- Wrap/unwrap symmetric keys (AES) for encrypted payloads

### Hybrid Mode

Real-world PQC migration uses hybrid schemes (classical + PQC) for defense-in-depth:

```
hybridSign(data, classicalKey, pqcKey, algorithms?)     -> { classicalSig, pqcSig }
hybridVerify(data, signatures, classicalPub, pqcPub)    -> boolean
hybridEncapsulate(classicalPub, pqcPub)                 -> { ciphertext, sharedSecret }
```

Hybrid mode ensures backward compatibility during the transition period where not all parties support PQC.

## Implementation Location

**Module:** `stdlib` (package `org.apache.utlx.stdlib.crypto`)

This follows the existing architecture:
- Core stdlib has read-only/decode functions (JWT decode, hashing)
- `stdlib/crypto/` has signing, verification, and key operations (merged from stdlib-security, F11)
- PQC extends the same pattern

## Dependencies and Platform Constraints

### JVM Libraries

| Option | Pros | Cons |
|--------|------|------|
| Bouncy Castle (`bcpqc-jdk18on`) | Mature, all NIST algorithms, available now | Reflection-heavy, GraalVM native image issues |
| JDK built-in (Java 24+) | No external dependency, well-integrated | UTL-X targets JDK 17 — not available yet |
| liboqs (via JNI) | Reference implementation, very fast | Native library, complex distribution |

**Recommended:** Bouncy Castle initially, migrate to JDK built-in when UTL-X moves to Java 24+.

### GraalVM Native Image

| Risk | Detail |
|------|--------|
| High | Bouncy Castle PQC uses reflection and service providers extensively |
| Mitigation | Register all PQC provider classes in `reflect-config.json` and `resource-config.json` |
| Alternative | Make PQC functions JVM/engine-only (like Key Vault in F11), exclude from native CLI binary |

**Recommendation:** Initially implement as JVM-only (available in UTLXe engine). Add native CLI support once JDK built-in PQC is available and GraalVM compatibility is confirmed.

## Prerequisites

F13 depends on F11 completion:

```
F11: RSA sign/verify              <- foundation for java.security.Signature pattern
F11: XMLDSig/XAdES                <- same infrastructure, classical algorithms
F11: JWS signing                  <- JOSE framework for token-based crypto
  |
  v
F13: PQC-Signature (Dilithium/ML-DSA)  <- extends java.security.Signature with PQC providers
F13: PQC-KEM (Kyber/ML-KEM)           <- new capability, uses same key management patterns
F13: Hybrid mode                  <- combines F11 classical + F13 PQC
```

## Trigger Criteria

Prioritize F13 when any of these occur:

1. **Standards mandate** — Peppol, ETSI, or ISO 20022 publish PQC migration timelines
2. **JDK support** — Java LTS includes native PQC providers (expected Java 25+)
3. **Customer request** — enterprise users require quantum readiness for compliance
4. **Industry adoption** — JOSE PQC algorithms (ML-DSA in JWS) reach RFC status

## Effort Estimate

| Task | Effort |
|------|--------|
| PQC-Signature — Dilithium/ML-DSA (via Bouncy Castle) | 2-3 days |
| PQC-KEM — Kyber/ML-KEM (via Bouncy Castle) | 2-3 days |
| Hybrid mode | 2 days |
| GraalVM compatibility / native image config | 2-3 days |
| Tests and documentation | 2 days |
| **Total** | **10-13 days** |

## See Also

- [F11: Advanced Security Functions](F11-advanced-security-functions.md) — prerequisite
- [JWS Analysis](../jws/jws_analysis.md) — JWS architecture decisions
- [stdlib/crypto package](/stdlib/src/main/kotlin/org/apache/utlx/stdlib/crypto/) — implementation target
- NIST PQC standards: [FIPS 203](https://csrc.nist.gov/pubs/fips/203/final), [FIPS 204](https://csrc.nist.gov/pubs/fips/204/final), [FIPS 205](https://csrc.nist.gov/pubs/fips/205/final)

---

*Feature F13. May 2026.*
