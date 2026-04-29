= Security Library and Message Security

== The UTL-X Security Library (stdlib-security)
// - Separate module from core stdlib (stdlib-security/)
// - Cryptographic functions for integration security
// - Available functions:
//   - Hash: md5, sha1, sha256, sha512
//   - HMAC: hmacSha256, hmacSha512
//   - UUID: uuid, uuidV4, uuidV5
//   - Encoding: base64Encode, base64Decode, hexEncode, hexDecode
//   - URL-safe: urlEncode, urlDecode
// - Future additions: AES encrypt/decrypt, RSA sign/verify, JWS, JWE

== Should Messages Be Encrypted in Transit?

=== Transport-Level Encryption (mTLS)
// - HTTPS/TLS: encrypts the connection, not the message
// - mTLS (mutual TLS): both client AND server present certificates
// - This is the STANDARD for service-to-service communication
// - Azure Container Apps: HTTPS by default (TLS termination at ingress)
// - Service Bus: TLS 1.2+ enforced
// - Dapr: mTLS between sidecars (automatic in Kubernetes)
//
// UTL-X receives messages AFTER TLS termination.
// The message content is in cleartext within the container.
// This is correct — the transformation NEEDS to read the content.

=== Message-Level Encryption (End-to-End)
// - Encrypt the message PAYLOAD, not just the connection
// - The message remains encrypted even in queues, logs, storage
// - Only the intended recipient can decrypt
// - Standards: JWE (JSON Web Encryption), XML Encryption, S/MIME
//
// The problem for transformation:
//   Encrypted message → UTLXe → ???
//   UTLXe cannot transform what it cannot read.
//
// Options:
// 1. Decrypt → Transform → Re-encrypt (common pattern)
//    - UTLXe needs access to decryption keys
//    - Adds complexity (key management, rotation)
//    - The message is briefly in cleartext in UTLXe's memory
//
// 2. Don't encrypt at message level — rely on transport encryption (mTLS)
//    - Simpler, standard practice
//    - Message is cleartext within the trusted network
//    - Risk: message visible in queue/storage (mitigated by access controls)
//
// Recommendation: mTLS for transport + access controls for queues/storage.
// Message-level encryption only when regulations REQUIRE it (e.g., PHI across organizations).

=== Why Encrypt-Transform-Re-encrypt Is a Maintenance Burden
// - Key management: UTLXe needs keys for EVERY encrypted channel
// - Key rotation: when keys change, UTLXe must be updated
// - Certificate lifecycle: certificates expire, must be renewed
// - Performance: decrypt + encrypt adds ~1-5ms per message
// - Debugging: encrypted messages cannot be inspected in logs/queues
// - Error handling: decryption failure vs transformation failure — different error paths
//
// In practice, most integration platforms (MuleSoft, Tibco, SAP CPI)
// do NOT encrypt messages within the integration flow.
// They rely on transport security (TLS) and access controls.

== Digital Signatures and Integrity

=== JCS (JSON Canonicalization Scheme)
// - RFC 8785: deterministic JSON serialization
// - Needed for: signing JSON documents (the signature must cover a stable byte sequence)
// - Problem: JSON allows arbitrary key ordering — same object, different bytes
// - JCS defines: key sorting, number formatting, string escaping rules
// - Use case: sign a JSON invoice, verify signature after transformation
// - UTL-X relevance: when generating signed JSON output, canonical form is needed

=== XML Digital Signatures (XMLDSig)
// - W3C standard for signing XML documents
// - Used in: SOAP WS-Security, UBL invoices (Peppol), SAML assertions
// - Canonicalization (C14N): normalize XML before signing (whitespace, namespace ordering)
// - UTL-X relevance: transforming signed XML may BREAK the signature
//   (any change to the signed portion invalidates the signature)
// - Rule: verify signature BEFORE transformation, re-sign AFTER transformation

=== JWS (JSON Web Signature)
// - IETF standard for signing JSON payloads (JWT tokens, API security)
// - Compact serialization: header.payload.signature
// - UTL-X can: parse JWT payloads (base64Decode + parseJson)
// - UTL-X cannot yet: verify JWS signatures (needs crypto library)
// - Future: jwtVerify(), jwtSign() functions in security library

== Future Security Functions (Vision)

=== Encryption
// - aesEncrypt(data, key, mode) → encrypted bytes
// - aesDecrypt(data, key, mode) → decrypted data
// - rsaEncrypt(data, publicKey) → encrypted bytes
// - rsaDecrypt(data, privateKey) → decrypted data
// - Support for: AES-256-GCM, RSA-OAEP (standard cloud-compatible algorithms)

=== Signing
// - jwtSign(payload, key, algorithm) → JWT string
// - jwtVerify(token, key) → {valid: true, payload: {...}}
// - xmlSign(document, privateKey, canonicalization) → signed XML
// - xmlVerifySignature(signedXml, publicKey) → boolean

=== Key Management Integration
// - Azure Key Vault: retrieve keys at runtime
// - GCP Secret Manager: retrieve keys at runtime
// - AWS KMS: retrieve keys at runtime
// - Environment variables: for development/testing
// - NEVER hardcode keys in .utlx files

== Large Files: CMIS and Object Storage References

=== The Problem with Large Payloads
// - Integration messages should be SMALL (< 1 MB, ideally < 100 KB)
// - Large files (PDFs, images, ZIPs) should NOT be in the message body
// - Reasons:
//   1. Memory: 10 MB XML = 300 MB in UDM (see Message Parsing chapter)
//   2. Queue limits: Service Bus message max = 256 KB (standard), 100 MB (premium)
//   3. Performance: serializing/deserializing large payloads is slow
//   4. Cost: message broker pricing often based on message size

=== The Claim Check Pattern
// - Store the large file in object storage (S3, Blob Storage, GCS)
// - Put a REFERENCE in the message (URL, object key, CMIS ID)
// - The consumer retrieves the file from storage when needed
//
// Message (small):
// {
//   "invoiceId": "INV-2026-001",
//   "pdfDocument": {
//     "storageType": "azure-blob",
//     "container": "invoices",
//     "blobName": "INV-2026-001.pdf",
//     "url": "https://storage.blob.core.windows.net/invoices/INV-2026-001.pdf",
//     "sizeBytes": 245000,
//     "contentType": "application/pdf"
//   }
// }
//
// UTL-X transforms the MESSAGE METADATA — not the file itself.
// The PDF stays in blob storage. UTL-X maps the reference fields.

=== CMIS (Content Management Interoperability Services)
// - OASIS standard for document management (Alfresco, SharePoint, OpenText)
// - Documents referenced by CMIS object ID
// - UTL-X can: transform CMIS metadata (properties, relationships)
// - UTL-X cannot: retrieve CMIS content (that's the application's job)

=== Avoid Base64 Embedding
// - Anti-pattern: embed binary content as Base64 in XML/JSON messages
//   <Document encoding="base64">JVBERi0xLjQKJeLjz9MK...</Document>
// - Problems:
//   1. 33% size increase (Base64 encoding overhead)
//   2. UDM stores the entire Base64 string in memory (can be huge)
//   3. Parsing is slow (long string allocation)
//   4. Not searchable, not indexable
// - When acceptable: small files (< 50 KB), inline images in HTML, digital signatures
// - When NOT acceptable: documents > 100 KB, batch processing, high-throughput pipelines
// - Rule of thumb: if the Base64 string would be > 100 KB, use a storage reference instead
