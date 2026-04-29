= Logging, Observability, and Compliance

== Logging in UTL-X

=== What UTL-X Logs
// - Transformation start/end: timestamp, transformation ID, duration
// - Errors: parse errors, validation failures, runtime exceptions
// - Health: startup, shutdown, worker pool status
// - Metrics: request count, latency percentiles, error rate (Prometheus)
//
// What UTL-X does NOT log (by default):
// - Input message content
// - Output message content
// - Intermediate UDM state
// - Field values during transformation

=== Log Levels
// - ERROR: transformation failures, parse errors, unrecoverable issues
// - WARN: validation warnings (WARN policy), deprecated function usage
// - INFO: startup, shutdown, transformation load/unload, health status
// - DEBUG: parser trace, interpreter step-by-step, UDM tree dump
// - TRACE: every function call, every property access (extremely verbose)
//
// Production: INFO (default)
// Development: DEBUG or TRACE (via --debug, --debug-parser, --debug-interpreter)
// NEVER in production: DEBUG or TRACE (performance impact + data exposure risk)

=== Configuring Logging
// - UTLXe: logback.xml (SLF4J + Logback)
// - utlx CLI: console output, --verbose flag
// - Azure Container Apps: stdout → Log Analytics (automatic)
// - GCP Cloud Run: stdout → Cloud Logging (automatic)
// - Prometheus metrics: /metrics endpoint on port 8081

== When NOT to Log: Compliance and Data Protection

=== PCI DSS (Payment Card Industry Data Security Standard)
// PCI DSS Requirement 3: Protect stored cardholder data
// PCI DSS Requirement 4: Encrypt transmission of cardholder data
//
// What you MUST NOT log:
// - Primary Account Number (PAN): the 16-digit card number
// - CVV/CVC: the 3-4 digit security code (NEVER stored, period)
// - Full magnetic stripe data
// - PIN or PIN block
//
// What you MAY log (with care):
// - Truncated PAN: first 6 and last 4 digits (BIN + last four)
// - Transaction ID (reference number, not card data)
// - Timestamp, amount, currency (metadata, not cardholder data)
//
// UTL-X implications:
// - DEBUG logging would expose card data in the UDM tree → NEVER use in payment flows
// - Transformation errors should NOT include the full input message
// - Error messages should reference field NAMES, not VALUES
//   Wrong: "Error: invalid card number 4111-1111-1111-1111"
//   Right: "Error: field 'cardNumber' failed validation"

=== GDPR (General Data Protection Regulation)
// - Personal data: name, email, address, phone, IP, BSN, SSN, health data
// - Right to erasure: if you log personal data, you must be able to delete it
// - Data minimization: log only what's necessary
//
// UTL-X implications:
// - Don't enable DEBUG in production with personal data
// - If logging transformations for auditing, mask PII fields
// - UTLXe's default (INFO) logs no message content — GDPR-safe
// - Azure Log Analytics retention: configure to match your GDPR policy

=== HIPAA (Health Insurance Portability and Accountability Act)
// - Protected Health Information (PHI): patient name, DOB, SSN, diagnoses, medications
// - Minimum necessary rule: access only what's needed for the task
// - Audit trail: WHO accessed WHAT, WHEN
//
// UTL-X implications:
// - Healthcare transformations (HL7, FHIR) process PHI
// - NEVER log input/output messages containing PHI
// - LOG: transformation ID, timestamp, success/failure (audit trail)
// - DON'T LOG: patient data, diagnosis codes, medication lists
// - Azure: use Customer-Managed Keys (CMK) for Log Analytics encryption

=== NEN 7510 (Dutch Healthcare Information Security)
// - Dutch implementation of ISO 27001 for healthcare
// - Strict access control, audit logging, data classification
// - UTL-X in hospital environments: data stays in tenant (no cloud logging to external services)
// - All processing in the hospital's Azure tenant → NEN 7510 by architecture

=== SOX (Sarbanes-Oxley Act — Financial)
// - Financial reporting integrity
// - Audit trail for data transformations that affect financial reports
// - LOG: who transformed what, when, which transformation version
// - DON'T LOG: actual financial data (amounts, account numbers)

== Designing for Compliance

=== The Principle: Log Metadata, Not Data
//
// | Log this | Don't log this |
// |----------|---------------|
// | Transformation ID | Input message content |
// | Timestamp | Output message content |
// | Duration (ms) | Field values |
// | Success / failure | PII (name, email, BSN) |
// | Error message (field name) | Error data (field value) |
// | Worker ID | Session tokens |
// | Source system ID | Credentials |
// | Schema validation result | The data that failed validation |

=== Audit Logging Pattern
// For regulated industries, add an audit step to the pipeline:
//
// Input → Transform → Validate → Audit Log → Output
//
// The audit log step records:
//   - Transaction ID (correlation)
//   - Transformation name and version
//   - Timestamp
//   - Input format and size (not content)
//   - Output format and size (not content)
//   - Validation result (pass/fail, rule names)
//   - Error details (field names, not values)
//
// This provides a full audit trail WITHOUT exposing sensitive data.

=== Masking in Error Messages
// UTL-X error messages should follow this pattern:
//
// Good: "Validation failed: field 'customerBSN' does not match pattern NL[0-9]{9}"
// Bad:  "Validation failed: value '123456789' does not match pattern NL[0-9]{9}"
//
// Good: "Type error at $.payment.cardNumber: expected string, got number"
// Bad:  "Type error: 4111111111111111 is not a string"
//
// The transformation developer controls error messages in try/catch blocks.
// Best practice: catch errors, log field paths, never log field values.

== Prometheus Metrics (Safe by Design)
// UTLXe exposes metrics on port 8081 — these are SAFE for compliance:
// - utlx_requests_total: count (no data content)
// - utlx_request_duration_seconds: latency histogram
// - utlx_errors_total: error count by type
// - utlx_workers_active: worker utilization
// - utlx_transformations_loaded: count of loaded transforms
//
// Prometheus metrics contain NO message data — only operational counters.
// Safe for: PCI DSS, GDPR, HIPAA, NEN 7510, SOX environments.
