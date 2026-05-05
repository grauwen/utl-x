# EF03: Bundle Management API

**Status:** Design  
**Priority:** High (required for Azure Marketplace offering)  
**Created:** May 2026

---

## Summary

The Azure Marketplace delivers UTLXe as a pre-built container. Customers should not need to build custom Docker images or set up Azure Files to deploy their transformations. EF03 adds a REST management API on the health/admin port (8081) that allows customers to upload, list, update, and remove transformations, schemas, and full bundles via HTTP.

The API supports two workflows: **batch** (upload a complete ZIP bundle) and **API-first** (build up the bundle incrementally via individual REST calls). Both produce the same result — compiled transformations ready to process messages on the data plane.

## Problem

When a customer deploys UTLXe from the Azure Marketplace, they get a running container with zero transformations. Currently there is no supported way to deploy transformations without:

- Building a custom Docker image (defeats the purpose of a managed offering)
- Mounting an Azure File Share (operational burden, requires infrastructure setup)
- Writing custom entrypoint scripts to download from Blob Storage

The engine already supports dynamic loading internally — `stdio-proto` has `LoadTransformation` messages, and the HTTP mode help text says "Transforms loaded dynamically via REST endpoints." But the HTTP management surface does not exist yet.

## Architecture Decision: Single Bundle per Container

**Decision:** One bundle per container, many transformations per bundle.

| Need | Solution |
|------|----------|
| Multiple transformations | One bundle, multiple entries in `transformations/` |
| Update one transformation | `POST /admin/transformations/{name}` |
| Separate scaling or SLA | Separate Container App instances, each with their own bundle |
| Logical grouping | Naming convention: `inbound-invoice`, `outbound-invoice` |

**Why not multiple bundles?**

A bundle is a deployment unit — a directory of transformations that belong together. The question is whether a customer needs multiple deployment units in one container.

If transformations need different scaling, different configs, or different SLAs, they belong in **separate containers**. That is what containers are for. Putting multiple bundles in one container means conflict resolution (what if two bundles define `invoice-to-ubl`?), merge semantics (replace or append?), and partial failure handling (one bundle fails to compile — do the others stay?). This complexity solves no problem that isn't already handled by either "multiple transformations in one bundle" or "multiple containers."

The single-bundle model keeps the mental model simple: upload replaces everything. The per-transformation endpoints (`POST /admin/transformations/{name}`) handle incremental updates without the overhead of multi-bundle management.

## Architecture Decision: No Modes — Admin and Data Plane Always On

**Decision:** There is no "design mode" or "running mode." The admin API (port 8081) and the data plane (port 8085) are always active simultaneously.

**Why not a mode switch?**

The admin API is **operational management** (deploying, updating, removing transformations), not design time. Design time is what `utlxd` (the IDE daemon) does — schema analysis, live preview, autocompletion. The admin API is closer to `kubectl apply` than to an IDE.

| Activity | Tool | Port | When |
|----------|------|------|------|
| Write and test a transformation | `utlxd` (IDE) | — | Development |
| Deploy a transformation to production | Admin API | 8081 | Operations |
| Process messages | Data plane | 8085 | Runtime |

A mode switch would force a choice:

| Hypothetical mode | Admin API | Data plane | Problem |
|-------------------|-----------|------------|---------|
| "Design" | Enabled | Disabled | Messages queue up or get rejected — SLA violation |
| "Running" | Disabled | Enabled | Can't deploy updates without switching mode — downtime |
| "Both" | Enabled | Enabled | This is just... always on. No mode needed. |

The only combination that makes sense is "both enabled."

**Live updates are safe because of atomic hot-swap:**

1. **Hot-swap a transformation** — new version compiled, atomically replaces old in the registry. In-flight messages drain on the old version (they hold a reference). New messages use the new version. Zero downtime.
2. **Upload a new schema** — schemas are resolved at validation time (per message), not at compile time. Replacing a schema file takes effect on the next message. No recompile, no restart.
3. **Add a new transformation** — appears in the registry, immediately available. Existing transformations unaffected.
4. **Remove a transformation** — removed from registry. New requests get 404. In-flight messages complete normally.
5. **Full bundle replace** — atomic swap of the entire registry.

**The only guard is compilation validation:** a bad upload is rejected with a 400 response and error details. The running system is untouched. The `POST /admin/bundle/validate` endpoint (dry run) exists for CI/CD pipelines that want to validate before deploying.

**Dependency warnings (not blocks):**

| Scenario | Behavior |
|----------|----------|
| Upload transformation referencing a schema not yet uploaded | Compilation succeeds (schema is runtime). Response includes: `"warnings": ["referenced schema 'order.xsd' not found"]` |
| Delete a schema referenced by transformations | Schema removed. Response includes: `"warnings": ["schema 'order.xsd' is referenced by 2 transformations"]` |
| Replace schema with incompatible version | Next messages may fail validation. Customer's responsibility — same as updating an XSD in any system. |

The API warns but does not block on dependency issues. The customer knows their deployment order. Blocking creates rigidity; warnings create awareness.

---

## Architecture Decision: Schemas as Shared Resources

**Decision:** Schemas are top-level resources, not embedded in transformation directories.

