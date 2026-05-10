= Operations

This chapter covers day-to-day operational tasks: updating transformations without downtime, handling incidents, and managing validation at runtime.

== Updating a Transformation

Upload a new version of the same transformation name. The update is atomic and zero-downtime:

```bash
curl -X POST \
  -H "X-Admin-Key: $KEY" \
  -F "source=@invoice-to-ubl-v2.utlx" \
  https://<your-fqdn>/admin/transformations/invoice-to-ubl
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
  https://<your-fqdn>/admin/transformations/invoice-to-ubl/pause
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
  https://<your-fqdn>/admin/transformations/invoice-to-ubl/resume
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
  https://<your-fqdn>/admin/transformations/invoice-to-ubl/validation
```

Check the effective state:

```bash
curl -H "X-Admin-Key: $KEY" \
  https://<your-fqdn>/admin/transformations/invoice-to-ubl/validation
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
  https://<your-fqdn>/admin/transformations/invoice-to-ubl/validation
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
  https://<your-fqdn>/admin/schemas/order.xsd
```

The new schema takes effect on the next message. Transformations that reference `order.xsd` do not need to be re-uploaded --- schema resolution happens at validation time, not compile time.

== Bulk Operations

Replace everything at once (atomic):

```bash
curl -X POST -H "X-Admin-Key: $KEY" \
  -F "file=@new-bundle.zip" \
  https://<your-fqdn>/admin/bundle
```

Remove everything and start fresh:

```bash
curl -X DELETE -H "X-Admin-Key: $KEY" \
  https://<your-fqdn>/admin/bundle
```

Export the current state as a backup:

```bash
curl -H "X-Admin-Key: $KEY" \
  https://<your-fqdn>/admin/bundle -o backup.zip
```

== Engine Configuration

View the current engine configuration:

```bash
curl -H "X-Admin-Key: $KEY" \
  https://<your-fqdn>/admin/config
```

Update a runtime-safe field:

```bash
curl -X POST \
  -H "X-Admin-Key: $KEY" \
  -H "Content-Type: application/json" \
  -d '{"maxInputSize": "10MB"}' \
  https://<your-fqdn>/admin/config
```

Fields that require a restart (like port numbers) are accepted but flagged in the response:

```json
{"updated": ["maxInputSize"], "restart_required": []}
```

== Production Locked Mode

When any `.utlar` file is present on the data volume, UTLXe enters *locked mode*. Name it after the business flow: `sales.utlar`, `orders.utlar`, `website.utlar`. The Admin API becomes read-only for all mutating endpoints --- transformations, schemas, messaging config, and bundle uploads all return 403 with `BUNDLE_LOCKED`.

Operational endpoints continue to work: pause/resume, validation override, test, log management, and all GET endpoints.

This matches enterprise expectations: production deployments are immutable. Changes go through CI/CD, not the Admin API.

Detection is automatic --- if any `.utlar` file exists in `/utlxe/data/`, UTLXe is locked. No CLI flag needed.

=== Deploying a .utlar to Production

To deploy or update a `.utlar` bundle, upload it to the Azure Files share and restart the container. Run these commands from Azure Cloud Shell or any terminal with `az` installed:

```bash
# Step 1: Find your storage account name
az containerapp show --name utlxe --resource-group <your-rg> \
  --query "properties.template.volumes[0].storageName" -o tsv

# Step 2: Upload the .utlar to Azure Files
az storage file upload \
  --account-name <your-storage-account> \
  --share-name utlxe-data \
  --source orders.utlar \
  --path orders.utlar

# Step 3: Restart the container to pick up the new bundle
az containerapp revision restart \
  --name utlxe \
  --resource-group <your-rg>
```

After the restart (~30 seconds):
- UTLXe detects `orders.utlar` on disk → enters locked mode
- All transformations, schemas, and messaging config from the bundle are loaded
- The Admin API becomes read-only
- The Web UI shows the mode as "locked" with the bundle version

To verify:

```bash
curl -H "X-Admin-Key: <your-key>" \
  https://<your-fqdn>/admin/info
```

```json
{
  "mode": "locked",
  "bundle_version": "v3.2.1",
  "transformations": 4,
  "ready": true
}
```

=== Updating a Production Bundle

Same procedure --- upload the new `.utlar` with the same filename and restart:

```bash
az storage file upload \
  --account-name <your-storage-account> \
  --share-name utlxe-data \
  --source orders-v2.utlar \
  --path orders.utlar \
  --overwrite

az containerapp revision restart \
  --name utlxe --resource-group <your-rg>
```

Note: `--path orders.utlar` keeps the same filename on Azure Files even if the local file is named differently. This ensures only one `.utlar` file is on disk.

=== Reverting to a Previous Version

Upload the previous `.utlar` from your git repository or artifact store and restart:

```bash
az storage file upload \
  --account-name <your-storage-account> \
  --share-name utlxe-data \
  --source orders-v1.utlar \
  --path orders.utlar \
  --overwrite

az containerapp revision restart \
  --name utlxe --resource-group <your-rg>
```

=== Switching Back to Open Mode

To switch from locked back to open mode (e.g., for debugging), delete the `.utlar` file and restart:

```bash
az storage file delete \
  --account-name <your-storage-account> \
  --share-name utlxe-data \
  --path orders.utlar

az containerapp revision restart \
  --name utlxe --resource-group <your-rg>
```

After restart, UTLXe finds no `.utlar` file → open mode. The Admin API is fully accessible again.

```bash
# Check the mode
curl -H "X-Admin-Key: $KEY" https://<your-fqdn>/admin/info
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
  https://<your-fqdn>/admin/log/level
```

