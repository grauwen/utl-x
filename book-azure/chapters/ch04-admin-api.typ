= The Admin API

The Admin API is the primary interface for deploying and operating UTLXe on Azure. It runs on port 8081 alongside the health and metrics endpoints. This chapter covers every operation you need for day-to-day management.

== Architecture: Two Ports, Two Purposes

UTLXe exposes two HTTP servers on separate ports:

#table(
  columns: (auto, auto, 1fr),
  [*Port*], [*Purpose*], [*Access*],
  [8081], [Admin API + health + metrics], [Internal to VNet --- not exposed via ingress],
  [8085], [Data plane (message processing)], [Exposed via Container App ingress],
)

This separation allows network isolation. The data plane is accessible to client applications and Dapr sidecars. The admin port is restricted to operators and CI/CD pipelines within the VNet.

Both ports are always active. There is no mode switch --- you can upload transformations while the data plane processes messages.

== The Admin Web UI (Optional)

UTLXe ships with an optional web UI --- a separate lightweight container (nginx, ~5MB) in the same pod. It provides a browser-based interface for all Admin API operations.

#table(
  columns: (auto, auto, 1fr),
  [*Container*], [*Port*], [*Purpose*],
  [utlxe], [8081 + 8085], [Engine --- admin API + data plane],
  [dapr], [3500], [Sidecar --- localhost only],
  [utlxe-ui], [8088 (default)], [Web UI --- browser access, proxies `/admin/*` to UTLXe],
)

Open the UI in your browser at the Container App's external URL.

=== Dashboard

The dashboard shows all loaded transformations with status, message count, error rate, and messaging configuration at a glance.

#figure(
  image("../pictures/webadmin/webadmin-1.png", width: 100%),
  caption: [Dashboard --- transformation overview with status, messaging, and sync state],
)

=== Upload

Paste `.utlx` source directly, upload a `.zip` / `.utlar` bundle, validate before deploying, or export the current state as an archive.

#figure(
  image("../pictures/webadmin/webadmin-2.png", width: 100%),
  caption: [Upload page --- paste source, upload bundle, or export],
)

=== Schemas

Upload, list, and manage shared validation schemas (XSD, JSON Schema, Avro, etc.) used by transformations.

#figure(
  image("../pictures/webadmin/webadmin-3.png", width: 100%),
  caption: [Schemas page --- upload and manage validation schemas],
)

=== Sync

View Dapr integration status: sidecar version, loaded components, and per-transformation sync state. Sync individual transformations or all drafts at once.

#figure(
  image("../pictures/webadmin/webadmin-4.png", width: 100%),
  caption: [Sync page --- Dapr sidecar status and per-transformation sync state],
)

=== Transformation Detail

Click a transformation name to see its details: status, strategy, message count, errors. Pause, resume, or delete from here. The messaging button opens the queue/topic configuration.

#figure(
  image("../pictures/webadmin/webadmin-6.png", width: 100%),
  caption: [Transformation detail --- status, configuration with strategy and schema dropdowns],
)

Scroll down for the validation override, test panel (send sample input and see the output), recent errors, and the transformation source code.

#figure(
  image("../pictures/webadmin/webadmin-7.png", width: 100%),
  caption: [Transformation detail (continued) --- validation override, test panel, errors, and source],
)

=== Logs

View recent log entries, filter by level or text, and change the log level at runtime with auto-revert. Essential for production debugging without opening the Azure portal.

#figure(
  image("../pictures/webadmin/webadmin-5.png", width: 100%),
  caption: [Logs page --- runtime log access with level change and filtering],
)

=== Config

View the engine configuration and runtime info: version, mode (open/locked), bundle version, uptime, Dapr status, and all configuration values.

#figure(
  image("../pictures/webadmin/webadmin-8.png", width: 100%),
  caption: [Config page --- engine configuration and runtime info],
)

In locked mode (production), the UI shows a read-only view. Upload and delete buttons are disabled. Operational actions (pause, resume, test, log level, validation override) remain available.

The UI is *optional*. Customers who manage UTLXe via CI/CD and scripts can omit the `utlxe-ui` container entirely. All functionality is available via the REST API --- the UI calls the same endpoints.

All ports are configurable via environment variables (`UI_PORT`, `ADMIN_PORT`). The UI makes zero changes to UTLXe --- it is a pure API consumer.

=== Reaching the Web UI After Deployment

When you deploy from the Marketplace, Azure Container Apps gives you a public HTTPS URL automatically. No VPN, no port-forwarding, no jumpbox needed.

To find the URL:

```bash
az containerapp show \
  --name <your-app-name> \
  --resource-group <your-rg> \
  --query "properties.configuration.ingress.fqdn" -o tsv
```

Open `https://<fqdn>` in your browser, enter the admin key you set during deployment, and the dashboard loads.

If the URL is not reachable:

