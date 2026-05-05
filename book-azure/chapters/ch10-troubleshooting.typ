= Troubleshooting

_Common problems, their symptoms, and how to fix them._

== Container Starts But ready=false

// Cause: No transformations loaded
// Fix: Upload via Admin API or check volume mount
// Check: GET /health → transformations: 0

== Transformation Upload Fails (400)

// Cause: Syntax error in .utlx source
// Fix: Check error response — includes line number and message
// Test locally: utlx -e 'your expression' before uploading

== Messages Failing (500 on Data Plane)

// Cause: Runtime error in transformation (null reference, type mismatch)
// Diagnose: GET /admin/transformations/{name}/errors
// Fix: Upload corrected .utlx, test with sample input

== High Latency (p99 > 100ms)

// Cause 1: Message too large — parsing dominates
// Check: Prometheus utlxe_transform_duration_seconds
// Fix: Increase maxInputSize or optimize transformation
//
// Cause 2: GC pressure — heap too small
// Check: utlxe_heap_used_bytes near limit
// Fix: Upgrade to Professional (4GB) or optimize message size
//
// Cause 3: Swap enabled
// Fix: Set memory request = memory limit (no swap)

== Out of Memory (OOM Kill)

// Symptom: Container restarts, logs show "Killed"
// Cause: Message larger than available heap
// Fix: Set maxInputSize to prevent oversized messages
// Fix: Upgrade container plan for more memory

== Admin API Returns 403

// Cause 1: UTLXE_ADMIN_KEY not set → all admin endpoints locked
// Fix: Set the environment variable in Container App secrets
//
// Cause 2: Wrong key in X-Admin-Key header
// Fix: Verify the key matches the environment variable

== Transformations Lost After Restart

// Cause: No persistent storage — ephemeral filesystem
// Fix: Enable Azure Files volume mount (createUiDefinition toggle)
// Or: Set up CI/CD re-deploy pipeline

== Dapr Not Delivering Messages

// Cause 1: UTLXe not ready (ready=false) — Dapr waits for readiness
// Fix: Upload transformations first
//
// Cause 2: Binding name doesn't match transformation name
// Fix: Check Dapr component YAML vs transformation name
//
// Cause 3: Transformation is paused
// Fix: POST /admin/transformations/{name}/resume

== Schema Validation Failing Unexpectedly

// Cause: Schema updated but transformation expects old format
// Diagnose: GET /admin/transformations/{name}/validation
// Quick fix: POST /admin/transformations/{name}/validation {"policy":"off"}
// Proper fix: Update the .utlx to match the new schema

== Useful Diagnostic Commands

// # Health and readiness
// curl http://<internal>:8081/health
//
// # List transformations with status
// curl -H "X-Admin-Key: $KEY" http://<internal>:8081/admin/transformations
//
// # Recent errors for a transformation
// curl -H "X-Admin-Key: $KEY" http://<internal>:8081/admin/transformations/{name}/errors
//
// # Engine info (version, uptime, config)
// curl -H "X-Admin-Key: $KEY" http://<internal>:8081/admin/info
//
// # Test a transformation with sample input
// curl -X POST -H "X-Admin-Key: $KEY" -d '{"test":"data"}' http://<internal>:8081/admin/transformations/{name}/test
//
// # Container logs
// az containerapp logs show -n utlxe -g myResourceGroup --follow