View recent log entries directly via the Admin API --- no need to open the Azure portal:

```bash
# Last 50 entries
curl -H "X-Admin-Key: $KEY" "https://<your-fqdn>/admin/logs?limit=50"

# Only errors
curl -H "X-Admin-Key: $KEY" "https://<your-fqdn>/admin/logs?level=ERROR"

# Search for a specific MessageId
curl -H "X-Admin-Key: $KEY" "https://<your-fqdn>/admin/logs?contains=msg-abc-123"
```

The log buffer holds the last 5000 entries in memory. Zero disk I/O. The auto-revert feature ensures DEBUG does not stay on accidentally --- the level reverts to the previous value after the specified time.

== Messaging Configuration

Configure Dapr queues, topics, and Event Hubs per transformation via the Admin API. Changes are staged as drafts and pushed to Dapr via explicit sync:

```bash
# 1. Set messaging (staged as draft --- Dapr not touched)
curl -X POST -H "X-Admin-Key: $KEY" \
  -H "Content-Type: application/json" \
  -d '{"input": {"queue": "orders-in"}, "output": {"topic": "processed"}}' \
  https://<your-fqdn>/admin/transformations/orders-in/messaging

# 2. Test via HTTP (no Dapr needed --- the transformation works via HTTP)
curl -X POST -H "X-Admin-Key: $KEY" \
  -d '{"orderId": "123"}' \
  https://<your-fqdn>/admin/transformations/orders-in/test

# 3. Sync to Dapr (creates binding YAML, messages start flowing)
curl -X POST -H "X-Admin-Key: $KEY" \
  https://<your-fqdn>/admin/transformations/orders-in/sync
```

The stage-then-sync model lets you test transformations via HTTP before connecting them to real queues. Sync is the "go live" switch.

== Heap Backpressure

UTLXe monitors heap usage and automatically rejects incoming Dapr messages when heap usage exceeds the backpressure threshold (default: 85%). Messages stay in Service Bus and are retried when pressure drops. This prevents OOM crashes without operator intervention.

View current status:

```bash
curl -H "X-Admin-Key: $KEY" https://<your-fqdn>/admin/backpressure
```

```json
{
  "threshold": "85%",
  "heap_used_mb": 1247,
  "heap_max_mb": 3072,
  "heap_usage": "40%",
  "pressure": false
}
```

Adjust the threshold at runtime (no restart needed):

```bash
curl -X POST -H "X-Admin-Key: $KEY" \
  -d '{"threshold": 80}' \
  https://<your-fqdn>/admin/backpressure
```

The Web UI shows the heap status visually on the *Config* tab --- a color-coded bar (green/yellow/red) with the threshold dropdown.

What happens during pressure:
- Dapr binding and pub/sub messages → 503 (retried by Dapr, messages stay in Service Bus)
- Direct HTTP calls → still accepted (client controls retry)
- Admin API → always available (operators can always manage the engine)
- After GC reduces heap below threshold → messages accepted again automatically

The backpressure check adds zero overhead to message processing --- the heap percentage is cached by a background thread (updated every 100ms) and the hot path reads a single cached number.

== Per-Transformation Message Size Limits

Each transformation can set a maximum input size. Messages exceeding the limit are rejected before processing:

```bash
curl -X POST -H "X-Admin-Key: $KEY" \
  -d '{"maxInputSize": "100KB"}' \
  https://<your-fqdn>/admin/transformations/sensor-events/config
```

Available sizes: 10KB, 100KB, 500KB, 1MB, 5MB (default), 10MB, 25MB, 50MB. A transformation handling small sensor events can set 100KB --- large EDI batches can set 25MB. This catches bad data early and protects heap for other transformations.

The limit can also be set in `transform.yaml` inside the `.utlar` bundle:

```yaml
strategy: COMPILED
maxInputSize: 100KB
input:
  queue: sensor-events
```

== Resetting the Admin Key

The admin key (`UTLXE_ADMIN_KEY`) protects the Admin API and the Web UI. It is set during deployment and stored as a Container App secret. It *cannot be retrieved* from Azure after deployment --- only reset.

=== If You Lost the Key

Reset it with the Azure CLI:

```bash
# Set a new admin key
az containerapp secret set \
  --name <your-app-name> \
  --resource-group <your-rg> \
  --secrets admin-key=<your-new-key>

# Update the environment variable to reference the new secret
az containerapp update \
  --name <your-app-name> \
  --resource-group <your-rg> \
  --set-env-vars UTLXE_ADMIN_KEY=secretref:admin-key

# Restart to pick up the new key
az containerapp revision restart \
  --name <your-app-name> \
  --resource-group <your-rg>
```

After the restart (~30 seconds), the new key is active. Log into the Web UI with the new key.

=== If You Want to Rotate the Key

The same procedure as above. Rotation is recommended:
- After a team member with access leaves the organization
- After the key was shared over an insecure channel
- Periodically as part of security hygiene (e.g., every 90 days)

=== What Happens During a Key Reset

- The container restarts (~30 seconds of downtime for the Admin API)
- The data plane (port 8085) continues processing messages during the restart --- Dapr queues messages that arrive during the restart window
- Transformations, schemas, and bundles on Azure Files are not affected
- Active Web UI sessions are invalidated --- users must re-enter the new key

=== Preventing Key Loss

Store the admin key in:
- A password manager (1Password, Bitwarden, Azure Key Vault)
- Your CI/CD secrets (GitHub Secrets, Azure DevOps variable groups)
- *Not* in email, chat, or source code