#table(
  columns: (auto, 1fr),
  [*Symptom*], [*Fix*],
  [Browser times out], [Ingress may be set to internal. Run: `az containerapp ingress update --name <app> -g <rg> --type external`],
  [Shows nginx 404], [`targetPort` points at the wrong container. Should be `8088` (UI), not `8085` (data plane).],
  [Login rejects the key], [Check that `UTLXE_ADMIN_KEY` is set: `az containerapp show --name <app> -g <rg> --query "properties.template.containers[0].env"`],
  [Dashboard loads but empty], [No transformations uploaded yet. Upload one via the Upload tab.],
)

=== Switching Between Public and Private Access

You can flip the Web UI between public and private access at any time --- no redeployment needed:

```bash
# Make public (dev/test — reachable from any browser)
az containerapp ingress update \
  --name <app> --resource-group <rg> \
  --type external

# Make private (production — reachable only inside the VNet)
az containerapp ingress update \
  --name <app> --resource-group <rg> \
  --type internal
```

The change takes effect within 30--120 seconds. The admin key remains the authentication boundary in both modes.

For production environments with private access, operators reach the Web UI via:
- An existing jumpbox VM in the same VNet (most common --- enterprises already have one)
- Azure Bastion
- VPN or ExpressRoute connecting the corporate network to the Azure VNet
- `az containerapp exec` for terminal-only access (zero infrastructure)

*Note:* VNet integration must be chosen at deployment time --- it cannot be added to an existing Container Apps environment. If you anticipate needing private network access in the future, select VNet integration during deployment.

== Authentication

Every request to `/admin/*` endpoints must include the `X-Admin-Key` header:

```bash
curl -H "X-Admin-Key: my-secret-key-here" \
  https://<your-fqdn>/admin/transformations
```

The key is set via the `UTLXE_ADMIN_KEY` environment variable. If this variable is not set, all admin endpoints return 403 --- the API is locked by default.

The health endpoints (`/health`, `/metrics`) do not require authentication. They are read-only and needed by Kubernetes probes and Prometheus.

== Uploading Transformations

=== Single .utlx File

The simplest deployment: upload just the `.utlx` source file. UTLXe applies sensible defaults (COMPILED strategy, auto-detect format).

```bash
curl -X POST \
  -H "X-Admin-Key: $KEY" \
  -F "source=@invoice-to-ubl.utlx" \
  https://<your-fqdn>/admin/transformations/invoice-to-ubl
```

Response:

```json
{
  "status": "deployed",
  "name": "invoice-to-ubl",
  "strategy": "COMPILED",
  "config": "defaults",
  "compiled_in_ms": 48
}
```

=== With Configuration

For explicit control over strategy and validation, include a `transform.yaml`:

```bash
curl -X POST \
  -H "X-Admin-Key: $KEY" \
  -F "source=@invoice-to-ubl.utlx" \
  -F "config=@transform.yaml" \
  https://<your-fqdn>/admin/transformations/invoice-to-ubl
```

A typical `transform.yaml`:

```yaml
strategy: COMPILED
validationPolicy: strict
inputs:
  - name: input
    schema: order.json
maxConcurrent: 4
```

=== ZIP Bundle (or .utlar)

For batch deployment, upload a ZIP (or `.utlar`) containing all transformations and schemas at once. The file name does not matter --- the Admin API reads the ZIP contents regardless of the filename.

```bash
curl -X POST \
  -H "X-Admin-Key: $KEY" \
  -F "file=@mybundle.zip" \
  https://<your-fqdn>/admin/bundle
```

The ZIP follows this structure:

```
mybundle.zip (or .utlar)
  schemas/                          (optional)
    order.xsd
    invoice.json
  transformations/
    invoice-to-ubl/
      invoice-to-ubl.utlx          (required)
      transform.yaml                (optional — strategy, validation, messaging)
    order-enrichment/
      order-enrichment.utlx
```

*What happens on upload:*

+ UTLXe unpacks the ZIP, compiles all `.utlx` files, loads schemas into the schema store.
+ The contents are saved as a *directory tree* on disk (not as a ZIP file). Each transformation gets its own directory under `/utlxe/data/transformations/`.
+ On restart, UTLXe scans the directory tree → *open mode*. The Admin API remains fully accessible.

*This is different from locked mode.* Locked mode is triggered when CI/CD places any `.utlar` file on the Azure Files volume (e.g., `sales.utlar`, `website.utlar`, `orders.utlar`). The filename can be anything --- name it after the business flow it serves. Uploading via the Admin API never creates a `.utlar` file --- it always unpacks to a directory tree.

#table(
  columns: (auto, 1fr, auto),
  [*How deployed*], [*What's on disk*], [*Mode on restart*],
  [Admin API upload (`POST /admin/bundle`)], [Directory tree: `transformations/{name}/{name}.utlx`], [Open],
  [CI/CD places a `.utlar` file on Azure Files], [Single file: e.g., `sales.utlar`], [Locked],
)

== Managing Schemas

Schemas are shared resources, stored separately from transformations. A single schema like `order.xsd` can be referenced by multiple transformations.

Upload a schema:

```bash
curl -X POST \
  -H "X-Admin-Key: $KEY" \
  -F "file=@order.xsd" \
  https://<your-fqdn>/admin/schemas/order.xsd
```

List all schemas:

