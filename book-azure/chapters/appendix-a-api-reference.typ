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

=== Messaging Endpoints (EF10)

Configure Dapr messaging (queues, topics, Event Hubs) per transformation. Changes are staged as drafts and pushed to Dapr via sync.

#table(
  columns: (auto, auto, 1fr),
  [*Method*], [*Path*], [*Description*],
  [`GET`], [`/admin/dapr`], [Dapr sidecar status, integration mode (http-only/static/dynamic), loaded components.],
  [`GET`], [`/admin/transformations/{name}/messaging`], [Messaging config with sync status and Dapr binding state.],
  [`POST`], [`/admin/transformations/{name}/messaging`], [Set input/output messaging (queue/topic/eventhub). Saved as draft.],
  [`DELETE`], [`/admin/transformations/{name}/messaging`], [Remove messaging config. Saved as draft.],
  [`POST`], [`/admin/transformations/{name}/sync`], [Push this transformation's messaging to Dapr.],
  [`POST`], [`/admin/sync`], [Push all draft transformations to Dapr.],
  [`GET`], [`/admin/sync`], [Sync status overview for all transformations.],
)

Messaging body uses the field name as discriminator --- `queue`, `topic`, or `eventhub`:

```json
{"input": {"queue": "orders-in"}, "output": {"topic": "processed-orders"}}
{"input": {"topic": "raw-invoices", "subscription": "utlxe"}, "output": {"queue": "alerts"}}
{"input": {"eventhub": "telemetry", "consumerGroup": "utlxe"}, "output": {"queue": "alerts"}}
```

=== Log Management Endpoints (EF12)

Runtime log level changes and in-memory log access. Allowed in locked mode.

#table(
  columns: (auto, auto, 1fr),
  [*Method*], [*Path*], [*Description*],
  [`GET`], [`/admin/log/level`], [Current root log level.],
  [`POST`], [`/admin/log/level`], [Change log level. Body: `{"level":"DEBUG","revert_after_minutes":30}`.],
  [`GET`], [`/admin/logs`], [Recent log entries from memory buffer. Params: `?limit=N&level=ERROR&contains=text&since=ISO8601`.],
  [`DELETE`], [`/admin/logs`], [Clear the log buffer.],
)

=== Health Endpoints (no authentication)

#table(
  columns: (auto, auto, 1fr),
  [*Method*], [*Path*], [*Description*],
  [`GET`], [`/health`], [Returns status, transformation count, and ready flag.],
  [`GET`], [`/health/ready`], [Readiness probe. Returns 200 when RUNNING with at least one transformation.],
  [`GET`], [`/health/live`], [Liveness probe. Always returns 200.],
  [`GET`], [`/metrics`], [Prometheus metrics in text exposition format.],
)

== Data Plane Port (8085)

#table(
  columns: (auto, auto, 1fr),
  [*Method*], [*Path*], [*Description*],
  [`GET`], [`/api/transformations`], [List available transformations (discovery). No authentication.],
  [`POST`], [`/api/execute/{id}`], [Execute a transformation. Body: `{"payload":"...","contentType":"application/json"}`.],
  [`OPTIONS`], [`/{bindingName}`], [Dapr binding probe. Always returns 200.],
  [`POST`], [`/{bindingName}`], [Dapr input binding delivery. Routes to transformation by binding name.],
  [`GET`], [`/dapr/subscribe`], [Dapr pub/sub subscription list. Derived from loaded transformations with topic config.],
  [`POST`], [`/pubsub/{name}`], [Dapr pub/sub delivery. Accepts CloudEvents (structured or binary mode).],
)

== Production Locked Mode (EF09)

When `bundle.utlar` is found on the data volume, UTLXe enters locked mode. Mutating endpoints return 403:

#table(
  columns: (auto, auto, auto),
  [*Endpoint category*], [*Open mode*], [*Locked mode*],
  [Upload/delete transformations], [Allowed], [*403 BUNDLE_LOCKED*],
  [Upload/delete bundle], [Allowed], [*403 BUNDLE_LOCKED*],
  [Upload/delete schemas], [Allowed], [*403 BUNDLE_LOCKED*],
  [Set/delete messaging], [Allowed], [*403 BUNDLE_LOCKED*],
  [Pause/resume], [Allowed], [Allowed --- operational],
  [Validation override], [Allowed], [Allowed --- operational],
  [Test transformation], [Allowed], [Allowed --- read-only],
  [All GET endpoints], [Allowed], [Allowed --- read-only],
  [Log level / logs], [Allowed], [Allowed --- diagnostic],
)

== Response Status Codes

#table(
  columns: (auto, 1fr),
  [*Code*], [*Meaning*],
  [200], [Success.],
  [400], [Bad request --- compilation error, invalid input, malformed ZIP.],
  [403], [Forbidden --- missing or wrong `X-Admin-Key`, key not configured, or locked mode.],
  [404], [Transformation not found.],
  [413], [Message too large --- exceeds `maxInputSize`.],
  [429], [Transformation paused --- back off (pub/sub input). Includes `Retry-After` header.],
  [500], [Transformation runtime error (null reference, type mismatch, etc.).],
  [503], [Transformation not loaded or paused (binding input).],
)
