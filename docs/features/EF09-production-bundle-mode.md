# EF09: Production Bundle Mode (.utlar)

> **Canonical bundle format:** the on-disk layout and naming rules are specified in
> **[Bundle Format](../architecture/bundle-format.md)**. This doc owns the **locked `.utlar`
> deploy form** (manifest, locked mode, CI/CD) and defers to it for the general structure.

**Status:** **Implemented** (utlxe / Azure) — *this doc was stale at "Design"; corrected June 2026.*
**Priority:** High (production hardening for Azure Marketplace)  
**Created:** May 2026  
**Depends on:** EF03 (Admin API), EF02 (validation wiring)

> **As-built contract (source of truth = engine code; see `book-azure/UTLXe on Azure.pdf`):**
> - **Mode detection** — `engine/admin/BundleMode.kt::detectBundleMode`: exactly one
>   `<name>.utlar` ZIP on the data volume → **locked** mode (Admin API read-only except
>   operational endpoints); none → **open** mode (directory); >1 → ambiguous (uses first, logs error).
> - **`.utlar` = ZIP** of a bundle directory with layout:
>   `transformations/<name>/{transform.yaml, <name>.utlx}` + shared `schemas/` + a
>   **manifest** (`version`, `created`; sha256 checksum computed at load).
> - **Loading** — `engine/bundle/BundleLoader.kt` (open-dir scan of `transformations/`) and
>   `engine/admin/BundleMode.kt::loadUtlar` / `readManifestFromUtlar`; wired in `engine/Main.kt`.
> - Consumed by the IDE via **IF03** (Bundle Project Model & Explorer) against this exact layout.

---

## Summary

In production, transformations should be deployed via CI/CD — not edited at runtime via the Admin API. EF09 introduces a locked deployment mode based on a single `.utlar` archive file on disk. When UTLXe finds a `.utlar` file on the mounted volume, it enters locked mode: the Admin API becomes read-only (except operational endpoints like pause/resume), and all changes must go through CI/CD.

This matches enterprise expectations: MuleSoft, Tibco, SAP CPI, and Azure Logic Apps all enforce immutable production deployments.

## Two modes

| What's on disk | Mode | How determined |
|---|---|---|
| Directory structure (no .utlar) | **Open** (dev/test) | Automatic — no .utlar found |
| Any `.utlar` file (e.g., `sales.utlar`, `orders.utlar`) | **Locked** (acc/prd) | Automatic — any `.utlar` file found on volume |

No CLI flag needed — the mode is determined by what's on the mounted volume. If CI/CD placed a `.utlar` file (any name), it's production. If there's a directory tree (or nothing), it's development. Name the file after the business flow: `sales.utlar`, `orders.utlar`, `website.utlar`.

> **Open-mode project folder convention (`.utlxp`).** Engine open-mode detection is
> purely **structural** — *any* directory containing `transformations/` (the engine does
> **not** key off a suffix). The IDE (IF03) names the editable open-mode project directory
> **`<name>.utlxp/`** as a folder marker (cf. `.xcodeproj`), used for "New Bundle" and
> badging; it is **not** required for recognition. Build/Export packs a `<name>.utlxp/`
> into a locked **`<name>.utlar`**. So: **`.utlxp` (open, editable) → Build → `.utlar`
> (locked, deployed)**.

## Locked mode: Admin API restrictions

