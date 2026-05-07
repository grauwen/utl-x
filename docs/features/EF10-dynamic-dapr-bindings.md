# EF10: Dynamic Dapr Bindings and Subscriptions

**Status:** Design  
**Priority:** High (eliminates the static/dynamic mismatch in dev/test)  
**Created:** May 2026  
**Depends on:** EF03 (Admin API), EF06 (Dapr strategy), EF09 (open vs locked mode)

---

## Summary

UTLXe's core value proposition is dynamic: upload a transformation at runtime, start processing messages immediately. But Dapr's binding model is static: components are defined in YAML at deploy time. This mismatch means uploading a new transformation via the Admin API doesn't create the corresponding Dapr input binding — the operator must manually create a component YAML and restart.

EF10 eliminates this gap using two Dapr capabilities:

1. **Component Hot Reload** (preview since Dapr v1.13, improved v1.17): Dapr watches the `--resources-path` directory. Write a YAML file → Dapr initializes the binding within ~1 second. Delete the file → Dapr tears it down.

2. **Streaming Subscriptions** (alpha since Dapr v1.14, gRPC): For pub/sub, the app opens/closes bidirectional gRPC streams to dynamically subscribe/unsubscribe from topics — no YAML per topic needed.

## The Problem Today

```
Developer uploads transformation "orders-in" via Admin API
  → UTLXe compiles and registers it ✓
  → UTLXe listens on POST /orders-in ✓
  → But Dapr has no binding component for "orders-in" ✗
  → No messages flow until operator creates YAML + restarts ✗
```

## The Solution

### For Input/Output Bindings (Hot Reload)

When the Admin API creates or deletes a transformation, UTLXe writes or removes Dapr component YAML files in the Dapr resources directory.

**Flow — interactive configuration (stage → sync):**

The Admin API uses a stage-then-sync model (see EF03). Config changes are saved to disk immediately but Dapr components are only created/updated on explicit sync. This prevents churn when building up a configuration over multiple API calls.

```
# Stage 1: Upload transformation
POST /admin/transformations/orders-in  (.utlx source)
  → compiled, registered, sync_status: no_dapr

# Stage 2: Set messaging (saved to transform.yaml, Dapr NOT touched)
POST /admin/transformations/orders-in/messaging
  {"input": {"queue": "orders-in"}, "output": {"queue": "orders-out"}}
  → sync_status: draft

# Stage 3: Sync — push to Dapr
POST /admin/transformations/orders-in/sync
  ↓
UTLXe generates /dapr/components/binding-orders-in.yaml
  (type: bindings.azure.servicebusqueues, namespaceName from --dapr-servicebus-namespace)
  ↓
UTLXe generates /dapr/components/binding-orders-out.yaml (output queue)
  ↓
Dapr detects new files (filesystem watch, <1 second)
  ↓
Dapr initializes bindings → starts listening on queue "orders-in"
  ↓
Dapr probes OPTIONS /orders-in → UTLXe returns 200
  ↓
sync_status: synced — messages flow
```

**Flow — topic example (stage → sync):**

```
POST /admin/transformations/invoice-normalize  (.utlx source)
POST /admin/transformations/invoice-normalize/messaging
  {"input": {"topic": "raw-invoices", "subscription": "utlxe"}, "output": {"topic": "normalized-invoices"}}
  → sync_status: draft

POST /admin/transformations/invoice-normalize/sync
  ↓
UTLXe ensures /dapr/components/pubsub-utlxe-servicebus.yaml exists (shared, one per namespace)
  ↓
UTLXe opens streaming subscription via gRPC: Subscribe(pubsub="utlxe-servicebus", topic="raw-invoices")
  ↓
Dapr subscribes to Service Bus topic → sync_status: synced, messages flow
```

**Flow — bulk configuration (multiple transformations, one sync):**

```
POST /admin/transformations/orders-in/messaging      → draft
POST /admin/transformations/invoices-in/messaging     → draft
POST /admin/transformations/returns-in/messaging      → draft

# One sync for everything
POST /admin/sync
  → Generates all Dapr components at once
  → All transformations: synced
```

**Flow — bundle upload (auto-sync):**

```
POST /admin/bundle  (ZIP with .utlx files + transform.yaml with messaging config)
  → Compiles all transformations
  → AUTO-SYNCS all messaging to Dapr (bundle is a coherent deployment unit)
  → All transformations: synced
  → No separate sync call needed
```

**Flow — remove transformation (auto-sync):**