A schema like `order.xsd` may be referenced by multiple transformations (`invoice-to-ubl`, `validate-order`, `enrich-order`). Embedding schemas inside each transformation directory creates copies that must stay in sync — a maintenance hazard.

Instead, schemas live in a shared `schemas/` directory and are uploaded independently:

```
/utlxe/data/
  schemas/
    order.xsd              ← single source of truth
    invoice.json
  transformations/
    invoice-to-ubl/
      invoice-to-ubl.utlx  (header: input json {schema: "order.xsd"})
    validate-order/
      validate-order.utlx  (header: input json {schema: "order.xsd"})
```

Updating a schema does **not** recompile transformations — schemas are resolved at validation time, not compile time. This means a schema update takes effect immediately for the next message without any downtime.

## Architecture Decision: .utlx Alone is Enough

**Decision:** A transformation can be deployed with just a `.utlx` file. The `transform.yaml` config is optional.

When no `transform.yaml` is provided, sensible defaults apply:

```yaml
# Implicit defaults
strategy: COMPILED
inputFormat: auto        # detect from .utlx header or message content
outputFormat: auto
```

This lowers the barrier to entry — the simplest possible deployment is one file, one command:

```bash
curl -X POST -H "X-Admin-Key: $KEY" \
  -F "source=@invoice-to-ubl.utlx" \
  http://admin:8081/admin/transformations/invoice-to-ubl
```

The customer can add or update the config later via `POST /admin/transformations/{name}/config` without recompiling the transformation source.

## Architecture Decision: Transformation Names Are Unique

**Decision:** Each transformation name must be unique within a container. Uploading a transformation with an existing name **replaces** it (upsert semantics).

The name is the identity — it maps to a directory under `/utlxe/data/transformations/{name}/` and to the data plane URL `POST /api/transform/{name}`. Duplicate names would create ambiguity: which transformation does the request hit?

**Scenarios that seem to need duplicates, and their solutions:**

| Scenario | Seems like you need... | Better solution |
|----------|----------------------|-----------------|
| Canary / A/B testing | v1 and v2 of same name | Two container instances behind Azure traffic splitting (90/10) |
| Multi-tenant | Same name, different logic per tenant | Separate container per tenant, or naming convention: `tenantA-invoice-to-ubl` |
| Regional variants | EU and US versions | Different names: `invoice-to-ubl-eu`, `invoice-to-ubl-us`. Or separate containers per region. |
| Large org, many teams | Risk of naming collisions | Team-prefixed names: `finance-invoice-to-ubl`, `logistics-invoice-to-ubl` |

Every scenario is better solved by **different names** or **different containers**. The uniqueness constraint pushes toward good architecture — one concern per container, names that reflect purpose.

---

## Design

### Management API (port 8081)

The management API runs on the existing health/admin port, separate from the data plane (8085). This allows network isolation — the Container App exposes only 8085 via ingress, while 8081 stays internal to the VNet.

#### Bundle endpoints (batch workflow)

| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/admin/bundle` | Upload a `.zip` bundle (replaces everything) |
| `GET` | `/admin/bundle` | Export current state as downloadable `.zip` |
| `DELETE` | `/admin/bundle` | Remove all transformations and schemas (returns to empty state) |
| `POST` | `/admin/bundle/validate` | Upload and validate without deploying (dry run) |

#### Transformation endpoints (incremental workflow)

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/admin/transformations` | List all deployed transformations |
| `GET` | `/admin/transformations/{name}` | Get transformation details (config, compile status, metrics, source) |
| `POST` | `/admin/transformations/{name}` | Deploy or update a transformation (`.utlx` required, config optional) |
| `POST` | `/admin/transformations/{name}/config` | Update config only (no recompile) |
| `DELETE` | `/admin/transformations/{name}` | Remove a single transformation |

#### Schema endpoints (shared resources)

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/admin/schemas` | List all uploaded schemas |
| `GET` | `/admin/schemas/{filename}` | Download a schema file |
| `POST` | `/admin/schemas/{filename}` | Upload or replace a schema file |
| `DELETE` | `/admin/schemas/{filename}` | Remove a schema |

#### Testing endpoint

| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/admin/transformations/{name}/test` | Run a transformation with sample input, return output or error |

Send a test message through a deployed transformation without touching the data plane. Essential for verifying a newly uploaded transformation before routing real traffic.

```json
// Request
POST /admin/transformations/invoice-to-ubl/test
Content-Type: application/json

{"orderId": "12345", "amount": 100.00, "customer": {"name": "Acme Corp"}}

// Success response
{
  "status": "ok",
  "output": {"Invoice": {"ID": "12345", "BuyerParty": {"Name": "Acme Corp"}, ...}},
  "duration_ms": 3
}

// Failure response
{
  "status": "error",
  "error": "Null reference: $input.customer.address",
  "line": 14,
  "column": 22
}
```

The test endpoint uses the same compiled transformation as the data plane — the result is exactly what a real message would produce. The difference: test calls are not counted in Prometheus metrics and do not affect `messages_processed` counters.