| Endpoint | Open mode | Locked mode | Rationale |
|----------|:---------:|:-----------:|-----------|
| `POST /admin/transformations/{name}` | Allowed | **Blocked (403)** | Changes go through CI/CD |
| `POST /admin/bundle` | Allowed | **Blocked (403)** | Deploy via CI/CD |
| `DELETE /admin/transformations/{name}` | Allowed | **Blocked (403)** | Deploy via CI/CD |
| `DELETE /admin/bundle` | Allowed | **Blocked (403)** | Deploy via CI/CD |
| `POST /admin/schemas/{filename}` | Allowed | **Blocked (403)** | Schemas are part of the bundle |
| `DELETE /admin/schemas/{filename}` | Allowed | **Blocked (403)** | Schemas are part of the bundle |
| `POST /admin/transformations/{name}/pause` | Allowed | **Allowed** | Operational — incident response |
| `POST /admin/transformations/{name}/resume` | Allowed | **Allowed** | Operational — resume after fix deployed |
| `POST /admin/transformations/{name}/validation` | Allowed | **Allowed** | Operational — emergency override |
| `DELETE /admin/transformations/{name}/validation` | Allowed | **Allowed** | Operational — revert override |
| `POST /admin/transformations/{name}/test` | Allowed | **Allowed** | Testing doesn't change state |
| `GET /admin/*` (all read endpoints) | Allowed | **Allowed** | Read-only always safe |
| `GET /admin/bundle` (export) | Allowed | **Allowed** | Returns the .utlar directly |

The 403 response in locked mode includes a clear message:

```json
{
  "error": "Production mode — transformations are deployed via CI/CD. Place a new bundle.utlar on the volume and restart.",
  "error_code": "BUNDLE_LOCKED",
  "mode": "locked",
  "bundle_version": "v3.2.1"
}
```

## The .utlar format

A `.utlar` file is a standard ZIP archive with a known directory structure and a manifest:

```
bundle.utlar (ZIP):
  manifest.json
  transformations/
    orders-in/
      orders-in.utlx
      transform.yaml              (optional — links inputs to schemas, sets strategy/policy)
    invoice-to-ubl/
      invoice-to-ubl.utlx
      transform.yaml
  schemas/
    order.json
    invoice.xsd
```

### manifest.json

```json
{
  "format": "utlar",
  "format_version": "1.0",
  "version": "v3.2.1",
  "checksum": "sha256:a1b2c3d4e5f6...",
  "created": "2026-05-07T10:00:00Z",
  "transformations": [
    {
      "name": "orders-in",
      "has_config": true,
      "input_schemas": ["order.json"],
      "output_schemas": ["invoice.xsd"],
      "input": { "queue": "orders-in" },
      "output": { "queue": "orders-out" }
    },
    {
      "name": "invoice-to-ubl",
      "has_config": true,
      "input_schemas": [],
      "output_schemas": [],
      "input": { "topic": "raw-invoices", "subscription": "utlxe-transform" },
      "output": { "topic": "normalized-invoices" }
    }
  ],
  "schemas": ["order.json", "invoice.xsd"],
  "messaging": {
    "queues": ["orders-in", "orders-out"],
    "topics": [
      { "name": "raw-invoices", "subscription": "utlxe-transform" },
      { "name": "normalized-invoices" }
    ],
    "eventhubs": []
  }
}
```

The manifest enables:
- **Fast startup**: compare manifest version to last loaded → skip reload if same
- **Validation**: verify checksum on startup → reject tampered bundles
- **Visibility**: `GET /admin/info` shows bundle version without unpacking
- **Dapr component generation**: the `messaging` summary tells UTLXe (or CI/CD) exactly which Dapr components are needed — without parsing every transform.yaml

### transform.yaml per transformation

Links inputs to schemas, declares operational settings, and configures messaging:

```yaml
strategy: COMPILED
validationPolicy: strict
maxConcurrent: 4

# Schema validation (references files in schemas/ directory)
inputs:
  - name: input
    schema: order.json
output:
  schema: invoice.xsd

# Messaging — declares WHAT to connect to (auth comes from environment)
# The field name IS the discriminator: queue / topic / eventhub
input:
  queue: orders-in                      # → Service Bus queue (Dapr binding)
output:
  queue: orders-out                     # → Service Bus queue (Dapr binding)
```

**Messaging field reference:**

