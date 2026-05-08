= Operations

This chapter covers day-to-day operational tasks: updating transformations without downtime, handling incidents, and managing validation at runtime.

== Updating a Transformation

Upload a new version of the same transformation name. The update is atomic and zero-downtime:

```bash
curl -X POST \
  -H "X-Admin-Key: $KEY" \
  -F "source=@invoice-to-ubl-v2.utlx" \
  http://<admin>:8081/admin/transformations/invoice-to-ubl
```

What happens internally:

+ UTLXe compiles the new source. If compilation fails, the upload is rejected and the running version is untouched.
+ The new compiled version atomically replaces the old one in the registry.
+ Messages that are currently being processed on the old version complete normally --- they hold a reference to the old compiled code.
+ New messages arriving after the swap use the new version.

There is no restart, no mode switch, and no window where messages are dropped.

== Pause and Resume

During an incident, you may need to stop processing for a specific transformation without removing it. Pausing preserves the compiled transformation and its on-disk state while stopping traffic.

Pause:

```bash
curl -X POST \
  -H "X-Admin-Key: $KEY" \
  http://<admin>:8081/admin/transformations/invoice-to-ubl/pause
```

While paused:

- Binding input: the data plane returns 503 for that transformation. Other transformations continue normally.
- Pub/sub input: the data plane returns 429 with a `Retry-After` header. Combined with a Dapr Resiliency circuit breaker, this prevents dead-lettering during maintenance windows.
- Messages stay in Service Bus until the transformation is resumed --- they are not consumed or dead-lettered.
- The transformation stays compiled in memory and on disk --- resume is instant.
- The listing shows `"status": "paused"`.

For global maintenance (pause everything), UTLXe can return 503 from `/healthz`, which causes Dapr to stop all bindings and subscriptions. See the Dapr Resiliency section in the architecture documentation.

Resume:

```bash
curl -X POST \
  -H "X-Admin-Key: $KEY" \
  http://<admin>:8081/admin/transformations/invoice-to-ubl/resume
```

Processing resumes immediately. Queued messages on Service Bus are delivered again.

== Incident Response Workflow

A complete incident lifecycle:

+ *Detect* --- Grafana alert fires on elevated error rate.
+ *Diagnose* --- Check the error ring buffer:
  ```bash
  curl -H "X-Admin-Key: $KEY" \
    .../admin/transformations/invoice-to-ubl/errors
  ```
+ *Contain* --- Pause the transformation:
  ```bash
  curl -X POST -H "X-Admin-Key: $KEY" \
    .../admin/transformations/invoice-to-ubl/pause
  ```
+ *Fix* --- Edit the `.utlx` source to handle the failing case.
+ *Deploy* --- Upload the fix:
  ```bash
  curl -X POST -H "X-Admin-Key: $KEY" \
    -F "source=@invoice-to-ubl-fixed.utlx" \
    .../admin/transformations/invoice-to-ubl
  ```
+ *Verify* --- Test with the input that caused the failure:
  ```bash
  curl -X POST -H "X-Admin-Key: $KEY" \
    -d '{"orderId":"12345","customer":null}' \
    .../admin/transformations/invoice-to-ubl/test
  ```
+ *Resume* --- Bring the transformation back online:
  ```bash
  curl -X POST -H "X-Admin-Key: $KEY" \
    .../admin/transformations/invoice-to-ubl/resume
  ```

The entire cycle --- from detection to resume --- can happen in minutes without a container restart.

== Validation Override

Schema validation catches malformed messages before they reach the transformation logic. But during an incident, validation might be too strict --- blocking messages that are technically valid but don't match the expected schema exactly.

Set a runtime override:

```bash
curl -X POST \
  -H "X-Admin-Key: $KEY" \
  -H "Content-Type: application/json" \
  -d '{"policy": "off"}' \
  http://<admin>:8081/admin/transformations/invoice-to-ubl/validation
```

Check the effective state:

```bash
curl -H "X-Admin-Key: $KEY" \
  http://<admin>:8081/admin/transformations/invoice-to-ubl/validation
```

```json
{
  "effective_policy": "off",
  "source": "runtime-override",
  "config_policy": "strict",
  "header_policy": "strict"
}
```

Remove the override to revert to the configured policy:

```bash
curl -X DELETE \
  -H "X-Admin-Key: $KEY" \
  http://<admin>:8081/admin/transformations/invoice-to-ubl/validation
```

The override is ephemeral --- it is not written to disk and disappears on container restart. The precedence chain is:

#table(
  columns: (auto, auto, auto),
  [*Level*], [*Set by*], [*Persists*],
  [Runtime override], [Ops via Admin API], [No --- ephemeral],
  [`transform.yaml` config], [Ops at deploy time], [Yes --- on disk],
  [`.utlx` header], [Developer at dev time], [Yes --- in source],
  [Default], [---], [`strict`],
)

