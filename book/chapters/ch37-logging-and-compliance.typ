= Logging, Observability, and Compliance

Data transformations sit at the most sensitive point in an integration pipeline — they see every field of every message. A payment transformation sees card numbers. A healthcare transformation sees patient records. A financial transformation sees account balances. This makes logging decisions critical: log too much and you violate PCI DSS, GDPR, or HIPAA. Log too little and you can't debug production issues.

This chapter covers UTL-X's approach: log metadata (what happened), never data (what was in the message).

== What UTL-X Logs

=== Default Behavior (INFO Level)

UTLXe logs operational events, never message content:

- *Startup:* engine version, loaded transformations, worker count, transport mode
- *Transformation lifecycle:* load, unload, reload events with transformation ID
- *Request processing:* transformation ID, duration, success/failure — but NOT the message
- *Errors:* parse errors, validation failures, runtime exceptions — with field paths, not field values
- *Health:* worker pool status, memory usage, queue depth

What UTL-X does NOT log by default:
- Input message content
- Output message content
- Intermediate UDM state
- Field values during transformation

This is safe for PCI DSS, GDPR, HIPAA, and SOX environments without any configuration.

=== Log Levels

#table(
  columns: (auto, auto, auto),
  align: (left, left, left),
  [*Level*], [*What it logs*], [*When to use*],
  [ERROR], [Transformation failures, parse errors, unrecoverable issues], [Always on],
  [WARN], [Validation warnings (WARN policy), deprecated functions], [Always on],
  [INFO], [Startup, shutdown, load/unload, health, request metadata], [Production (default)],
  [DEBUG], [Parser trace, interpreter steps, UDM tree structure], [Development only],
  [TRACE], [Every function call, every property access], [Debugging only],
)

#block(
  fill: rgb("#FFEBEE"),
  inset: 12pt,
  radius: 4pt,
  width: 100%,
)[
  *Never use DEBUG or TRACE in production.* These levels dump message content — UDM trees, field values, intermediate results — into logs. In a payment or healthcare pipeline, this exposes sensitive data. The performance impact is also severe: TRACE can reduce throughput by 100x.
]

=== Configuring Logging

*UTLXe:* uses SLF4J + Logback. Configure via `logback.xml` in the container, or override with environment variables:

```bash
# Set log level via environment
UTLXE_LOG_LEVEL=INFO    # default
UTLXE_LOG_LEVEL=DEBUG   # development only
```

*CLI:* console output with `--verbose` flag for additional detail.

*Cloud platforms:* UTLXe logs to stdout. Container platforms capture stdout automatically:
- Azure Container Apps → Log Analytics
- GCP Cloud Run → Cloud Logging
- AWS ECS → CloudWatch Logs
- Kubernetes → node-level log collection (Fluentd, Fluent Bit, Vector)

== When NOT to Log: Compliance Requirements

=== PCI DSS (Payment Card Industry)

PCI DSS protects cardholder data. If your transformation processes payments:

*Never log:*
- Primary Account Number (PAN) — the 16-digit card number
- CVV/CVC — the 3-4 digit security code (must never be stored, period)
- Full magnetic stripe data
- PIN or PIN block

*May log (with care):*
- Truncated PAN: first 6 + last 4 digits (`411111****1111`)
- Transaction reference ID
- Timestamp, amount, currency (metadata)

*UTL-X implication:* DEBUG logging exposes card data in the UDM tree. Never enable DEBUG in payment flows. Error messages must reference field names, not values:

```
Good: "Validation failed: field 'cardNumber' does not match pattern"
Bad:  "Validation failed: value '4111-1111-1111-1111' does not match pattern"
```

=== GDPR (General Data Protection Regulation)

GDPR protects personal data of EU residents. If your transformation processes customer data:

*Personal data includes:* name, email, address, phone, IP address, BSN (Dutch), SSN (US), health data, financial data, location data, biometric data.

*Key obligations:*
- *Data minimization:* log only what's necessary for the purpose
- *Right to erasure:* if you log personal data, you must be able to delete it on request
- *Purpose limitation:* logs collected for debugging can't be used for marketing

*UTL-X implication:* INFO level logs no message content — GDPR-safe by default. If you enable DEBUG for troubleshooting, disable it immediately after and purge the debug logs. Configure log retention to match your GDPR data retention policy.

=== HIPAA (Health Insurance Portability and Accountability Act)

HIPAA protects health information. If your transformation processes HL7, FHIR, or clinical data:

*Protected Health Information (PHI):* patient name, date of birth, SSN, diagnosis codes (ICD-10), medication lists, lab results, insurance IDs.

*Key obligations:*
- *Minimum necessary:* access only what's needed for the transformation
- *Audit trail:* log WHO accessed WHAT, WHEN — but not the PHI itself
- *Encryption:* PHI must be encrypted at rest and in transit

*UTL-X implication:* healthcare transformations process PHI — the transformation itself is fine (it needs to see the data to map it). But logs must never contain PHI. Log the transformation ID, timestamp, and success/failure. Don't log patient names, diagnosis codes, or lab values.

=== NEN 7510 (Dutch Healthcare Security)

Dutch implementation of ISO 27001 for healthcare, required for all Dutch healthcare organizations:

- Strict access control and data classification
- Audit logging requirements
- Data must stay within the organization's tenant

UTL-X in hospital environments: deploy UTLXe in the hospital's own Azure tenant. All processing, logging, and data stay within the tenant boundary. No external cloud logging services.

=== SOX (Sarbanes-Oxley — Financial Reporting)

SOX requires audit trails for data that affects financial reports:

- Log: WHO transformed WHAT, WHEN, WHICH transformation version
- Don't log: actual financial amounts, account numbers, balances
- Transformation version must be traceable — `.utlx` files in Git with commit hashes

== Designing for Compliance

=== Principle: Log Metadata, Not Data

#table(
  columns: (auto, auto),
  align: (left, left),
  [*Log this (metadata)*], [*Don't log this (data)*],
  [Transformation ID], [Input message content],
  [Timestamp], [Output message content],
  [Duration (milliseconds)], [Field values],
  [Success / failure], [PII (name, email, BSN, SSN)],
  [Error message (field path)], [Error data (field value)],
  [Worker ID], [Session tokens or API keys],
  [Source system identifier], [Credentials or passwords],
  [Schema validation result (pass/fail)], [The data that failed validation],
  [Message size (bytes)], [Message content],
  [Input/output format], [Actual payload],
)

=== Audit Logging Pattern

For regulated industries, add an audit step to the pipeline:

```
Input → Transform → Validate → Audit Log → Output
```

The audit log records:
- Transaction ID (for correlation across systems)
- Transformation name and version (Git commit hash)
- Timestamp (ISO 8601, UTC)
- Input format and size (not content)
- Output format and size (not content)
- Validation result (pass/fail, which rules)
- Error details (field paths, not values)

This provides a complete audit trail — who, what, when, which version — without exposing a single sensitive value.

=== Masking in Error Messages

Error messages are the most common source of accidental data exposure. UTL-X error messages reference field paths, not values:

```
Good: "Type error at $.payment.cardNumber: expected string, got number"
Bad:  "Type error: 4111111111111111 is not a string"

Good: "Validation failed: field 'patient.bsn' does not match pattern"
Bad:  "Validation failed: '123456789' does not match pattern NL[0-9]{9}"
```

When writing custom error handling with `try/catch`, follow the same principle:

```utlx
try {
  parseDate($input.birthDate, "yyyy-MM-dd")
} catch {
  // Good: log what failed, not the value
  null    // or: error("Invalid date format in field 'birthDate'")
}
```

== Prometheus Metrics: Safe by Design

UTLXe's Prometheus metrics (port 8081) contain no message data — only operational counters and histograms:

```
utlxe_messages_processed_total{transformation="order-to-invoice"}  42531
utlxe_messages_failed_total{transformation="order-to-invoice"}  12
utlxe_transformation_duration_seconds_bucket{le="0.01"}  38000
utlxe_active_workers  6
utlxe_transformations_loaded  5
```

These metrics are safe for every compliance regime — PCI DSS, GDPR, HIPAA, NEN 7510, SOX. They tell you how the system is performing without revealing what data it's processing.

== The Security Library Connection

UTL-X's security library (Chapter 16) provides functions for masking, hashing, and encrypting sensitive data *within* transformations. If you need to produce an audit-safe version of the output:

```utlx
{
  orderId: $input.orderId,
  customerName: mask($input.customerName, 3),     // "Ali***"
  email: hashSHA256($input.email),                // irreversible hash
  cardNumber: maskPAN($input.cardNumber),          // "411111******1111"
  total: $input.total                              // non-sensitive, keep as-is
}
```

This produces a record that's safe to log, store in a data warehouse, or send to analytics — the sensitive fields are masked or hashed, the business metadata is preserved. See Chapter 16 for the full security function reference.