```
DELETE /admin/transformations/orders-in
  ↓
UTLXe unregisters transformation
  ↓
If queue-based: UTLXe deletes /dapr/components/binding-orders-in.yaml
If topic-based: UTLXe closes the streaming subscription
  ↓
Dapr detects change (<1 second) → tears down binding / unsubscribes
  ↓
Clean removal — no separate sync needed (deletion is always immediate)
```

### For Pub/Sub Topics (Streaming Subscriptions)

When using Dapr pub/sub instead of bindings, a single pub/sub component provides access to the entire Service Bus namespace. Individual topic subscriptions are managed in code via streaming subscriptions (gRPC).

**Flow — add transformation with topic:**
```
POST /admin/transformations/orders-in  (upload .utlx with topic config)
  ↓
UTLXe compiles, registers transformation
  ↓
UTLXe opens streaming subscription via gRPC to Dapr sidecar:
  Subscribe(pubsub="utlxe-servicebus", topic="incoming-orders")
  ↓
Dapr subscribes to Service Bus topic "incoming-orders"
  ↓
Messages flow immediately — no YAML, no restart
```

**Flow — remove transformation:**
```
DELETE /admin/transformations/orders-in
  ↓
UTLXe closes the streaming subscription (dispose gRPC stream)
  ↓
Dapr unsubscribes from topic
  ↓
Clean removal
```

## Required Dapr Configuration

### Hot Reload feature gate

```yaml
apiVersion: dapr.io/v1alpha1
kind: Configuration
metadata:
  name: utlxe-config
spec:
  features:
    - name: HotReload
      enabled: true
```

### Dapr startup with watched resources directory

```bash
# Self-hosted
daprd --app-id utlxe \
      --app-port 8085 \
      --resources-path /dapr/components \
      --config /dapr/config.yaml

# The /dapr/components directory is watched for changes at runtime
```

### Kubernetes

With HotReload enabled, the Dapr Operator watches Component CRDs. UTLXe could create CRDs via the Kubernetes API — but for simplicity, the file-based approach (shared volume) is preferred.

## Pre-sync testing: the HTTP sandbox

A key advantage of the stage-then-sync model: **before sync, transformations are fully testable via HTTP without Dapr**.

A transformation in `draft` state is compiled and registered in the engine. The HTTP data plane (`POST :8085/api/transform/{name}`) and the test endpoint (`POST /admin/transformations/{name}/test`) work normally — only the Dapr messaging connection is absent.

This means:
- **No Dapr needed for development** — upload, test via HTTP, iterate until correct
- **No queue impact during testing** — bad transformations don't consume or dead-letter real messages
- **Validation is testable** — schema validation, policy enforcement, multi-input all work before go-live
- **Sync is the "go live" switch** — only when confident, push to Dapr and start processing real messages

Workflow: upload → test → fix → test → sync → messages flow.

## Mode-specific behavior

| Mode | Binding management | Rationale |
|---|---|---|
| **Open** (dev/test) | **Dynamic** — Admin API writes/deletes YAML | Full dynamic experience, no restarts |
| **Locked** (production) | **Static** — YAML from CI/CD, no writes | Immutable deployment, no runtime YAML changes |

In locked mode, the binding YAML files are part of the CI/CD deployment (alongside the .utlar bundle). UTLXe does NOT write to the Dapr resources directory — the Admin API is read-only for transformations.

In open mode, UTLXe manages the Dapr resources directory as part of its lifecycle. The `--dapr-components-dir` flag tells UTLXe where to write component YAML.

## Configuration

### Engine config for dynamic bindings

```bash
utlxe --mode http \
      --data-dir /utlxe/data \
      --dapr-components-dir /dapr/components \
      --dapr-servicebus-namespace "mycompany.servicebus.windows.net" \
      --dapr-eventhub-namespace "mycompany-eventhubs" \
      --dapr-storage-account "mycompanystorage"
```

| Flag | Purpose |
|---|---|
| `--dapr-components-dir` | Directory where UTLXe writes Dapr component YAML (watched by Dapr) |
| `--dapr-servicebus-namespace` | Service Bus namespace FQDN (for queue and topic components) |
| `--dapr-eventhub-namespace` | Event Hub namespace name (for eventhub components) |
| `--dapr-storage-account` | Storage account for Event Hub pub/sub checkpointing |

If `--dapr-components-dir` is not set, dynamic binding management is disabled (backward compatible).

Auth is handled by Azure Managed Identity (recommended) — no connection strings or secrets in flags. The container's managed identity must have the appropriate RBAC roles:
- **Service Bus**: `Azure Service Bus Data Sender` + `Azure Service Bus Data Receiver`
- **Event Hub**: `Azure Event Hubs Data Sender` + `Azure Event Hubs Data Receiver`
- **Storage** (Event Hub checkpoints only): `Storage Blob Data Contributor`