#### Operational endpoints

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/admin/info` | Engine version, uptime, config, persistence mode |
| `POST` | `/admin/transformations/{name}/pause` | Stop processing for this transformation (503 on data plane) |
| `POST` | `/admin/transformations/{name}/resume` | Resume processing |
| `GET` | `/admin/transformations/{name}/errors` | Recent errors (ring buffer, last 100) |
| `GET` | `/admin/config` | View current engine configuration |
| `POST` | `/admin/config` | Update engine configuration (partial, runtime-safe fields only) |

#### Validation override endpoints

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/admin/transformations/{name}/validation` | Get effective validation state (policy, source, config default) |
| `POST` | `/admin/transformations/{name}/validation` | Set a runtime validation override (does not modify config on disk) |
| `DELETE` | `/admin/transformations/{name}/validation` | Remove runtime override (reverts to config/header default) |

Runtime validation overrides are ephemeral — they don't touch the `transform.yaml` on disk. On container restart, the override is gone and the config-file or header policy applies again. This is an incident management tool: disable validation immediately, investigate, fix the schema or data, then remove the override.

```json
// Disable validation during an incident
POST /admin/transformations/invoice-to-ubl/validation
{"policy": "off"}

→ 200 OK
{"policy": "off", "source": "runtime-override", "config_policy": "strict", "header_policy": "strict"}

// Check current effective state
GET /admin/transformations/invoice-to-ubl/validation

→ 200 OK
{
  "effective_policy": "off",
  "source": "runtime-override",
  "config_policy": "strict",
  "header_policy": "strict"
}

// Remove override — back to config/header default
DELETE /admin/transformations/invoice-to-ubl/validation

→ 200 OK
{"effective_policy": "strict", "source": "config"}
```

**Precedence chain (highest wins):**

```
runtime override  →  transform.yaml config  →  .utlx header  →  default (off)
   (ephemeral)         (on disk, EF03)         (in source)      (no validation)
```

This extends the EF02 precedence model with a new top-level override. The runtime override is the only level that is ephemeral — all others persist across restarts.

**Engine info** — basic operational visibility:

```json
GET /admin/info

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

**Pause / resume** — stop processing for a specific transformation without removing it. When paused:
- Data plane returns 503 for that transformation (others continue)
- Dapr/Service Bus messages are not acknowledged (they retry or go to DLQ)
- The transformation stays compiled and on disk — resume is instant
- `GET /admin/transformations/{name}` shows `"status": "paused"`

This fills the gap between "everything is fine" and "delete the transformation." During an incident, you pause the problematic transformation, investigate, fix, upload a new version, then resume.

**Recent errors** — ring buffer of the last N errors per transformation:

```json
GET /admin/transformations/invoice-to-ubl/errors?limit=20

{
  "errors": [
    {
      "timestamp": "2026-05-05T14:32:01Z",
      "message": "Null reference: $input.customer.address",
      "line": 14,
      "input_preview": "{\"orderId\":\"12345\",\"customer\":{\"name\":\"Acme\"}}"
    },
    {
      "timestamp": "2026-05-05T14:32:03Z",
      "message": "Schema validation failed: 'amount' is required",
      "input_preview": "{\"orderId\":\"12346\",\"customer\":{...}}"
    }
  ],
  "total_errors": 47,
  "showing": 20
}
```

Prometheus gives error counters but not the actual messages. This endpoint provides quick diagnosis without digging through container logs. The `input_preview` is truncated (first 200 characters) to avoid exposing full message payloads.

**Engine config** — view and update runtime-safe configuration:

```json
GET /admin/config

{
  "maxInputSize": "5MB",
  "workers": 4,
  "healthPort": 8081,
  "dataPort": 8085
}

POST /admin/config
{"maxInputSize": "10MB"}

→ 200 OK {"updated": ["maxInputSize"], "restart_required": []}
```

Only a subset of config fields are safe to change at runtime. Fields that require restart (like port numbers) are accepted but flagged in the response: `"restart_required": ["healthPort"]`.

#### Data plane discovery (port 8085)

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/api/transformations` | List available transformations (no admin key required) |

Client applications need to discover what transformations are available. This endpoint is on the data plane (8085) and does not require authentication — it's part of the public API.

```json
GET /api/transformations

{
  "transformations": [
    {"name": "invoice-to-ubl", "status": "ready", "input": "json", "output": "xml"},
    {"name": "validate-order", "status": "ready", "input": "json", "output": "json"},
    {"name": "enrich-order", "status": "paused", "input": "json", "output": "json"}
  ]
}
```

This makes the data plane self-describing. A client connecting for the first time can discover available transformations without out-of-band knowledge.

### Two deployment workflows

#### Batch workflow: ZIP upload

For CI/CD pipelines that build the complete bundle in a build step and deploy it in one call.

```bash
# Deploy everything at once
curl -X POST -H "X-Admin-Key: $KEY" \
  -F "file=@bundle.zip" \
  http://admin:8081/admin/bundle
```

#### API-first workflow: incremental build

For interactive development, scripting, or pipelines that manage resources individually.