```bash
curl -H "X-Admin-Key: $KEY" \
  https://<your-fqdn>/admin/schemas
```

```json
{
  "schemas": [
    {"filename": "order.xsd", "size_bytes": 4820, "uploaded_at": "2026-05-05T14:29:00Z"},
    {"filename": "invoice.json", "size_bytes": 2340, "uploaded_at": "2026-05-05T14:29:05Z"}
  ]
}
```

Updating a schema does not recompile transformations --- schemas are resolved at validation time, not compile time. The new schema takes effect on the next message.

== Testing Before Go-Live

The test endpoint is the most important step after uploading a transformation. It executes the transformation with sample input and returns the result without affecting metrics.

```bash
curl -X POST \
  -H "X-Admin-Key: $KEY" \
  -H "Content-Type: application/json" \
  -d '{"orderId": "12345", "amount": 250.00, "currency": "EUR"}' \
  https://<your-fqdn>/admin/transformations/invoice-to-ubl/test
```

On success:

```json
{
  "status": "ok",
  "output": {"Invoice": {"ID": "INV-12345", "Amount": 250.0, "Currency": "EUR"}},
  "duration_ms": 3
}
```

On failure:

```json
{
  "status": "error",
  "error": "Null reference: $input.customer.address",
  "line": 14,
  "column": 22
}
```

Always test before routing real traffic. The deployment workflow should be: upload, test, then allow traffic.

== Listing and Inspecting

List all deployed transformations:

```bash
curl -H "X-Admin-Key: $KEY" \
  https://<your-fqdn>/admin/transformations
```

```json
{
  "transformations": [
    {
      "name": "invoice-to-ubl",
      "strategy": "COMPILED",
      "status": "ready",
      "config": "explicit",
      "deployed_at": "2026-05-05T14:30:00Z",
      "messages_processed": 12345,
      "avg_transform_ms": 2.3
    }
  ]
}
```

Get details for a specific transformation, including the source code:

```bash
curl -H "X-Admin-Key: $KEY" \
  https://<your-fqdn>/admin/transformations/invoice-to-ubl
```

== Updating Transformations

Upload the same name again to replace a transformation. The update is atomic:

+ The new source is compiled.
+ If compilation fails, the upload is rejected --- the running transformation is untouched.
+ If compilation succeeds, the new version replaces the old one in the registry.
+ In-flight messages on the old version complete normally.
+ New messages use the new version immediately.

There is no downtime, no mode switch, and no restart.

== Deleting

Remove a single transformation:

```bash
curl -X DELETE \
  -H "X-Admin-Key: $KEY" \
  https://<your-fqdn>/admin/transformations/invoice-to-ubl
```

Remove everything and start fresh:

```bash
curl -X DELETE \
  -H "X-Admin-Key: $KEY" \
  https://<your-fqdn>/admin/bundle
```

== Exporting the Bundle

Download the current state as a ZIP file:

```bash
curl -H "X-Admin-Key: $KEY" \
  https://<your-fqdn>/admin/bundle -o bundle-backup.zip
```

This is useful for version control, backup, or migrating transformations to another container. The exported ZIP has the same format as the upload --- you can re-import it with `POST /admin/bundle`.

== Data Plane Discovery

Client applications can discover available transformations without an admin key. This endpoint runs on the data plane port (8085):

```bash
curl http://<ingress>:8085/api/transformations
```

```json
{
  "transformations": [
    {"name": "invoice-to-ubl", "status": "ready", "input": "json", "output": "xml"},
    {"name": "order-enrichment", "status": "ready", "input": "json", "output": "json"}
  ]
}
```

This makes the data plane self-describing. A client connecting for the first time can discover what transformations are available without out-of-band knowledge.

== Engine Info

Get basic operational information:

```bash
curl -H "X-Admin-Key: $KEY" \
  https://<your-fqdn>/admin/info
```

```json
{
  "version": "1.0.1",
  "uptime_seconds": 86400,
  "mode": "http",
  "workers": 4,
  "heap_max_mb": 3072,
  "data_dir": "/utlxe/data",
  "persistence": "volume-backed",
  "admin_key_set": true,
  "transformations": 3,
  "schemas": 2,
  "ready": true
}
```

== Two Deployment Workflows

The Admin API supports two workflows. Use whichever fits your process.

*Batch workflow* --- assemble a ZIP in your CI/CD pipeline and deploy in one call:

```bash
curl -X POST -H "X-Admin-Key: $KEY" \
  -F "file=@bundle.zip" \
  https://<your-fqdn>/admin/bundle
```

*Incremental workflow* --- upload resources one at a time:

```bash
# Upload schemas first
curl -X POST -H "X-Admin-Key: $KEY" -F "file=@order.xsd" .../admin/schemas/order.xsd

# Then transformations
curl -X POST -H "X-Admin-Key: $KEY" -F "source=@invoice.utlx" .../admin/transformations/invoice

# Export the assembled bundle for version control
curl -H "X-Admin-Key: $KEY" .../admin/bundle -o my-bundle.zip
```

Both workflows produce the same result. You can start with the incremental workflow during development, then switch to batch for production CI/CD.