### Transform config declares messaging intent

The field name is the discriminator — `queue`, `topic`, or `eventhub`:

```yaml
# Queue in, queue out (Service Bus binding)
input:
  queue: orders-in
output:
  queue: orders-out

# Topic in, topic out (Service Bus pub/sub)
input:
  topic: raw-invoices
  subscription: utlxe-transform       # required for topic input
output:
  topic: normalized-invoices

# Event Hub in, queue out (mixed)
input:
  eventhub: iot-telemetry
  consumerGroup: utlxe                 # optional, triggers pub/sub mode
output:
  queue: alerts-out

# Mix-and-match: input and output can use different services
input:
  queue: orders-in                     # Service Bus queue (binding)
output:
  topic: processed-orders              # Service Bus topic (pub/sub)
```

| Field | Dapr component type | Building block |
|---|---|---|
| `queue` | `bindings.azure.servicebusqueues` | Binding |
| `topic` | `pubsub.azure.servicebus.topics` | Pub/Sub |
| `eventhub` | `bindings.azure.eventhubs` (default) or `pubsub.azure.eventhubs` (if `consumerGroup` set) | Binding or Pub/Sub |

The Admin API reads these fields from the transformation config and generates the appropriate Dapr component YAML or streaming subscription. Auth comes from the environment (managed identity or secret store), never from the bundle.

## Generated YAML templates

UTLXe generates Dapr component YAML based on the `queue`, `topic`, or `eventhub` field in transform.yaml. All generated components are marked with `utlxe.io/managed: "true"` for reconciliation.

### Queue component (Service Bus binding)

```yaml
# Auto-generated by UTLXe — do not edit manually
# Transformation: orders-in → input queue
# Generated: 2026-05-07T10:00:00Z
apiVersion: dapr.io/v1alpha1
kind: Component
metadata:
  name: orders-in
  annotations:
    utlxe.io/managed: "true"
    utlxe.io/transformation: "orders-in"
    utlxe.io/role: "input"
spec:
  type: bindings.azure.servicebusqueues
  version: v1
  metadata:
    - name: namespaceName                  # Managed identity (secretless)
      value: "mycompany.servicebus.windows.net"
    - name: queueName
      value: "orders-in"
    - name: direction
      value: "input, output"
    - name: maxConcurrentHandlers
      value: "1"
```

### Topic component (Service Bus pub/sub)

One pub/sub component per Service Bus namespace — shared across all topic-based transformations. UTLXe generates this once, not per transformation:

```yaml
# Auto-generated by UTLXe — do not edit manually
# Shared Service Bus pub/sub component
# Generated: 2026-05-07T10:00:00Z
apiVersion: dapr.io/v1alpha1
kind: Component
metadata:
  name: utlxe-servicebus
  annotations:
    utlxe.io/managed: "true"
    utlxe.io/role: "pubsub"
spec:
  type: pubsub.azure.servicebus.topics
  version: v1
  metadata:
    - name: namespaceName
      value: "mycompany.servicebus.windows.net"
```

Topic subscriptions are then managed via:
- **Open mode**: Dapr streaming subscriptions (gRPC, per-topic, dynamic)
- **Locked mode**: `GET /dapr/subscribe` response (derived from manifest at startup)

### Event Hub component (binding or pub/sub)

```yaml
# Auto-generated by UTLXe — do not edit manually
# Transformation: telemetry-in → input eventhub (binding mode)
apiVersion: dapr.io/v1alpha1
kind: Component
metadata:
  name: telemetry-in
  annotations:
    utlxe.io/managed: "true"
    utlxe.io/transformation: "telemetry-in"
    utlxe.io/role: "input"
spec:
  type: bindings.azure.eventhubs
  version: v1
  metadata:
    - name: eventHubNamespace              # Note: NOT FQDN for Event Hub
      value: "mycompany-eventhubs"
    - name: eventHub
      value: "iot-telemetry"
```

If the transform.yaml specifies `consumerGroup`, UTLXe generates a pub/sub component instead (requires checkpoint storage):

```yaml
# With consumerGroup → pub/sub mode
spec:
  type: pubsub.azure.eventhubs
  metadata:
    - name: eventHubNamespace
      value: "mycompany-eventhubs"
    - name: eventHub
      value: "iot-telemetry"
    - name: consumerGroup
      value: "utlxe"
    - name: storageAccountName             # Required for pub/sub checkpointing
      value: "mycompanystorage"
    - name: storageContainerName
      value: "eventhub-checkpoints"
```

