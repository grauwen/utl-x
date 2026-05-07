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

**Flow — add transformation:**
```
POST /admin/transformations/orders-in  (upload .utlx)
  ↓
UTLXe compiles, registers transformation
  ↓
UTLXe writes /dapr/components/binding-orders-in.yaml:
  apiVersion: dapr.io/v1alpha1
  kind: Component
  metadata:
    name: orders-in
  spec:
    type: bindings.azure.servicebusqueues
    version: v1
    metadata:
      - name: connectionString
        value: <from engine config or secret store>
      - name: queueName
        value: orders-in
      - name: direction
        value: "input, output"
  ↓
Dapr detects new file (filesystem watch, <1 second)
  ↓
Dapr initializes binding → starts listening on queue "orders-in"
  ↓
Dapr probes OPTIONS /orders-in → UTLXe returns 200
  ↓
Messages flow immediately
```

**Flow — remove transformation:**
```
DELETE /admin/transformations/orders-in
  ↓
UTLXe unregisters transformation
  ↓
UTLXe deletes /dapr/components/binding-orders-in.yaml
  ↓
Dapr detects file deletion (<1 second)
  ↓
Dapr tears down binding → stops listening
  ↓
Clean removal, no restart needed
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
      --dapr-default-binding-type bindings.azure.servicebusqueues \
      --dapr-connection-string-secret "servicebus-connection"
```

| Flag | Purpose |
|---|---|
| `--dapr-components-dir` | Directory where UTLXe writes Dapr component YAML (watched by Dapr) |
| `--dapr-default-binding-type` | Component type for auto-generated bindings (default: `bindings.azure.servicebusqueues`) |
| `--dapr-connection-string-secret` | Secret store reference for the connection string |

If `--dapr-components-dir` is not set, dynamic binding management is disabled (backward compatible).

### Transform config declares binding intent

```yaml
# transform.yaml — binding mode
inputBinding: "orders-in"          # queue/topic name = binding name
outputBinding: "orders-out"

# transform.yaml — pub/sub mode
input:
  pubsub: "utlxe-servicebus"
  topic: "incoming-orders"
output:
  pubsub: "utlxe-servicebus"
  topic: "processed-orders"
```

The Admin API reads the binding/topic declaration from the transformation config and generates the appropriate Dapr component YAML or streaming subscription.

## Generated YAML template

UTLXe generates binding YAML from a template:

```yaml
# Auto-generated by UTLXe — do not edit manually
# Transformation: orders-in
# Generated: 2026-05-07T10:00:00Z
apiVersion: dapr.io/v1alpha1
kind: Component
metadata:
  name: orders-in
  annotations:
    utlxe.io/managed: "true"
    utlxe.io/transformation: "orders-in"
spec:
  type: bindings.azure.servicebusqueues
  version: v1
  metadata:
    - name: connectionString
      secretKeyRef:
        name: servicebus-connection
        key: connectionString
    - name: queueName
      value: "orders-in"
    - name: direction
      value: "input, output"
    - name: maxConcurrentHandlers
      value: "1"
```

The `utlxe.io/managed: "true"` annotation marks auto-generated components. On startup, UTLXe can clean up orphaned managed components (transformation deleted while UTLXe was down).

## Startup reconciliation

On startup in open mode, UTLXe reconciles the Dapr components directory with loaded transformations:

```
Startup:
  1. Scan data dir → load transformations
  2. Scan Dapr components dir → find utlxe.io/managed components
  3. For each loaded transformation:
     - If no matching component → generate and write YAML
  4. For each managed component:
     - If no matching transformation → delete orphaned YAML
  5. Dapr hot reload picks up all changes
```

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
*Key insight: Dapr Component Hot Reload (preview, `--resources-path` filesystem watch) lets UTLXe write binding YAML at runtime. Upload a transformation → binding appears in ~1 second → messages flow. No restart. The static/dynamic mismatch is eliminated for dev/test. Production stays immutable via locked mode.*
