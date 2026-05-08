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

Both ports are always active. There is no mode switch --- you can upload transformations while the data plane processes messages. See the architecture decision in the design document for the rationale.

== Authentication

Every request to `/admin/*` endpoints must include the `X-Admin-Key` header:

```bash
curl -H "X-Admin-Key: my-secret-key-here" \
  http://<internal-ip>:8081/admin/transformations
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
  http://<admin>:8081/admin/transformations/invoice-to-ubl
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
  http://<admin>:8081/admin/transformations/invoice-to-ubl
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

=== ZIP Bundle

For batch deployment, upload a ZIP containing all transformations and schemas at once. This replaces the entire bundle atomically.

```bash
curl -X POST \
  -H "X-Admin-Key: $KEY" \
  -F "file=@bundle.zip" \
  http://<admin>:8081/admin/bundle
```

The ZIP follows this structure:

```
bundle.zip
  schemas/
    order.xsd
    invoice.json
  transformations/
    invoice-to-ubl/
      invoice-to-ubl.utlx
    order-enrichment/
      order-enrichment.utlx
```

The `schemas/` directory is optional. Each transformation directory requires only the `.utlx` source file. A `transform.yaml` can be added alongside the `.utlx` to override defaults (strategy, validation policy, output binding) --- but it is not required.

== Managing Schemas

Schemas are shared resources, stored separately from transformations. A single schema like `order.xsd` can be referenced by multiple transformations.

Upload a schema:

```bash
curl -X POST \
  -H "X-Admin-Key: $KEY" \
  -F "file=@order.xsd" \
  http://<admin>:8081/admin/schemas/order.xsd
```

List all schemas:

```bash
curl -H "X-Admin-Key: $KEY" \
  http://<admin>:8081/admin/schemas
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
  http://<admin>:8081/admin/transformations/invoice-to-ubl/test
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
  http://<admin>:8081/admin/transformations
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
  http://<admin>:8081/admin/transformations/invoice-to-ubl
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
  http://<admin>:8081/admin/transformations/invoice-to-ubl
```

Remove everything and start fresh:

```bash
curl -X DELETE \
  -H "X-Admin-Key: $KEY" \
  http://<admin>:8081/admin/bundle
```

== Exporting the Bundle

Download the current state as a ZIP file:

```bash
curl -H "X-Admin-Key: $KEY" \
  http://<admin>:8081/admin/bundle -o bundle-backup.zip
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
  http://<admin>:8081/admin/info
```

```json
{
  "version": "1.0.1",
  "uptime_seconds": 86400,
  "mode": "http",
  "workers": 4,
  "heap_max_mb": 1536,
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
  http://<admin>:8081/admin/bundle
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