```bash
# Step 1: Upload shared schemas
curl -X POST -H "X-Admin-Key: $KEY" \
  -F "file=@order.xsd" \
  http://admin:8081/admin/schemas/order.xsd

curl -X POST -H "X-Admin-Key: $KEY" \
  -F "file=@invoice.json" \
  http://admin:8081/admin/schemas/invoice.json

# Step 2: Deploy transformations (just the .utlx — config is optional)
curl -X POST -H "X-Admin-Key: $KEY" \
  -F "source=@invoice-to-ubl.utlx" \
  http://admin:8081/admin/transformations/invoice-to-ubl

curl -X POST -H "X-Admin-Key: $KEY" \
  -F "source=@validate-order.utlx" \
  -F "config=@transform.yaml" \
  http://admin:8081/admin/transformations/validate-order

# Step 3: Export the assembled bundle for version control
curl -H "X-Admin-Key: $KEY" \
  http://admin:8081/admin/bundle -o bundle-backup.zip
```

Both workflows produce the same result. The customer can start with API-first, then switch to batch when they have a CI/CD pipeline. Or mix: deploy a bundle, then update individual transformations or schemas.

### Bundle ZIP format

The upload ZIP follows the existing `BundleLoader` directory convention, extended with a `schemas/` directory:

```
bundle.zip
  schemas/                          (optional — shared schema files)
    order.xsd
    invoice.json
  transformations/
    invoice-to-ubl/
      invoice-to-ubl.utlx          (required)
      transform.yaml               (optional — defaults apply)
    validate-ubl/
      validate-ubl.utlx
      transform.yaml
  engine.yaml                      (optional — engine config overrides)
```

### Single transformation upload

`POST /admin/transformations/{name}` accepts `multipart/form-data`:

- `source` — the `.utlx` file (required)
- `config` — the `transform.yaml` file (optional, defaults to COMPILED strategy)

Or a small `.zip` containing both files.

### Hot-swap

When a transformation is uploaded:

1. Parse and compile the `.utlx` source
2. If compilation fails → return 400 with error details, existing transformation unchanged
3. If compilation succeeds → atomic replace in the `TransformationRegistry`
4. In-flight messages on the old version drain naturally (they hold a reference to the old compiled transformation)
5. New messages use the new version immediately

Full bundle upload (`POST /admin/bundle`) follows the same pattern but replaces all transformations atomically.

### Response format

```json
// POST /admin/bundle — success
{
  "status": "deployed",
  "transformations": [
    {"name": "invoice-to-ubl", "strategy": "COMPILED", "status": "ready"},
    {"name": "validate-ubl", "strategy": "COMPILED", "status": "ready"}
  ],
  "schemas": ["order.xsd", "invoice.json"],
  "compiled_in_ms": 342
}

// POST /admin/bundle — compilation error
{
  "status": "rejected",
  "errors": [
    {"transformation": "invoice-to-ubl", "line": 14, "message": "Unknown function: concatX"}
  ]
}

// POST /admin/transformations/{name} — success (single .utlx, no config)
{
  "status": "deployed",
  "name": "invoice-to-ubl",
  "strategy": "COMPILED",
  "config": "defaults",
  "compiled_in_ms": 48
}

// GET /admin/transformations
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
    },
    {
      "name": "validate-order",
      "strategy": "COMPILED",
      "status": "ready",
      "config": "defaults",
      "deployed_at": "2026-05-05T14:31:00Z",
      "messages_processed": 12340,
      "avg_transform_ms": 1.1
    }
  ]
}

// GET /admin/schemas
{
  "schemas": [
    {"filename": "order.xsd", "size_bytes": 4820, "uploaded_at": "2026-05-05T14:29:00Z"},
    {"filename": "invoice.json", "size_bytes": 2340, "uploaded_at": "2026-05-05T14:29:05Z"}
  ]
}

// GET /admin/bundle (export)
// Returns: application/zip with the complete bundle
```

## Authentication

The management API is protected by an API key passed as an environment variable:

```yaml
UTLXE_ADMIN_KEY=my-secret-key-here
```

Requests must include the header:
```
X-Admin-Key: my-secret-key-here
```

If `UTLXE_ADMIN_KEY` is not set, the management API returns 403 on all endpoints (locked by default). This prevents accidental exposure.

The health endpoints (`/health`, `/metrics`) remain unauthenticated — they are read-only and needed by Kubernetes probes and Prometheus.

## On-Disk Layout

There is no "bundle file." The bundle is a **directory structure** — the on-disk state under `/utlxe/data/` IS the bundle. Every API call that modifies the bundle writes to this directory immediately.

```
/utlxe/data/                                    ← this IS "the bundle"
  schemas/                                       ← shared schema files
    order.xsd
    invoice.json
  transformations/                               ← one subdirectory per transformation
    invoice-to-ubl/
      invoice-to-ubl.utlx                       ← the transformation source
      transform.yaml                             ← config (absent = defaults apply)
    validate-order/
      validate-order.utlx
```

### What each API call writes to disk

| API call | Disk effect |
|----------|-------------|
| `POST /admin/transformations/invoice-to-ubl` (single .utlx) | Creates `transformations/invoice-to-ubl/invoice-to-ubl.utlx` |
| `POST /admin/transformations/invoice-to-ubl` (.utlx + config) | Creates `.utlx` + `transform.yaml` in that directory |
| `POST /admin/transformations/invoice-to-ubl/config` (config only) | Creates or replaces `transform.yaml` in existing directory |
| `POST /admin/schemas/order.xsd` | Creates `schemas/order.xsd` |
| `POST /admin/bundle` (ZIP upload) | **Replaces** entire `/utlxe/data/` content with ZIP contents |
| `GET /admin/bundle` (export) | Zips up `/utlxe/data/` and returns it — no disk change |
| `DELETE /admin/transformations/invoice-to-ubl` | Removes directory `transformations/invoice-to-ubl/` |
| `DELETE /admin/schemas/order.xsd` | Removes `schemas/order.xsd` |
| `DELETE /admin/bundle` | Empties `/utlxe/data/` — back to zero transformations |

