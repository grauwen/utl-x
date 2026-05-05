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

- The data plane returns 503 for that transformation. Other transformations continue normally.
- Dapr receives the 503 and does not acknowledge the message. Service Bus retries it later (or moves it to the dead-letter queue after max retries).
- The transformation stays compiled in memory and on disk --- resume is instant.
- The listing shows `"status": "paused"`.

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