| Field | Azure service | Dapr component type | Dapr building block |
|---|---|---|---|
| `queue: name` | Service Bus Queue | `bindings.azure.servicebusqueues` | Binding |
| `topic: name` | Service Bus Topic | `pubsub.azure.servicebus.topics` | Pub/Sub |
| `eventhub: name` | Event Hub | `bindings.azure.eventhubs` (default) or `pubsub.azure.eventhubs` (if `consumerGroup` set) | Binding or Pub/Sub |

**Input-only fields:**

| Field | When | Purpose |
|---|---|---|
| `subscription: name` | `topic` input | Service Bus subscription name (required for topic input) |
| `consumerGroup: name` | `eventhub` input | Event Hub consumer group (triggers pub/sub mode, requires checkpoint storage) |

**Mix-and-match is allowed.** Input and output can use different services and patterns:

```yaml
# Queue in, topic out (fan-out pattern)
input:
  queue: orders-in
output:
  topic: processed-orders

# Event Hub in, queue out (stream-to-action)
input:
  eventhub: iot-telemetry
  consumerGroup: utlxe               # → pub/sub mode with checkpointing
output:
  queue: alerts-out

# Topic in, topic out (pub/sub chain)
input:
  topic: raw-invoices
  subscription: utlxe-transform
output:
  topic: normalized-invoices

# Queue in, queue out (simple point-to-point)
input:
  queue: orders-in
output:
  queue: orders-out
```

The `schema` field references a file in the `schemas/` directory of the archive. The engine resolves it during loading.

### Security: what's in the bundle vs the environment

The bundle declares **what** to connect to (queue/topic/eventhub names). The environment provides **how** to authenticate. Secrets never go in the bundle — the same .utlar deploys to dev, acc, and prd.

| Concern | Where | Example |
|---|---|---|
| Queue/topic/eventhub names | **Bundle** (transform.yaml) | `queue: orders-in` |
| Authentication | **Environment** (Dapr config) | Managed identity or secret store ref |
| Namespace / connection | **Environment** (Dapr config) | `namespaceName: myco.servicebus.windows.net` |

**Recommended: Azure Managed Identity (secretless)**

```yaml
# Environment config — deployed by Bicep, NOT in bundle
apiVersion: dapr.io/v1alpha1
kind: Component
metadata:
  name: utlxe-servicebus
spec:
  type: bindings.azure.servicebusqueues
  metadata:
    - name: namespaceName
      value: "mycompany.servicebus.windows.net"    # FQDN for Service Bus
    # No connectionString, no keys — system-assigned managed identity handles auth
```

**Supported auth methods (all Azure messaging components):**

| Method | Secrets? | Fields needed |
|---|---|---|
| System-assigned managed identity | **No** | `namespaceName` only |
| User-assigned managed identity | **No** | `namespaceName` + `azureClientId` |
| Service principal | Yes | `azureTenantId` + `azureClientId` + `azureClientSecret` |
| Connection string | Yes | `connectionString` |

Note: `namespaceName` (FQDN) is for Service Bus. Event Hub uses `eventHubNamespace` (just the name, not FQDN).

## Startup sequence

```
Container starts
  ↓
Scan mount point (/utlxe/data/)
  ↓
Found bundle.utlar? ──YES──→ LOCKED mode
  │                            ↓
  │                       Read manifest.json
  │                            ↓
  │                       Compare version to last loaded
  │                            ↓
  │                       Same? → skip reload (fast restart)
  │                       Different? → full reload:
  │                            ↓
  │                       Unpack to memory
  │                       Load schemas into SchemaStore
  │                       Compile all transformations
  │                       Create validators from transform.yaml
  │                            ↓
  │                       Admin API: locked mode
  │                       Health: ready=true, mode=locked
  │
  NO
  ↓
Open mode (directory scan as today)
  ↓
Admin API: full access
Health: ready=true, mode=open
```

## CI/CD deployment flow

### Terraform / ARM