The in-memory `TransformationRegistry` always mirrors the on-disk state. A write to disk and a registry update happen as a single operation — if compilation fails, neither the disk nor the registry change.

## Persistence: Surviving Container Restarts

Docker containers have an **ephemeral filesystem**. When a container restarts (crash, scale-to-zero, redeployment), everything written inside the container is lost. This means `/utlxe/data/` is wiped clean unless external storage is mounted.

### Three persistence tiers

| Tier | How it works | Restart behavior | When to use |
|------|-------------|------------------|-------------|
| **Volume-backed** | Azure Files mounted at `/utlxe/data/` | Transformations survive — loaded automatically on startup | **Recommended for Azure Marketplace** |
| **CI/CD re-deploy** | No volume mount. Pipeline uploads bundle after each start | Pipeline detects `ready: false`, uploads, waits for `ready: true` | Customers with mature CI/CD pipelines |
| **Ephemeral** | No volume, no pipeline. Manual upload | Lost on restart — must re-upload manually | Dev/test only |

### Volume-backed persistence (recommended)

When Azure Files is mounted at `/utlxe/data/`, the directory lives on a network file share, not inside the container:

```
Container filesystem (ephemeral):
  /utlxe/utlxe.jar                   ← from Docker image, rebuilt on restart

Mount point (persistent — Azure File Share):
  /utlxe/data/                        ← survives restarts
    schemas/
      order.xsd
    transformations/
      invoice-to-ubl/
        invoice-to-ubl.utlx
        transform.yaml
```

On restart, UTLXe scans `/utlxe/data/`, finds the transformations from the previous session, compiles them, and becomes ready. Zero manual intervention.

### CI/CD re-deploy persistence

The bundle lives in the CI/CD system (git repo, artifact store). The container is truly stateless — the pipeline is the source of truth:

```
Container starts → /utlxe/data/ is empty → health: ready=false
  ↓
CI/CD detects ready=false → POST /admin/bundle with bundle.zip
  ↓
UTLXe compiles → health: ready=true → Kubernetes routes traffic
```

## Startup Sequence

The startup sequence is the same regardless of persistence tier:

```
Container starts
  │
  ├── 1. Start Javalin HTTP server on :8081
  │     ├── Health endpoint:  GET /health  → {"status":"UP", "transformations":0, "ready":false}
  │     ├── Metrics endpoint: GET /metrics → (Prometheus counters, all zero)
  │     └── Admin API:        POST/GET/DELETE /admin/* → accepting requests
  │
  ├── 2. Scan /utlxe/data/
  │     ├── If transformations found (volume mount from previous session):
  │     │     ├── Compile all .utlx files
  │     │     ├── Register in TransformationRegistry
  │     │     └── Health: {"status":"UP", "transformations":3, "ready":true}
  │     │
  │     └── If empty (first start, or ephemeral, or CI/CD pattern):
  │           └── Health: {"status":"UP", "transformations":0, "ready":false}
  │              (waiting for API upload)
  │
  ├── 3. If --bundle flag provided:
  │     ├── Load from --bundle path (in addition to /utlxe/data/)
  │     └── --bundle transformations are read-only base; API uploads override
  │
  ├── 4. Start data plane on :8085
  │     └── Kubernetes readiness probe checks ready=true before routing traffic
  │
  └── 5. Running — both admin API and data plane active simultaneously
        ├── Admin API accepts uploads/updates/deletes at any time
        └── Data plane processes messages (only when ready=true)
```

### Readiness vs. liveness

| Probe | Endpoint | Checks | Used by |
|-------|----------|--------|---------|
| **Liveness** | `GET /health` | `status == "UP"` (process is alive) | Kubernetes — restart if dead |
| **Readiness** | `GET /health` | `ready == true` (transformations compiled) | Kubernetes — route traffic only when ready |

```json
// Container just started, no transformations yet
{"status": "UP", "transformations": 0, "ready": false}

// After bundle upload or volume-mount scan
{"status": "UP", "transformations": 3, "ready": true}

// After DELETE /admin/bundle (all removed)
{"status": "UP", "transformations": 0, "ready": false}
```

This means:
- A container that just started but hasn't received its bundle yet: **alive but not ready** (no traffic routed)
- A container with compiled transformations: **alive and ready** (traffic routed)
- A container where all transformations were deleted: **alive but not ready** (traffic stops until new upload)

## Azure Marketplace integration

### How volume mounts work (separation of concerns)

The volume mount is configured at the **infrastructure level** (Bicep template), not by the container. UTLXe has no mount API, executes no unix commands, and is completely unaware of whether `/utlxe/data/` is a local directory or a network mount. It uses standard Java file I/O (`java.nio.file.Files`) to read and write — nothing else.