### Auth resolution

The generated YAML never contains secrets. Auth fields come from:
1. **Managed identity** (recommended): just `namespaceName` / `eventHubNamespace` — Dapr uses the container's identity
2. **Secret store reference**: `secretKeyRef` pointing to a Dapr secret store component
3. **Engine config**: `--dapr-servicebus-namespace`, `--dapr-eventhub-namespace` CLI flags provide the namespace values

The `utlxe.io/managed: "true"` annotation marks auto-generated components. On startup, UTLXe can clean up orphaned managed components (transformation deleted while UTLXe was down).

## Startup reconciliation (auto-sync)

On startup in open mode, UTLXe reconciles the Dapr components directory with loaded transformations. This is an **automatic sync** — transformations that were `synced` before shutdown should be `synced` again after restart without manual intervention.

```
Startup:
  1. Scan data dir → load transformations (including transform.yaml with messaging config)
  2. Scan Dapr components dir → find utlxe.io/managed components
  3. For each loaded transformation WITH messaging config:
     - If no matching component → generate and write YAML (auto-sync)
     - If matching component exists → verify it matches config (update if drifted)
  4. For each managed component:
     - If no matching transformation → delete orphaned YAML
  5. Dapr hot reload picks up all changes
  6. All transformations with messaging: sync_status = synced
     Transformations without messaging: sync_status = no_dapr
```

This means: if you configured and synced a transformation, then the container restarts, everything comes back automatically. The sync state is derived from comparing config-on-disk with components-in-Dapr, not stored separately.

## Dapr capability matrix

| Capability | Status (Dapr v1.17) | Works for bindings? | Works for pub/sub? | UTLXe use |
|---|---|---|---|---|
| Component Hot Reload | Preview (feature gate) | Yes | Yes | Primary for bindings |
| Streaming Subscriptions | Alpha (gRPC) | No | Yes | Future for pub/sub |
| `/dapr/subscribe` | Stable | No | Yes (read-once) | Current, requires restart |
| Component HTTP API | Not implemented | — | — | Not available |

## Risk assessment

| Risk | Mitigation |
|---|---|
| HotReload is preview — API could change | Monitor Dapr releases. Feature is widely used and approaching stable. |
| Filesystem watch misses events | Startup reconciliation catches any drift |
| Race condition: YAML written but Dapr hasn't loaded yet | OPTIONS probe retry: UTLXe waits for Dapr to probe before confirming to user |
| Connection string in YAML | Use Dapr secret store references, not plain text |
| Locked mode accidentally writes YAML | Mode check: if locked, skip all YAML generation |

## Files to implement

| File | Change |
|------|--------|
| New: `modules/engine/.../dapr/DaprComponentManager.kt` | YAML generation, file write/delete, reconciliation |
| `modules/engine/.../admin/AdminEndpoint.kt` | After upload/delete: call DaprComponentManager |
| `modules/engine/.../Main.kt` | `--dapr-components-dir` and related CLI flags |
| `modules/engine/.../UtlxEngine.kt` | Hold reference to DaprComponentManager, call reconcile on startup |
| `modules/engine/.../config/TransformConfig.kt` | Ensure inputBinding/outputBinding/pubsub/topic fields are parsed |

## Effort estimate

| Task | Effort |
|------|--------|
| DaprComponentManager (YAML template, write, delete) | 1 day |
| Startup reconciliation (scan + diff + cleanup) | 0.5 day |
| Admin API integration (post-upload/delete hooks) | 0.5 day |
| CLI flags and configuration | 0.5 day |
| Tests (mock filesystem, component generation) | 1 day |
| Documentation + Bicep template update | 0.5 day |
| **Total** | **~4 days** |

## Relationship to other features

- **EF03** (Admin API): upload/delete triggers YAML generation in open mode
- **EF06** (Dapr strategy): EF10 resolves the static/dynamic mismatch identified in EF06
- **EF07** (parallel transports): dynamic bindings work alongside gRPC/stdio
- **EF09** (locked mode): locked mode disables YAML generation — bindings come from CI/CD

---

*Feature EF10. May 2026. Design document.*
*Key insight: The transform.yaml field name IS the discriminator — `queue`, `topic`, or `eventhub`. UTLXe reads the field, generates the correct Dapr component YAML (or streaming subscription), and Dapr Hot Reload picks it up in ~1 second. Upload a transformation → messaging flows. No restart, no manual YAML, no secrets in the bundle. Input and output can mix freely (queue in → topic out, eventhub in → queue out). Production stays immutable via locked mode.*