```hcl
# Terraform: deploy bundle.utlar to Azure Files
resource "azurerm_storage_share_file" "bundle" {
  name             = "bundle.utlar"
  storage_share_id = azurerm_storage_share.utlxe_data.id
  source           = "${path.module}/artifacts/bundle.utlar"
}

# Restart container to pick up new bundle
resource "null_resource" "restart_utlxe" {
  triggers = {
    bundle_hash = filesha256("${path.module}/artifacts/bundle.utlar")
  }
  provisioner "local-exec" {
    command = "az containerapp revision restart -n utlxe -g ${var.resource_group}"
  }
}
```

### GitHub Actions

```yaml
- name: Deploy bundle
  run: |
    # Upload .utlar to Azure Files
    az storage file upload \
      --share-name utlxe-data \
      --source bundle.utlar \
      --path bundle.utlar

    # Restart to pick up new bundle
    az containerapp revision restart -n utlxe -g $RG
```

### Azure DevOps

```yaml
- task: AzureCLI@2
  inputs:
    scriptType: bash
    scriptLocation: inlineScript
    inlineScript: |
      az storage file upload --share-name utlxe-data --source $(Build.ArtifactStagingDirectory)/bundle.utlar --path bundle.utlar
      az containerapp revision restart -n utlxe -g $(ResourceGroup)
```

## Building a .utlar

### From a directory

```bash
# Build .utlar from directory structure
cd my-transformations/
zip -r ../bundle.utlar manifest.json transformations/ schemas/
```

### Via Admin API (export from dev/test)

```bash
# Export current state as .utlar
curl -H "X-Admin-Key: $KEY" http://dev-utlxe:8081/admin/bundle -o bundle.utlar

# Deploy to production
az storage file upload --share-name utlxe-data --source bundle.utlar --path bundle.utlar
az containerapp revision restart -n utlxe -g prod-rg
```

### Via CLI tool (future)

```bash
# Validate and package
utlx bundle pack my-transformations/ -o bundle.utlar --validate
```

## Engine info shows the mode

```json
GET /admin/info

{
  "version": "1.0.1",
  "mode": "locked",
  "bundle_version": "v3.2.1",
  "bundle_checksum": "sha256:a1b2c3...",
  "bundle_created": "2026-05-07T10:00:00Z",
  "transformations": 2,
  "schemas": 2,
  "ready": true,
  "admin_key_set": true,
  "data_dir": "/utlxe/data"
}
```

In open mode:

```json
{
  "version": "1.0.1",
  "mode": "open",
  "bundle_version": null,
  "transformations": 3,
  "schemas": 1,
  "ready": true
}
```

## Atomic writes (open mode Admin API)

When the Admin API is in open mode AND `--bundle-file` flag is set, every write operation rewrites the .utlar atomically:

1. Write `bundle.utlar.tmp` (new archive with the change)
2. Rename `bundle.utlar.tmp` → `bundle.utlar` (atomic on all filesystems)
3. Old archive is replaced in one operation — no partial state

This means even in open mode with a .utlar, the archive is always consistent.

## Files to implement

| File | Change |
|------|--------|
| New: `modules/engine/.../admin/BundleMode.kt` | Mode detection (open/locked), .utlar reading, manifest parsing |
| `modules/engine/.../admin/AdminEndpoint.kt` | Check mode before mutating endpoints, return 403 in locked mode |
| `modules/engine/.../UtlxEngine.kt` | Startup: detect .utlar, load from archive, set mode |
| `modules/engine/.../Main.kt` | `--bundle-file` CLI flag (alternative to `--data-dir`) |
| `modules/engine/.../health/HealthEndpoint.kt` | Show mode in health response |

## Effort estimate

| Task | Effort |
|------|--------|
| .utlar reading (ZIP with manifest) | 1 day |
| Locked mode enforcement in Admin API | 0.5 day |
| Manifest creation/validation | 0.5 day |
| Mode detection on startup | 0.5 day |
| Atomic .utlar writes (open mode) | 0.5 day |
| `--bundle-file` CLI flag | 0.5 day |
| Info endpoint: mode/version/checksum | 0.5 day |
| Tests | 1 day |
| **Total** | **~5 days** |

