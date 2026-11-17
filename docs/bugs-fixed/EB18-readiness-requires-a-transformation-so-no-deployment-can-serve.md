# EB18 — readiness requires a loaded transformation, so no deployment can ever serve traffic

**Status:** Fixed — 2026-11-17
**Priority:** Critical — every Azure deployment of UTLXe is unreachable from the moment it starts
**Component:** `:modules:engine` — `health/HealthEndpoint.kt` (`/health/ready` semantics), with the
Marketplace templates `deploy/azure-marketplace/{starter,professional}/mainTemplate.json`
(readiness probe target)
**Found:** 2026-11-17, during the first real deployment of the Azure Marketplace offering

## Summary

The engine reports itself **not ready** until at least one transformation is loaded:

```kotlin
// health/HealthEndpoint.kt
get("/health/ready") {
    val txCount = engine.registry.list().size
    val isReady = engine.state == EngineState.RUNNING && txCount > 0
    ...  // else HttpStatusCode.ServiceUnavailable
}
```

The Marketplace template used that endpoint as the Container Apps **readiness probe**:

```json
{ "type": "Readiness", "httpGet": { "port": 8081, "path": "/health/ready" },
  "periodSeconds": 5, "failureThreshold": 3 }
```

And the container ships empty — "no transformations are loaded" is the documented, intended
start state.

So: zero transformations → `/health/ready` answers 503 → the readiness probe never passes →
Azure Container Apps never adds the replica to the ingress backend → **every request to the
public FQDN times out**, including static pages served by the UI sidecar, which is healthy and
simply never receives a request.

**It is a deadlock.** The engine cannot become ready until a transformation is uploaded, and a
transformation cannot be uploaded because the Admin API that accepts uploads is behind the same
ingress. No deployment of this offering could ever serve traffic, to anyone, at any point.

## How it presented

Everything that normally localises a fault pointed the wrong way:

- the engine logged a clean startup — `Engine 'utlxe' is RUNNING`, `Health endpoint started on
  port 8081`, `HttpTransport: listening on 0.0.0.0:8085`
- the UI sidecar logged a clean nginx startup, four worker processes, no errors
- neither container restarted (`restarts: 0`)
- the ARM deployment succeeded
- ingress was correct: `external: true`, `targetPort: 8088`
- **every** public path timed out — `/`, `/health`, `/metrics`, `/admin/info` — with
  `HTTP 000, curl exit 28`

Two healthy containers, a correct ingress, and nothing reachable. The only signal that named the
cause was in the replica state and the platform events:

```
utlxe     ready: false   restarts: 0   started: true
utlxe-ui  ready: true    restarts: 0   started: true

"Probe of Readiness failed with status code: 503"   Count: 88
```

Because `/` timed out as well, the obvious reading was that the nginx sidecar was broken — it was
not. A replica with any not-ready container receives no traffic at all, so a perfectly working
proxy looks dead.

## Why six months of testing missed it

Only a real Container Apps deployment exercises a readiness probe. Running the image locally,
running the conformance suite, and running the engine's own tests all bypass the orchestrator
entirely, and the engine is genuinely healthy in every one of them. The offer was published on
2026-05-11 and never deployed until today.

## The fix

Point the readiness probe at `/health`, which reports 200 whenever the engine state is RUNNING,
independent of how much work is loaded:

```diff
- { "type": "Readiness", "httpGet": { "port": 8081, "path": "/health/ready" } }
+ { "type": "Readiness", "httpGet": { "port": 8081, "path": "/health" } }
```

Liveness and startup continue to use `/health/live`.

`/health/live` would have been wrong here: it is unconditionally 200, which makes readiness
meaningless. `/health` tracks actual engine state, which is what readiness means to an
orchestrator — *able to accept requests*, not *already has work*.

## Verified

Redeployed the fixed template over the live deployment — same images, same nginx, one probe path
changed:

```
/                200   <!DOCTYPE html> … the Admin UI
/health          200   {"status":"UP","state":"RUNNING","transformations":0}
/health/live     200   {"status":"UP"}
/health/ready    503   {"ready":false,"transformations":0}
/metrics         200   utlxe_up 1
/admin/info      403   {"error":"Invalid or missing X-Admin-Key"}
```

`/health/ready` still answers 503 — the endpoint is unchanged. That is the proof: nothing about
the engine was altered, and the offering went from entirely unreachable to fully serving.

## The endpoint itself — fixed in EB19

`isReady = RUNNING && txCount > 0` remains the underlying defect, and the template change only
routes around it. An engine with no transformations is ready to *receive* them; conflating
readiness with having work loaded will mislead any orchestrator that probes it — Kubernetes,
Dapr, or a future deployment target. Fixing it properly means changing the endpoint's semantics
and shipping a new image, and should carry a test that asserts a freshly started engine reports
ready.

**Resolved on 2026-11-17 in [EB19](EB19-admin-info-reports-a-hardcoded-version.md)**, which
changed both sites — `HealthEndpoint.kt` and `/admin/info` — to `engine.state == RUNNING`, with
the transformation count still reported separately. The Marketplace template keeps pointing the
readiness probe at `/health`, which is now equivalent and avoids depending on a single
endpoint's semantics.
