# EF05: Dapr Integration Fixes

**Status:** Must-have (required before go-live)  
**Priority:** Critical  
**Created:** May 2026  
**Source:** Validation against `docs/dapr/dapr-abstract.md` (Dapr 1.17 reference)

---

## Summary

Validation of UTLXe's Dapr integration against the Dapr 1.17 specification revealed several gaps. These must be fixed before the Azure Marketplace go-live. The issues range from a missing startup handshake (input bindings won't activate) to incorrect route paths.

## Issues

### 1. OPTIONS probe at startup (CRITICAL)

**What Dapr does:** Before activating an input binding, Dapr sends an `OPTIONS` request to `http://app:appPort/{component-name}`. If the app returns 404, Dapr does NOT activate that binding — no messages are delivered. Ever.

**Our current state:** UTLXe does not handle `OPTIONS` requests. Input bindings may not activate depending on how Dapr handles unrecognized routes.

**Fix:** Add an OPTIONS handler that always responds 200 for any binding name:

```kotlin
// In HttpTransport.kt
options("/{bindingName}") {
    // Dapr startup probe — always 200, regardless of whether transformation exists.
    // Reason: the Admin API upload may happen AFTER Dapr's probe.
    // If we return 404 here, the binding never activates and the operator
    // can't fix it without restarting the container.
    // Messages arriving before the transformation is uploaded get 500 (retry).
    call.respond(HttpStatusCode.OK)
}
```

**Why always 200?** The Admin API (EF03) allows uploading transformations at any time — before or after container start. The Dapr OPTIONS probe happens once at startup. If UTLXe returns 404 for a not-yet-uploaded transformation, the binding is permanently deactivated. By responding 200 always, Dapr activates the binding immediately. Messages arriving before the transformation exists trigger Service Bus retry (500 → abandon → redeliver). Once the transformation is uploaded, the next delivery succeeds.

### 2. Route path mismatch (CRITICAL)

**What Dapr does:** By default, Dapr delivers input binding messages to `POST /{component-name}` — the component name at the root path.

**Our current state:** UTLXe registers handlers at `/api/dapr/input/{bindingName}`, not at `/{component-name}`.

**Options:**

| Option | Change | Backward compatible |
|--------|--------|:---:|
| A. Register at both paths | Add `post("/{bindingName}")` alongside existing `/api/dapr/input/{bindingName}` | Yes |
| B. Use Dapr `route` metadata | Dapr component YAML sets `route: /api/dapr/input/{name}` | Yes, but requires customer to know |
| C. Switch to root path only | Remove `/api/dapr/input/` prefix, register at `/{bindingName}` | Breaking |

**Recommendation: Option A** — register at both paths. The root path `/{bindingName}` handles the Dapr default behavior and the OPTIONS probe. The `/api/dapr/input/{bindingName}` path stays for backward compatibility and explicit clarity.

```kotlin
// In HttpTransport.kt — handle both paths
val daprInputHandler: suspend PipelineContext.() -> Unit = {
    val bindingName = call.parameters["bindingName"] ?: "default"
    // ... existing input binding logic
}

post("/{bindingName}", daprInputHandler)                    // Dapr default path
post("/api/dapr/input/{bindingName}", daprInputHandler)     // Explicit path (backward compat)

options("/{bindingName}") {
    call.respond(HttpStatusCode.OK)  // Dapr startup probe
}
```

**Route conflict risk:** The root path `/{bindingName}` could conflict with other routes like `/health` or `/metrics`. These are on port 8081, not 8085, so there's no conflict. On port 8085, the existing routes are `/api/transform/{name}`, `/api/transformations`, `/api/health` — all start with `/api/`, so no conflict with `/{bindingName}`.

### 3. Service Bus metadata header format (MEDIUM)

**What Dapr sends:** Service Bus message properties arrive as `metadata.{property-name}` headers:
- `metadata.MessageId`
- `metadata.CorrelationId`
- `metadata.SessionId`
- `metadata.{custom-property-name}`

**Our current state (EF04):** EF04 reads `MessageId` and `CorrelationId` directly as header names, without the `metadata.` prefix.

**Fix:** Read both formats — with and without prefix:

```kotlin
val incomingMessageId = call.request.header("metadata.MessageId")
    ?: call.request.header("MessageId")
val incomingCorrelationId = call.request.header("metadata.CorrelationId")
    ?: call.request.header("CorrelationId")
```

### 4. Sidecar health check at startup (MEDIUM)

**What Dapr provides:** The sidecar exposes `/v1.0/healthz` on port 3500. The sidecar reaches readiness only after components are loaded and the app port is responsive.

**Our current state:** UTLXe does not check if the Dapr sidecar is ready before declaring itself ready.

**Fix:** During startup, before setting `ready: true`, optionally check the sidecar health:

```kotlin
// In UtlxEngine startup sequence
if (daprEnabled) {
    val sidecarReady = waitForSidecar("http://localhost:3500/v1.0/healthz", timeoutMs = 30000)
    if (!sidecarReady) {
        logger.warn("Dapr sidecar not ready after 30s — proceeding without sidecar")
    }
}
```

This is a best-effort check — UTLXe should not fail to start if the sidecar is slow. But it prevents a window where UTLXe reports `ready: true` while the sidecar is still initializing.

### 6. Dapr binding validation endpoint (MEDIUM)

**Problem:** When the customer has N queues, they need N Dapr components AND N transformations. The naming must match (component route = transformation name). A typo or missing upload means messages arrive but no transformation handles them — silent 500 errors until someone checks the error log.

**Fix:** UTLXe tracks which OPTIONS probes Dapr sent at startup and exposes a validation endpoint that cross-checks against loaded transformations:

```
GET /admin/dapr/bindings

{
  "bindings": [
    {"binding": "orders-in", "transformation": "orders-in", "status": "matched"},
    {"binding": "invoices-in", "transformation": "invoices-in", "status": "matched"},
    {"binding": "returns-in", "transformation": null, "status": "MISSING"}
  ],
  "matched": 2,
  "missing": 1
}
```

Implementation:
- UTLXe records every OPTIONS probe path in a `Set<String>` (the Dapr binding names)
- The validation endpoint compares this set against the `TransformationRegistry`
- `matched` = binding name exists in the registry
- `MISSING` = Dapr probed for it but no transformation is uploaded

This catches mismatches immediately — the operator can see which transformations need to be uploaded. The health endpoint should also reflect this:

```json
{
  "status": "UP",
  "transformations": 2,
  "dapr_bindings_probed": 3,
  "dapr_bindings_unmatched": 1,
  "ready": true
}
```

A warning log is emitted when a POST arrives for a binding that has no matching transformation:

```
WARN  Dapr binding "returns-in" received message but no transformation is loaded.
      Upload via: POST /admin/transformations/returns-in
```

### 7. Dapr gRPC as future option (LOW — document only)

**What Dapr supports:** App-to-sidecar communication can use gRPC on port 50001 instead of HTTP on port 3500. For input bindings, Dapr calls the app using its own `AppCallback.OnBindingEvent` RPC (Dapr's proto, not ours).

**Our current state:** HTTP only.

**Decision:** Document as future option. Do not implement for go-live. The reasons:

1. Dapr gRPC requires implementing Dapr's `AppCallback` proto — a separate interface from our `utlxe.proto`
2. The performance difference between HTTP and gRPC on localhost is negligible (sub-millisecond)
3. Adding Dapr gRPC adds a third interface to maintain (HTTP + UTLXe gRPC + Dapr gRPC)
4. HTTP is simpler to debug (curl, browser, logs show readable requests)

If a customer needs Dapr gRPC for throughput, it's a post-launch enhancement — set `app-protocol: grpc` in the Container App annotation and implement `AppCallback`.

## Architecture Decision: Why HTTP Cannot Be Eliminated

Even with gRPC for Dapr and SDKs, HTTP is required for:

| Concern | Why HTTP |
|---------|---------|
| Kubernetes health probes | `GET /health` — standard, supported by all platforms |
| Prometheus metrics | `GET /metrics` — HTTP scrape is the Prometheus standard |
| Admin API (EF03) | Operators use `curl`, CI/CD uses shell scripts |
| Bundle upload | `curl -F "file=@bundle.zip"` — one line vs gRPC streaming |
| Dapr HTTP mode | Default, simplest, works on all platforms |
| Direct HTTP clients | API gateways, webhooks, testing |

UTLXe has two interface categories:

1. **UTLXe proto** (`utlxe.proto`) — one proto, two transports (gRPC and stdio-proto). For SDKs and language wrappers.
2. **HTTP** — for admin, health, metrics, Dapr, and direct clients. Cannot be replaced.

Dapr gRPC (if added later) would be a third interface using Dapr's own proto — it does not replace either of the above.

## Files to modify

| File | Change |
|------|--------|
| `modules/engine/.../transport/HttpTransport.kt` | Add `OPTIONS /{bindingName}` handler (always 200), add `POST /{bindingName}` route, fix metadata header prefix, track probed binding names |
| `modules/engine/.../UtlxEngine.kt` | Add optional sidecar health check at startup |
| New: `modules/engine/.../admin/DaprBindingTracker.kt` | `Set<String>` of probed binding names, validation against TransformationRegistry |
| `modules/engine/.../health/HealthEndpoint.kt` | Add `dapr_bindings_probed` and `dapr_bindings_unmatched` to health response |
| `docs/features/EF04-message-correlation-and-tracing.md` | Fix metadata header names to include `metadata.` prefix format |
| `docs/architecture/utlxe-dapr-messaging-patterns.md` | Add startup handshake (OPTIONS probe), document route paths, multi-binding |
| `docs/architecture/utlxe-engine-architecture.md` | Add Dapr gRPC as future option in transport section |
| `book-azure/chapters/ch00-why-utlxe.typ` | Startup diagram with Admin API, multi-binding explanation |
| `book-azure/chapters/ch04-azure-services.typ` | Multi-queue walkthrough, binding validation endpoint |

## Effort estimate

| Task | Effort |
|------|--------|
| OPTIONS handler + root path registration (always 200) | 0.5 day |
| Dapr binding tracker (record probed names) | 0.5 day |
| Validation endpoint (`GET /admin/dapr/bindings`) | 0.5 day |
| Health endpoint: binding match counts | 0.5 day |
| Metadata header prefix handling (with/without `metadata.`) | 0.5 day |
| Sidecar health check at startup | 0.5 day |
| Tests | 0.5 day |
| Documentation updates (EF04, architecture docs, book) | 1 day |
| **Total** | **~4.5 days** |

## Updated implementation order

```
Week 1-2:   EF04 (tracing, 7.5 days) + EF05 (Dapr fixes, 3 days) — together
            Proto changes for EF01 already included
Week 2-3:   EF01 (pipeline multi-input, 4-5 days)
Week 3-7:   EF03 (Admin API, 19 days)
Week 5-6:   EF02 (validation, parallel with EF03)
Week 7:     Go-live readiness
```

EF05 should be done alongside EF04 — both touch `HttpTransport.kt` and the Dapr message handling code.

---

*Feature EF05. May 2026. Must-have before go-live.*
*Source: validation against docs/dapr/dapr-abstract.md (Dapr 1.17 specification).*