## Pause behavior in production: two mechanisms

### The problem

When an operator pauses a transformation (e.g., downstream system in maintenance), the current implementation returns HTTP 503. Dapr treats this as a failure → abandons the message → Service Bus increments delivery count → after `maxDeliveryCount` retries → message goes to the dead-letter queue.

This is wrong for a deliberate pause. The operator wants messages to **wait in the queue**, not dead-letter.

### Two complementary mechanisms

| Scenario | Mechanism | Granularity | Latency |
|---|---|---|---|
| Pause **one** transformation | **429 + Retry-After + Resiliency circuit breaker** | Per-transformation | Immediate (429), circuit opens after 3 failures |
| Pause **everything** (full maintenance) | **Dapr App Health Check** (`/healthz` → 503) | Global (all bindings + subscriptions) | ~15s (3 × 5s probes, configurable) |
| Graceful shutdown | **Dapr App Health Check** | Global | ~15s |

Both mechanisms prevent dead-lettering. Use 429 for surgical per-transformation pause. Use app health for global maintenance windows.

### Mechanism 1: Per-transformation — 429 + Retry-After + Dapr Resiliency

UTLXe returns **HTTP 429 (Too Many Requests)** for deliberately paused transformations, with a `Retry-After` header:

```
HTTP/1.1 429 Too Many Requests
Retry-After: 300
Content-Type: application/json

{
  "error": "Transformation 'orders-in' is paused by operator",
  "error_code": "TRANSFORMATION_PAUSED",
  "retry_after_seconds": 300
}
```

The semantic difference matters:
- **503 Service Unavailable** = "I'm broken, retry might help" → Dapr retries aggressively → delivery count climbs → DLQ
- **429 Too Many Requests** = "I'm deliberately refusing, back off" → signals backpressure

**Required Dapr Resiliency configuration:**

Even with 429, Dapr needs a circuit breaker to stop hammering UTLXe during a long pause. This YAML must be deployed alongside UTLXe:

```yaml
apiVersion: dapr.io/v1alpha1
kind: Resiliency
metadata:
  name: utlxe-resiliency
spec:
  policies:
    circuitBreakers:
      pauseBreaker:
        maxRequests: 1
        timeout: 300s              # stop trying for 5 minutes
        trip: consecutiveFailures > 3
  targets:
    apps:
      utlxe:
        circuitBreaker: pauseBreaker
```

Flow during per-transformation pause:
```
Message 1 → UTLXe returns 429 → Dapr: failure count = 1
Message 2 → UTLXe returns 429 → Dapr: failure count = 2
Message 3 → UTLXe returns 429 → Dapr: failure count = 3 → CIRCUIT OPENS
  ↓
Dapr stops calling UTLXe for 5 minutes
Messages stay in Service Bus queue (lock expires naturally)
Delivery count does NOT increment (Dapr isn't trying)
  ↓
After 5 minutes: Dapr half-opens → tries one message
  If still paused → 429 → circuit opens again for 5 more minutes
  If resumed → 200 → circuit closes → normal flow resumes
  ↓
All queued messages drain naturally
No dead-lettering during maintenance window
```

**Service Bus configuration for pause tolerance:**

Increase `maxDeliveryCount` to tolerate the initial retries before the circuit breaker opens:

```yaml
# Dapr component metadata
- name: maxDeliveryCount
  value: "100"              # default 10 is too low for pause scenarios
```

### Mechanism 2: Global — Dapr App Health Check

Dapr's built-in app health check feature provides a global kill switch. When enabled, Dapr probes `GET /healthz` on the app at a configurable interval. When the probe fails N consecutive times, Dapr **stops all input bindings and unsubscribes from all pub/sub topics**. When the probe succeeds again, Dapr **automatically resumes** everything.

**Dapr Configuration:**

