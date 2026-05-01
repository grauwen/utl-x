= Security Library and Message Security

Security in data transformation is often an afterthought — until an audit finds credit card numbers in log files or patient data crossing a network boundary unencrypted. This chapter covers UTL-X's security functions, the practical decisions around message encryption in integration flows, and what should (and absolutely should NOT) happen with sensitive data during transformation.

== The Security Library (stdlib-security)

UTL-X's security functions live in a separate module (`stdlib-security`) from the main standard library. This separation is deliberate — not every deployment needs cryptographic functions, and keeping them separate reduces the attack surface for environments where crypto is restricted.

=== Available Functions

#table(
  columns: (auto, auto, auto),
  align: (left, left, left),
  [*Category*], [*Functions*], [*Use case*],
  [Hash], [md5, sha1, sha256, sha512], [Data integrity, checksums, fingerprinting],
  [HMAC], [hmacSha256, hmacSha512], [Message authentication, API signing],
  [UUID], [uuid, uuidV4, uuidV5], [Unique identifiers, correlation IDs],
  [Base64], [base64Encode, base64Decode], [Binary-to-text encoding],
  [Hex], [hexEncode, hexDecode], [Byte representation],
  [URL], [urlEncode, urlDecode, urlEncodeComponent], [URL-safe encoding],
)

=== Hashing

Hash functions produce a fixed-size fingerprint from arbitrary input. They're one-way — you can't reverse a hash to get the original data.

```utlx
sha256("sensitive-data")
// "6f2c7b22f1c4e3a8d9f..."  (64 hex characters, always)

md5($input.document)
// "5d41402abc4b2a76..."     (32 hex characters)
```

Common uses in integration:
- *Deduplication:* hash the message content, compare to previously seen hashes
- *Integrity verification:* hash before sending, hash after receiving, compare
- *Anonymization:* hash a customer ID to create a pseudonymous identifier
- *Cache keys:* hash the transformation source + input to cache compiled results

=== HMAC (Hash-based Message Authentication)

HMAC combines a hash with a secret key — proving both integrity AND authenticity:

```utlx
hmacSha256("message-body", "shared-secret-key")
// "a1b2c3d4..."

// Verify: receiver computes the same HMAC with the same key
// If they match, the message hasn't been tampered with
```

Common uses:
- *API authentication:* sign requests for webhook verification (Stripe, GitHub, Shopify)
- *Message integrity:* ensure messages haven't been modified in transit
- *Token generation:* create short-lived authentication tokens

=== UUID Generation

```utlx
uuid()        // "f47ac10b-58cc-4372-a567-0e02b2c3d479" (random v4)
uuidV4()      // same as uuid()
```

Common uses:
- *Correlation IDs:* trace a message through a multi-step pipeline
- *Idempotency keys:* ensure a message is processed exactly once
- *Unique identifiers:* generate IDs for new records created by transformation

== Transport Security: mTLS

Before discussing message-level security, let's be clear about what's already handled:

*Transport Layer Security (TLS)* encrypts the connection between two endpoints. Every message UTL-X receives over HTTPS is encrypted in transit. The message content is decrypted at the TLS termination point (the container's ingress) and is in cleartext inside the container.

*Mutual TLS (mTLS)* goes further — both client AND server present certificates, proving identity in both directions. Azure Container Apps supports mTLS. Dapr sidecars use mTLS automatically between services in Kubernetes.

For most integration scenarios, TLS/mTLS is sufficient:
- Messages are encrypted between endpoints
- Identity is verified (who sent this message?)
- No additional encryption needed inside the trusted network

== Message-Level Encryption

=== When It's Required

Some regulations require encryption of the message _payload_ itself — not just the connection:

- *PCI DSS:* cardholder data must be encrypted at rest and in transit
- *HIPAA:* protected health information (PHI) must be encrypted
- *NEN 7510:* Dutch healthcare information security standard
- *Government classified data:* often requires end-to-end encryption

=== The Encrypt-Transform-Re-encrypt Pattern

When message-level encryption is required, the transformation flow becomes:

```
Encrypted message arrives
    ↓ decrypt (UTLXe needs the decryption key)
    ↓ transform (cleartext in memory, briefly)
    ↓ encrypt (UTLXe needs the encryption key for the target)
Encrypted message sent
```

This works but adds complexity:
- UTLXe needs access to decryption AND encryption keys
- Key management: rotation, storage, access control
- Performance: decrypt + encrypt adds 1-5ms per message
- Debugging: encrypted messages cannot be inspected in logs or queues
- The message IS in cleartext in UTLXe's memory during transformation — this is unavoidable

=== Why This Is a Maintenance Burden

```
Keys to manage:           per source × per target = N × M keys
Certificate lifecycle:    each expires, must be renewed
Key rotation:             planned rotations, emergency rotations
Access control:           who can access which keys?
Audit:                    prove keys are handled correctly
Testing:                  tests need test keys (not production keys)
```

For 5 source systems and 3 target systems: 15 key pairs to manage. Each with its own rotation schedule. This is why most integration platforms rely on transport encryption (TLS) and access controls — message-level encryption is reserved for regulated data that truly requires it.

=== Recommendation

Use *transport encryption (TLS/mTLS)* for all communication. Add *message-level encryption* only when regulation specifically requires it (PCI DSS, HIPAA, classified data). Don't encrypt "just in case" — the operational cost is significant and usually unnecessary.

== Digital Signatures and Integrity

=== When You Need Signatures

Digital signatures prove two things: *integrity* (the message hasn't been modified) and *authenticity* (the message came from a known sender). They don't encrypt — they verify.

Common in:
- *Peppol e-invoicing:* UBL invoices are digitally signed with XAdES
- *Banking (ISO 20022):* payment messages signed for non-repudiation
- *API webhooks:* HMAC signature in HTTP header (simpler than PKI)
- *JWT tokens:* signed claims for API authentication

=== Impact on Transformation

*Critical rule:* if you transform a digitally signed message, the signature becomes invalid. The signature covers the exact bytes of the original — any change (even whitespace) breaks it.

The correct flow:
+ Verify the signature (before transformation)
+ Transform the content (signature is now invalid)
+ Re-sign the output (with the sender's key for the target)

This means UTLXe must:
- Verify signatures on input (pre-validation step)
- Sign output (post-transformation step)
- Have access to the appropriate keys

=== What UTL-X Can Do Today

```utlx
// HMAC verification (simple API webhooks)
let expectedSig = hmacSha256($input.body, "webhook-secret")
if (expectedSig != $input.headers.signature)
  error("Invalid webhook signature")

// Hash for integrity check
let contentHash = sha256(renderJson($input))
{...$input, integrityHash: contentHash}
```

=== What UTL-X Cannot Do Yet (Future)

- JWS (JSON Web Signature): sign and verify JWT tokens
- XMLDSig: verify XML digital signatures (XAdES for Peppol)
- AES encryption/decryption: symmetric encryption
- RSA sign/verify: asymmetric cryptography
- Key Vault integration: retrieve keys from Azure Key Vault, GCP Secret Manager

These are documented as future additions to the security library.

== Large Files and Sensitive Data

=== The Claim Check Pattern

Sensitive large files (contracts, medical images, financial documents) should NOT travel through the message body. Use the *claim check pattern*:

+ Store the file in secure object storage (Azure Blob, S3, GCS) with encryption at rest
+ Put a *reference* in the message (URL, blob name, CMIS object ID)
+ UTL-X transforms the message metadata — not the file content
+ The consuming application retrieves the file from storage using the reference

```utlx
// Transform the reference, not the file:
{
  invoiceId: $input.invoiceId,
  document: {
    storageType: "azure-blob",
    container: "invoices",
    blobName: concat($input.invoiceId, ".pdf"),
    url: concat("https://storage.blob.core.windows.net/invoices/", $input.invoiceId, ".pdf")
  }
}
```

=== Avoid Base64 Embedding

Embedding binary content as Base64 in messages is an anti-pattern for sensitive data:

- 33% size increase (Base64 overhead)
- The entire file is in UTL-X memory during transformation
- The file content may appear in logs, error messages, queue storage
- No access control — anyone with the message has the file

For files under 50 KB (small images, digital signatures), Base64 is acceptable. For anything larger or sensitive, use the claim check pattern.

== What NOT to Log

This topic is covered in depth in Chapter 38 (Logging and Compliance). The short version:

*Never log:* credit card numbers (PCI DSS), patient data (HIPAA/GDPR), passwords, API keys, session tokens, private keys.

*Always log:* transformation ID, timestamp, duration, success/failure, error field names (not values).

UTL-X's default logging (INFO level) logs no message content — only operational metadata. DEBUG and TRACE levels can expose sensitive data and must NEVER be enabled in production with sensitive workloads.