The highest-priority level that is set wins.

== Updating Schemas

Replace a schema without recompiling transformations:

```bash
curl -X POST \
  -H "X-Admin-Key: $KEY" \
  -F "file=@order-v2.xsd" \
  http://<admin>:8081/admin/schemas/order.xsd
```

The new schema takes effect on the next message. Transformations that reference `order.xsd` do not need to be re-uploaded --- schema resolution happens at validation time, not compile time.

== Bulk Operations

Replace everything at once (atomic):

```bash
curl -X POST -H "X-Admin-Key: $KEY" \
  -F "file=@new-bundle.zip" \
  http://<admin>:8081/admin/bundle
```

Remove everything and start fresh:

```bash
curl -X DELETE -H "X-Admin-Key: $KEY" \
  http://<admin>:8081/admin/bundle
```

Export the current state as a backup:

```bash
curl -H "X-Admin-Key: $KEY" \
  http://<admin>:8081/admin/bundle -o backup.zip
```

== Engine Configuration

View the current engine configuration:

```bash
curl -H "X-Admin-Key: $KEY" \
  http://<admin>:8081/admin/config
```

Update a runtime-safe field:

```bash
curl -X POST \
  -H "X-Admin-Key: $KEY" \
  -H "Content-Type: application/json" \
  -d '{"maxInputSize": "10MB"}' \
  http://<admin>:8081/admin/config
```

Fields that require a restart (like port numbers) are accepted but flagged in the response:

```json
{"updated": ["maxInputSize"], "restart_required": []}
```

== Production Locked Mode

When CI/CD deploys a `bundle.utlar` file to the data volume, UTLXe enters *locked mode*. The Admin API becomes read-only for all mutating endpoints --- transformations, schemas, messaging config, and bundle uploads all return 403 with `BUNDLE_LOCKED`.

Operational endpoints continue to work: pause/resume, validation override, test, log management, and all GET endpoints.

This matches enterprise expectations: production deployments are immutable. Changes go through CI/CD, not the Admin API.

Detection is automatic --- if `/utlxe/data/bundle.utlar` exists, UTLXe is locked. No CLI flag needed.

```bash
# Check the mode
curl -H "X-Admin-Key: $KEY" http://<admin>:8081/admin/info
```

```json
{
  "mode": "locked",
  "bundle_version": "v3.2.1",
  "bundle_checksum": "sha256:a1b2c3..."
}
```

== Runtime Log Management

During an incident, switch to DEBUG logging without restarting the container:

```bash
# Switch to DEBUG with auto-revert after 30 minutes
curl -X POST -H "X-Admin-Key: $KEY" \
  -H "Content-Type: application/json" \
  -d '{"level": "DEBUG", "revert_after_minutes": 30}' \
  http://<admin>:8081/admin/log/level
```

View recent log entries directly via the Admin API --- no need to open the Azure portal:

```bash
# Last 50 entries
curl -H "X-Admin-Key: $KEY" "http://<admin>:8081/admin/logs?limit=50"

# Only errors
curl -H "X-Admin-Key: $KEY" "http://<admin>:8081/admin/logs?level=ERROR"

# Search for a specific MessageId
curl -H "X-Admin-Key: $KEY" "http://<admin>:8081/admin/logs?contains=msg-abc-123"
```

The log buffer holds the last 5000 entries in memory. Zero disk I/O. The auto-revert feature ensures DEBUG does not stay on accidentally --- the level reverts to the previous value after the specified time.

== Messaging Configuration

Configure Dapr queues, topics, and Event Hubs per transformation via the Admin API. Changes are staged as drafts and pushed to Dapr via explicit sync:

```bash
# 1. Set messaging (staged as draft --- Dapr not touched)
curl -X POST -H "X-Admin-Key: $KEY" \
  -H "Content-Type: application/json" \
  -d '{"input": {"queue": "orders-in"}, "output": {"topic": "processed"}}' \
  http://<admin>:8081/admin/transformations/orders-in/messaging

# 2. Test via HTTP (no Dapr needed --- the transformation works via HTTP)
curl -X POST -H "X-Admin-Key: $KEY" \
  -d '{"orderId": "123"}' \
  http://<admin>:8081/admin/transformations/orders-in/test

# 3. Sync to Dapr (creates binding YAML, messages start flowing)
curl -X POST -H "X-Admin-Key: $KEY" \
  http://<admin>:8081/admin/transformations/orders-in/sync
```

The stage-then-sync model lets you test transformations via HTTP before connecting them to real queues. Sync is the "go live" switch.