```yaml
apiVersion: dapr.io/v1alpha1
kind: Configuration
metadata:
  name: utlxe-config
spec:
  appHealthCheck:
    path: "/healthz"
    probeInterval: "5s"          # check every 5 seconds
    probeTimeout: "500ms"        # timeout per probe
    threshold: 3                 # 3 consecutive failures → unhealthy
```

**UTLXe implementation:**

When **all** transformations are paused (or operator triggers global maintenance):
- `/healthz` returns 503
- Dapr marks app unhealthy after 3 probes (~15s)
- All bindings and subscriptions stop
- Messages stay in Service Bus

When operator resumes:
- `/healthz` returns 200
- Dapr marks app healthy on next probe
- All bindings and subscriptions resume
- Queued messages drain

**Key insight:** App health is **global** — it pauses ALL bindings and subscriptions, not just one. This is why it complements rather than replaces the per-transformation 429 mechanism:

- **Single transformation per container** (production/locked mode): app health alone is sufficient
- **Multiple transformations per container** (dev/test): use 429 for per-transformation, app health for global maintenance

### What changes in UTLXe

| Current | Change |
|---------|--------|
| Paused → return 503 | Per-transformation pause → return **429** with `Retry-After: 300` |
| No global pause | Global maintenance → `/healthz` returns 503 → Dapr stops all delivery |
| Log: "transformation paused" | Log: "PAUSED by operator — messages will retry via Dapr Resiliency circuit breaker" |
| No header | Add `Retry-After` header (seconds) on 429 |

The Retry-After value should match the Dapr circuit breaker timeout (default 300s / 5 minutes). It can be configurable per transformation.

### Pause lifecycle in locked mode

In locked mode (production), pause/resume is the primary operational lever:

```
Normal operation:
  Messages flow → transform → output → complete

Operator pauses ONE transformation (targeted):
  POST /admin/transformations/orders-in/pause
  → UTLXe returns 429 to Dapr for orders-in messages
  → Circuit breaker opens for that binding
  → Other transformations continue processing
  → Messages for orders-in queue in Service Bus

Operator pauses ALL (global maintenance):
  POST /admin/maintenance/pause
  → /healthz returns 503
  → Dapr stops ALL bindings after ~15s
  → All messages queue in Service Bus

Operator resumes one transformation:
  POST /admin/transformations/orders-in/resume
  → Circuit breaker half-opens on next attempt
  → UTLXe returns 200 → circuit closes → messages drain

Operator resumes all (end maintenance):
  POST /admin/maintenance/resume
  → /healthz returns 200
  → Dapr resumes all bindings
  → All queued messages drain

Operator can verify:
  GET /admin/transformations
  → {"name": "orders-in", "status": "paused"}
  
  GET /admin/transformations/orders-in/errors
  → (check if errors were accumulating before pause)
```

### Documentation requirement

Both the Dapr Resiliency YAML and the App Health Check configuration must be included in the Azure Marketplace Bicep template as defaults. Customers should not need to discover these themselves — they should work out of the box.

## Relationship to other features

- **EF03** (Admin API): locked mode disables mutating endpoints; pause/resume stays enabled
- **EF02** (validation): schemas in .utlar resolved the same way as schemas in directory
- **EF05** (Dapr fixes): 429 vs 503 distinction for pause vs not-loaded; app health check for global pause
- **EF06** (Dapr pub/sub): subscriptions derived from .utlar contents
- **EF10** (dynamic Dapr bindings): open mode writes binding YAML via HotReload; locked mode uses static CI/CD bindings
- **EF08** (.NET SDK): `IBundleStore.EmbeddedBundleStore` can read .utlar format

---

*Feature EF09. May 2026. Design document.*
*Key insight: the presence of bundle.utlar on the volume IS the production lock. No CLI flag, no config — just deploy the file and the engine knows it's production. Pause uses 429 + Dapr Resiliency circuit breaker to prevent dead-lettering during maintenance.*
