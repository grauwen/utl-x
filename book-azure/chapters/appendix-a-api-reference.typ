= Appendix A: API Reference

This appendix lists every endpoint on both ports with request format, response format, and status codes.

== Authentication

All `/admin/*` endpoints require the header `X-Admin-Key: {value}`. The value must match the `UTLXE_ADMIN_KEY` environment variable. If the variable is not set, all admin endpoints return 403.

The `/health`, `/metrics`, and `/api/*` endpoints do not require authentication.

== Admin Port (8081)

=== Bundle Endpoints

#table(
  columns: (auto, auto, 1fr),
  [*Method*], [*Path*], [*Description*],
  [`POST`], [`/admin/bundle`], [Upload a `.zip` bundle. Replaces all transformations and schemas atomically.],
  [`GET`], [`/admin/bundle`], [Export the current state as a downloadable `.zip`.],
  [`DELETE`], [`/admin/bundle`], [Remove all transformations and schemas. Returns to empty state.],
  [`POST`], [`/admin/bundle/validate`], [Upload and validate without deploying (dry run).],
)

=== Transformation Endpoints

#table(
  columns: (auto, auto, 1fr),
  [*Method*], [*Path*], [*Description*],
  [`GET`], [`/admin/transformations`], [List all deployed transformations with status and metrics.],
  [`GET`], [`/admin/transformations/{name}`], [Get details: config, compile status, metrics, source.],
  [`POST`], [`/admin/transformations/{name}`], [Deploy or update. Accepts `multipart/form-data` with `source` (required) and `config` (optional).],
  [`POST`], [`/admin/transformations/{name}/config`], [Update config only. No recompile.],
  [`DELETE`], [`/admin/transformations/{name}`], [Remove a single transformation.],
)

=== Testing Endpoint

#table(
  columns: (auto, auto, 1fr),
  [*Method*], [*Path*], [*Description*],
  [`POST`], [`/admin/transformations/{name}/test`], [Execute with sample input. Returns output or error. Not counted in metrics.],
)

=== Operational Endpoints

#table(
  columns: (auto, auto, 1fr),
  [*Method*], [*Path*], [*Description*],
  [`GET`], [`/admin/info`], [Engine version, uptime, config, persistence mode, transformation count.],
  [`POST`], [`/admin/transformations/{name}/pause`], [Stop processing. Data plane returns 503. Transformation stays compiled.],
  [`POST`], [`/admin/transformations/{name}/resume`], [Resume processing.],
  [`GET`], [`/admin/transformations/{name}/errors`], [Recent errors (ring buffer, last 100). Accepts `?limit=N`.],
  [`GET`], [`/admin/config`], [View current engine configuration.],
  [`POST`], [`/admin/config`], [Update runtime-safe configuration fields.],
)

=== Validation Override Endpoints

#table(
  columns: (auto, auto, 1fr),
  [*Method*], [*Path*], [*Description*],
  [`GET`], [`/admin/transformations/{name}/validation`], [Get effective validation state (policy, source, config default).],
  [`POST`], [`/admin/transformations/{name}/validation`], [Set a runtime override. Body: `{"policy":"off"}`. Ephemeral --- not persisted.],
  [`DELETE`], [`/admin/transformations/{name}/validation`], [Remove override. Revert to config or header default.],
)

=== Schema Endpoints

#table(
  columns: (auto, auto, 1fr),
  [*Method*], [*Path*], [*Description*],
  [`GET`], [`/admin/schemas`], [List all uploaded schemas with filename and size.],
  [`GET`], [`/admin/schemas/{filename}`], [Download a schema file.],
  [`POST`], [`/admin/schemas/{filename}`], [Upload or replace a schema. Accepts `multipart/form-data` with `file`.],
  [`DELETE`], [`/admin/schemas/{filename}`], [Remove a schema.],
)

=== Health Endpoints (no authentication)

#table(
  columns: (auto, auto, 1fr),
  [*Method*], [*Path*], [*Description*],
  [`GET`], [`/health`], [Returns status, transformation count, and ready flag.],
  [`GET`], [`/metrics`], [Prometheus metrics in text exposition format.],
)

== Data Plane Port (8085)

#table(
  columns: (auto, auto, 1fr),
  [*Method*], [*Path*], [*Description*],
  [`GET`], [`/api/transformations`], [List available transformations (discovery). No authentication.],
  [`POST`], [`/api/transform/{name}`], [Execute a transformation. Body is the input message. Content-Type header determines format.],
)

== Response Status Codes

#table(
  columns: (auto, 1fr),
  [*Code*], [*Meaning*],
  [200], [Success.],
  [400], [Bad request --- compilation error, invalid input, malformed ZIP.],
  [403], [Forbidden --- missing or wrong `X-Admin-Key`, or key not configured.],
  [404], [Transformation not found.],
  [413], [Message too large --- exceeds `maxInputSize`.],
  [500], [Transformation runtime error (null reference, type mismatch, etc.).],
  [503], [Transformation is paused.],
)