```
┌─────────────────────────────────────────────────────┐
│ Azure Container Apps Platform                        │
│                                                      │
│  1. Creates Azure File Share (if enabled)            │
│  2. Mounts it at /utlxe/data BEFORE container starts │
│                                                      │
│  ┌───────────────────────────────────────────┐       │
│  │ UTLXe Container                           │       │
│  │                                           │       │
│  │  /utlxe/utlxe.jar    ← container layer   │       │
│  │  /utlxe/data/         ← just a directory  │       │
│  │    schemas/             (JVM reads/writes  │       │
│  │    transformations/      via java.nio —    │       │
│  │                          no mount commands,│       │
│  │                          no unix syscalls) │       │
│  └──────────────┬────────────────────────────┘       │
│                 │                                     │
│                 │ /utlxe/data/ transparently          │
│                 │ points to:                          │
│                 ▼                                     │
│  ┌───────────────────────────────────────────┐       │
│  │ Azure File Share (SMB)                    │       │
│  │ Storage Account: stutlxe{uniqueId}        │       │
│  │ Share name: utlxe-bundle                  │       │
│  └───────────────────────────────────────────┘       │
└─────────────────────────────────────────────────────┘
```

This is the same mechanism as Kubernetes `PersistentVolumeClaim` — the orchestrator mounts the volume, the container just sees a directory. No security implications for the JVM.

**What UTLXe does (application level):** reads and writes files in `/utlxe/data/` via `java.nio.file.Files`.

**What the Bicep template does (infrastructure level):** creates the storage account, file share, and volume mount configuration. The container never participates in the mount process.

### createUiDefinition.json

Add an optional "Persistent storage" toggle:

```
☐ Enable persistent transformation storage
  When enabled, uploaded transformations survive container restarts.
  Creates an Azure Files share mounted to the container.
```

### Bicep template changes

When persistent storage is enabled:

```bicep
// 1. Storage account
resource storageAccount 'Microsoft.Storage/storageAccounts@2023-01-01' = {
  name: 'stutlxe${uniqueString(resourceGroup().id)}'
  location: location
  sku: { name: 'Standard_LRS' }
  kind: 'StorageV2'
}

// 2. File share
resource fileShare 'Microsoft.Storage/storageAccounts/fileServices/shares@2023-01-01' = {
  name: '${storageAccount.name}/default/utlxe-bundle'
  properties: { shareQuota: 1 }    // 1 GB — bundles are small
}

// 3. Container App storage reference
resource containerAppStorage 'Microsoft.App/managedEnvironments/storages@2023-05-01' = {
  parent: managedEnvironment
  name: 'bundle-storage'
  properties: {
    azureFile: {
      accountName: storageAccount.name
      accountKey: storageAccount.listKeys().keys[0].value
      shareName: 'utlxe-bundle'
      accessMode: 'ReadWrite'
    }
  }
}

// 4. Container App with volume mount
resource containerApp 'Microsoft.App/containerApps@2023-05-01' = {
  properties: {
    template: {
      containers: [{
        name: 'utlxe'
        image: 'ghcr.io/utlx-lang/utlxe:latest'
        volumeMounts: [{
          volumeName: 'bundle-storage'
          mountPath: '/utlxe/data'          // ← this is all UTLXe sees
        }]
      }]
      volumes: [{
        name: 'bundle-storage'
        storageName: 'bundle-storage'
        storageType: 'AzureFile'
      }]
    }
  }
}
```

When persistent storage is disabled:
- No storage account created
- No volume mount configured
- `/utlxe/data/` is a regular container directory (ephemeral)
- Customer uses CI/CD re-deploy pattern or manual upload after each restart

In both cases, UTLXe code is identical — it reads and writes `/utlxe/data/`. The only difference is whether the platform mounted external storage there.

## Sequence diagrams

### 1. Batch workflow (ZIP bundle upload)

```mermaid
---
config:
  look: handDrawn
  theme: neutral
---
sequenceDiagram
    participant CICD as Customer<br/>CI/CD Pipeline
    participant Admin as UTLXe<br/>:8081 (admin)
    participant Engine as UTLXe<br/>Engine Core
    participant Data as UTLXe<br/>:8085 (data)
    participant App as Client<br/>Application

    Note over Admin: Container starts (empty)

    CICD->>Admin: POST /admin/bundle<br/>X-Admin-Key: ***<br/>[bundle.zip]
    Admin->>Admin: Extract ZIP
    Admin->>Admin: Store schemas to /utlxe/data/schemas/
    Admin->>Engine: Compile all .utlx files
    Engine-->>Admin: Compilation result
    Admin->>Admin: Write to /utlxe/data/transformations/
    Admin-->>CICD: 200 OK<br/>{"status":"deployed",<br/>"transformations": 3}

    Note over Data: Health: ready=true<br/>Kubernetes routes traffic

    App->>Data: POST /api/transform/invoice-to-ubl<br/>{input JSON}
    Data->>Engine: Execute transformation
    Engine-->>Data: {output UBL XML}
    Data-->>App: 200 OK
```

### 2. API-first workflow (incremental build)

