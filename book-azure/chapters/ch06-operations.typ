= Operations

_Day-to-day operational tasks: updating transformations, handling incidents, managing validation._

== Updating a Transformation (Zero Downtime)

// Upload new version → old version drains → new version takes over
// In-flight messages complete on old version
// No mode switch, no restart needed

== Pause and Resume

// POST /admin/transformations/{name}/pause
// Data plane returns 503 for that transformation
// Dapr/Service Bus: messages not acknowledged → retry later
// Transformation stays compiled — resume is instant

== Incident Response Workflow

// 1. Check errors: GET /admin/transformations/{name}/errors
// 2. Pause the transformation
// 3. Fix the .utlx source
// 4. Upload the fix
// 5. Test with sample input
// 6. Resume
// Sequence diagram: the full incident lifecycle

== Validation Override

// Runtime override without touching config files
// POST /admin/transformations/{name}/validation {"policy":"off"}
// Ephemeral — gone on restart
// Precedence: runtime override > config > header > default
// Use during incidents when validation is too strict for a data fix

== Updating Schemas

// POST /admin/schemas/{filename} — replaces the schema
// Takes effect on next message (no recompile)
// Warning if dependent transformations exist

== Bulk Operations

// DELETE /admin/bundle — remove everything, start fresh
// POST /admin/bundle — replace everything atomically
// GET /admin/bundle — export current state as ZIP backup

== Engine Configuration Changes

// GET /admin/config — view current
// POST /admin/config {"maxInputSize":"10MB"} — update at runtime
// Some fields require restart (flagged in response)