```mermaid
---
config:
  look: handDrawn
  theme: neutral
---
sequenceDiagram
    participant Dev as Developer
    participant Admin as UTLXe<br/>:8081 (admin)
    participant Engine as UTLXe<br/>Engine Core

    Dev->>Admin: POST /admin/schemas/order.xsd<br/>[order.xsd]
    Admin->>Admin: Store to /utlxe/data/schemas/
    Admin-->>Dev: 200 OK

    Dev->>Admin: POST /admin/transformations/invoice-to-ubl<br/>[invoice-to-ubl.utlx only]
    Admin->>Engine: Compile .utlx (defaults applied)
    Engine-->>Admin: Compiled OK (48ms)
    Admin-->>Dev: 200 OK<br/>{"config":"defaults"}

    Note over Dev,Admin: Later: update just the schema
    Dev->>Admin: POST /admin/schemas/order.xsd<br/>[order-v2.xsd]
    Admin->>Admin: Replace schema on disk
    Admin-->>Dev: 200 OK
    Note over Engine: Next message uses new schema<br/>(no recompile needed)

    Note over Dev,Admin: Export for version control
    Dev->>Admin: GET /admin/bundle
    Admin-->>Dev: [bundle.zip]
```

### 3. Deploy → Test → Go live

The most important operational flow: upload a transformation, test it with sample input, then let real traffic through.

```mermaid
---
config:
  look: handDrawn
  theme: neutral
---
sequenceDiagram
    participant Ops as Ops Engineer
    participant Admin as UTLXe<br/>:8081 (admin)
    participant Engine as UTLXe<br/>Engine Core
    participant Data as UTLXe<br/>:8085 (data)
    participant App as Client<br/>Application

    Ops->>Admin: POST /admin/transformations/invoice-to-ubl<br/>[invoice-to-ubl.utlx]
    Admin->>Engine: Compile
    Engine-->>Admin: Compiled OK
    Admin-->>Ops: 200 OK {"status":"deployed"}

    Note over Ops,Admin: Test before real traffic
    Ops->>Admin: POST /admin/transformations/invoice-to-ubl/test<br/>{"orderId":"TEST-001", "amount":100}
    Admin->>Engine: Execute (not counted in metrics)
    Engine-->>Admin: {"Invoice":{"ID":"TEST-001",...}}
    Admin-->>Ops: 200 OK {"status":"ok", "duration_ms":3}

    Note over Data: Verified — real traffic can flow

    App->>Data: POST /api/transform/invoice-to-ubl<br/>{real order}
    Data->>Engine: Execute
    Engine-->>Data: {UBL XML}
    Data-->>App: 200 OK
```

### 4. Incident: pause, fix, resume

```mermaid
---
config:
  look: handDrawn
  theme: neutral
---
sequenceDiagram
    participant Ops as Ops Engineer
    participant Admin as UTLXe<br/>:8081 (admin)
    participant Engine as UTLXe<br/>Engine Core
    participant Data as UTLXe<br/>:8085 (data)
    participant App as Client<br/>Application

    Note over App,Data: Transformation is producing errors

    Ops->>Admin: GET /admin/transformations/invoice-to-ubl/errors
    Admin-->>Ops: [{"message":"Null ref: $input.customer.address",<br/>"line":14, ...}]

    Ops->>Admin: POST /admin/transformations/invoice-to-ubl/pause
    Admin->>Engine: Mark as paused
    Admin-->>Ops: 200 OK

    App->>Data: POST /api/transform/invoice-to-ubl
    Data-->>App: 503 Service Unavailable<br/>(Dapr/Service Bus: message not acked → retry later)

    Note over Ops,Admin: Fix and re-deploy
    Ops->>Admin: POST /admin/transformations/invoice-to-ubl<br/>[fixed .utlx]
    Admin->>Engine: Compile → hot-swap
    Admin-->>Ops: 200 OK

    Ops->>Admin: POST /admin/transformations/invoice-to-ubl/test<br/>{test input with customer.address}
    Admin-->>Ops: 200 OK {"status":"ok"}

    Ops->>Admin: POST /admin/transformations/invoice-to-ubl/resume
    Admin->>Engine: Mark as ready
    Admin-->>Ops: 200 OK

    App->>Data: POST /api/transform/invoice-to-ubl
    Data->>Engine: Execute (fixed version)
    Engine-->>Data: {correct output}
    Data-->>App: 200 OK
```

### 5. Volume-backed restart (persistence)

```mermaid
---
config:
  look: handDrawn
  theme: neutral
---
sequenceDiagram
    participant Azure as Azure<br/>Container Apps
    participant UTLXe as UTLXe Container
    participant FS as Azure File Share<br/>/utlxe/data/

    Note over Azure,FS: First deployment
    Azure->>UTLXe: Start container
    Azure->>FS: Mount file share at /utlxe/data/
    UTLXe->>FS: Scan /utlxe/data/ → empty
    Note over UTLXe: Health: ready=false
    Note over UTLXe: (customer uploads bundle via API)
    UTLXe->>FS: Write transformations + schemas
    Note over UTLXe: Health: ready=true

    Note over Azure: Container crashes or restarts

    Azure->>UTLXe: Start new container instance
    Azure->>FS: Mount same file share at /utlxe/data/
    UTLXe->>FS: Scan /utlxe/data/ → found 3 transformations
    UTLXe->>UTLXe: Compile all .utlx files
    Note over UTLXe: Health: ready=true<br/>(automatic, no re-upload needed)
```

### 6. Client discovery on data plane

```mermaid
---
config:
  look: handDrawn
  theme: neutral
---
sequenceDiagram
    participant App as Client<br/>Application
    participant Data as UTLXe<br/>:8085 (data)

    App->>Data: GET /api/transformations
    Data-->>App: {"transformations":[<br/>{"name":"invoice-to-ubl","status":"ready","input":"json","output":"xml"},<br/>{"name":"validate-order","status":"ready","input":"json","output":"json"},<br/>{"name":"enrich-order","status":"paused","input":"json","output":"json"}]}

    App->>Data: POST /api/transform/invoice-to-ubl<br/>{order JSON}
    Data-->>App: 200 OK {UBL XML}

    App->>Data: POST /api/transform/enrich-order<br/>{order JSON}
    Data-->>App: 503 Service Unavailable (paused)
```

## Implementation notes

### Where it lives

The management API is a new `AdminEndpoint` alongside `HealthEndpoint` in `modules/engine`. It reuses the existing Javalin HTTP server on port 8081.

### Files to modify

| File | Change |
|------|--------|
| New: `modules/engine/.../admin/AdminEndpoint.kt` | All `/admin/*` routes (bundle, transformations, schemas, test, operational) |
| New: `modules/engine/.../admin/ErrorRingBuffer.kt` | Per-transformation ring buffer of recent errors (last 100) |
| `modules/engine/.../registry/TransformationRegistry.kt` | Add `replaceAll()`, `remove()`, `pause()`, `resume()` for hot-swap and operational control |
| `modules/engine/.../bundle/BundleLoader.kt` | Add `loadFromZip(inputStream)` alongside existing `load(path)` |
| `modules/engine/.../config/EngineConfig.kt` | Add `adminKey`, `dataDir` config fields; runtime-safe update support |
| `modules/engine/.../UtlxEngine.kt` | Wire admin endpoint, startup scan of data dir, test execution path |
| `modules/engine/.../health/HealthEndpoint.kt` | Add `ready` field to health response |
| `modules/engine/.../transport/HttpTransport.kt` | Add `GET /api/transformations` discovery endpoint on data plane |
| `deploy/docker/Dockerfile.engine` | Add `VOLUME /utlxe/data` and `UTLXE_ADMIN_KEY` env |

### What already exists

- `BundleLoader` — loads transformations from a directory (reuse for ZIP extraction target)
- `TransformationRegistry` — holds compiled transformations (needs atomic replace)
- `HealthEndpoint` — Javalin HTTP server on 8081 (add `AdminEndpoint` alongside)
- Hot compilation — engine already compiles `.utlx` at startup (reuse for dynamic uploads)

## Effort estimate

| Task | Effort |
|------|--------|
| AdminEndpoint: transformation endpoints (upload, list, delete) | 2 days |
| AdminEndpoint: schema endpoints (upload, list, delete) | 1 day |
| AdminEndpoint: bundle endpoints (ZIP upload, export, validate) | 1 day |
| AdminEndpoint: test endpoint (execute with sample input) | 1 day |
| AdminEndpoint: operational endpoints (info, pause/resume, errors, config) | 1.5 days |
| Data plane: discovery endpoint (`GET /api/transformations`) | 0.5 day |
| ZIP bundle parsing and extraction | 0.5 day |
| Hot-swap in TransformationRegistry (atomic replace) | 1 day |
| Pause/resume state machine in TransformationRegistry | 0.5 day |
| Error ring buffer per transformation | 0.5 day |
| Admin key authentication middleware | 0.5 day |
| Startup scan of `/utlxe/data/` directory | 0.5 day |
| Readiness probe enhancement | 0.5 day |
| Bicep template: optional Azure Files mount | 0.5 day |
| Tests | 2 days |
| **Total** | **~13 days** |

## Customer workflows (end to end)

### Simplest possible start (one file, one command)

```bash
curl -X POST -H "X-Admin-Key: $KEY" \
  -F "source=@my-transform.utlx" \
  http://admin:8081/admin/transformations/my-transform
# Done. Transformation is live on :8085.
```

### Production CI/CD pipeline

```bash
# Build step assembles bundle.zip
# Deploy step uploads it
curl -X POST -H "X-Admin-Key: $KEY" \
  -F "file=@bundle.zip" \
  http://admin:8081/admin/bundle
# All transformations + schemas replaced atomically.
```

### Interactive development

```bash
# Upload schemas
curl -X POST -H "X-Admin-Key: $KEY" -F "file=@order.xsd" .../admin/schemas/order.xsd

# Deploy transformations one by one
curl -X POST -H "X-Admin-Key: $KEY" -F "source=@invoice.utlx" .../admin/transformations/invoice
curl -X POST -H "X-Admin-Key: $KEY" -F "source=@validate.utlx" .../admin/transformations/validate

# Update just the schema (no recompile, immediate effect)
curl -X POST -H "X-Admin-Key: $KEY" -F "file=@order-v2.xsd" .../admin/schemas/order.xsd

# Export what you built for version control
curl -H "X-Admin-Key: $KEY" .../admin/bundle -o my-bundle.zip
```

---

*Feature EF03. May 2026. Design document.*
*Key insight: the engine already supports dynamic loading internally — EF03 is the REST surface that exposes it to Azure Marketplace customers.*
