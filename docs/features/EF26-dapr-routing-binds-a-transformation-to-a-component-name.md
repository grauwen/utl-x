# EF26 — Dapr routing tied a transformation's name to a Dapr component's name

**Status:** **PARTLY DONE (2026-11-21)** — message *routing* inside the engine is implemented and
tested. What remains is split across the engine, the ARM templates and the test scripts; see
*Remaining work*, which says for each item where it lives. "Engine half / template half" was the
first draft's wording and it was wrong: the pub/sub item needs a change in **both**.
**Priority:** High — one of the two advertised message sources was unusable as documented.
**Component:** `:modules:engine` — `transport/HttpTransport.kt` (`handleDaprInput`)
**Found:** 2026-11-21, reading the Azure offering's messaging path. Not by a test: the Service Bus
path has **no test coverage at all** in `utlxe-acceptance.sh`, and `enableDapr` has never been
true in any deployment we have made.

## The defect

Dapr delivers an input binding to `POST /{component-name}`. The engine took that path segment and
used it directly as a transformation id:

```kotlin
val bindingName = call.parameters["bindingName"] ?: "default"
val transformId = call.request.header("X-UTLXe-Transform") ?: bindingName
val instance = registry.get(transformId)       // looks up by NAME. Only ever by name.
```

So three unrelated identifiers were forced to be one string:

| | chosen by |
|---|---|
| the Dapr component name | the ARM template |
| the HTTP route | Dapr — it *is* the component name, not configurable |
| the transformation's name | whoever uploaded it |

The Marketplace template names its component `servicebus-input`. Therefore a customer had to name
their transformation `servicebus-input`, or every message returned:

```
503  "No transformation loaded for binding 'servicebus-input'"
```

Nothing documented that — not the book, not the wizard, not the README. A customer following the
documentation would have had a deployment that accepted messages and processed none.

It also capped a deployment at **one queue**: a binding is one component per queue, the component
name is the transformation id, so a second queue would need a second transformation named after it
and a second component that the template does not create.

`X-UTLXe-Transform` existed as an override and nothing set it. Dapr does not send it, and the only
other occurrence in the repository is one unit test.

## Why it was built this way

Not carelessness. The engine's self-hosted path generates **one component per transformation
endpoint**, named after the queue the transformation declares
(`DaprIntegration.generateQueueBinding`: `val componentName = endpoint.queue!!`). In that world one
component really does belong to one transformation. The conflation was still there — it forced
*transformation name == queue name* — but it was invisible.

Azure made it absurd, because Container Apps cannot create a component at runtime. Components are
ARM resources created before any transformation exists, so the template hardcodes one whose name
describes a *role*, not anybody's transformation.

## The fix

Resolution in `handleDaprInput`, most specific first:

1. **`metadata.X-UTLXe-Transform`** — a per-message selector. Dapr forwards Service Bus application
   properties as `metadata.*` headers, which the correlation reads in the same function already
   depend on. One queue can now feed many transformations, chosen by the sender.
2. **`X-UTLXe-Transform`** — the unprefixed form, for a caller posting directly.
3. **A transformation whose `transform.yaml` declares that queue or topic as its input.** This makes
   the transformation's own config authoritative and frees its name entirely.
4. **A transformation named after the component** — the historical behaviour, kept deliberately so
   nothing that works today stops working.

Ambiguity in rule 3 — two transformations declaring the same input — is logged with both names and
resolved deterministically by sorted name rather than silently.

### The change is strictly additive, deliberately

Rules are evaluated cheapest-first, and that ordering is a design decision rather than an
accident. The exact-name lookup is a `ConcurrentHashMap` hit; scanning the registry for a declared
input is O(n) plus a sort, and this code runs on **every delivered message**. A first draft
computed the scan before the name lookup, which would have made every message pay for it even when
the old fast path would have hit.

Ordering the name lookup first has a second effect that matters more than the cost: **whenever the
old one-line resolution found a transformation, the new code finds the same one by the same
route.** The new behaviour applies only where the old code answered 503. So no working deployment
can change behaviour — there are none, but the property is worth having regardless.

### Blast radius

Only two routes reach `handleDaprInput`:

```
POST /{bindingName}                  ← Dapr input binding delivery
POST /api/dapr/input/{bindingName}   ← the backward-compatible alias
```

Untouched: the published data path `/api/transformations/{id}/run`, `/api/execute/*`,
`/api/transform`, `/api/load`, the pub/sub path `/pubsub/{name}`, the whole admin API on 8081, and
the stdio and gRPC transports — which the .NET and Go wrappers drive, and which never enter this
function.

## Verification

`EF26DaprRoutingTest`, 7 tests. `:modules:core:test` 441 and `:modules:engine:test` 1052, 0 failures.

Proven load-bearing by restoring the one-line resolution:

```
a transformation declaring the queue receives it under any name()   FAILED
the Dapr metadata message property selects the transformation()     FAILED
the message property wins over a declared input()                   FAILED
an unknown selector is a 503, not a silent fallback()               FAILED
a transformation named after the component still works()            PASSED  ← compat guard
the bare header still selects the transformation()                  PASSED  ← compat guard
an unrelated transformation does NOT receive the message()          PASSED  ← over-reach guard
```

The three that pass either way are the point of including them: two pin behaviour that must not
change, and the third stops the new fallback being too eager — a loaded transformation declaring a
*different* queue must not be picked up merely because it is the only one present.

### A test-harness fix that came with it

All three transport test classes written this week slept a fixed 1200 ms for the server to bind.
That passes in isolation and loses the race under full-suite load, surfacing as an intermittent
`Connection refused` that looks like a product failure. They now poll `/api/health` until it
answers. `HttpTransportTest` has the same fixed-sleep pattern *and* a hardcoded port
(`private val port = 18085`, which `AGENTS.md` forbids) — not touched here, but worth cleaning up.

## Remaining work — and where each piece lives

The engine no longer forces the naming, but the offering still deploys **one queue binding**. The
rest is not all in one place:

| # | item | lives in | kind |
|---|---|---|---|
| 1 | several queue components from one deployment | `deploy/azure-marketplace/*/mainTemplate.json` (an ARM `copy` loop) + both `createUiDefinition.json` | Azure config only |
| 2a | create the `pubsub.azure.servicebus.topics` component | the same two `mainTemplate.json` files | Azure config only |
| 2b | **stop hardcoding the component name** | `modules/engine` — `HttpTransport.kt:473, 695, 924` and `DaprIntegration.kt:231, 370` | **UTLXe code** |
| 2c | assert the template creates every component the engine addresses | `deploy/azure-marketplace/build-zips.sh` | build guard |
| 3 | Service Bus assertions | `deploy/azure-marketplace/utlxe-acceptance.sh` | test script + a real namespace |
| 4 | document how a message finds its transformation | `books/engine-azure` ch05 | documentation |

### 1 — several queues: Azure configuration only

A Dapr *binding* is one component per queue, created by ARM before any transformation exists. More
queues means more components: a `copy` loop over a list of names collected in the wizard. The
engine needs nothing — after this change it already routes each one correctly, by declared input or
by per-message selector.

### 2 — pub/sub: Azure configuration **and** engine code

This is the item that is not purely one or the other, and the reason the first draft's framing was
wrong.

*The engine side already works.* `GET /dapr/subscribe` returns one subscription per transformation
that declares `input.topic`, each routed to `/pubsub/{transformation name}` — one component, many
topics, no naming coupling at all. That is the design this whole report is about, already written.

*What blocks it in Azure is one component that nobody creates* — and the name the engine insists on
is **hardcoded in five places** as `utlxe-servicebus`, with no environment variable and no
parameter:

```
HttpTransport.kt:473    "pubsubname" to "utlxe-servicebus"
HttpTransport.kt:695    /v1.0/publish/utlxe-servicebus/{binding}
HttpTransport.kt:924    /v1.0/publish/utlxe-servicebus/{binding}
DaprIntegration.kt:231  if (ep.isPubSub) "utlxe-servicebus" else …
DaprIntegration.kt:370  val componentName = "utlxe-servicebus"
```

So wiring pub/sub up needs three separate changes: the template creates the component (Azure),
the engine reads the name from `UTLXE_PUBSUB_NAME` defaulting to today's value so nothing existing
breaks (**UTLXe code**), and `build-zips.sh` asserts the two agree (build guard). That third one is
the real lesson — two hardcoded names on opposite sides of a boundary, with nothing checking they
match, is how an entire code path stayed dead without anyone noticing.

### 2d — a transformation should declare its component AND its resource

The same boundary, in the output direction, and this one is purely **UTLXe code**.

Dapr keeps two things separate:

```
POST /v1.0/publish/<pubsubname>/<topic>     ← component, then topic
POST /v1.0/bindings/<name>                  ← component
```

UTLXe collapses them into one value:

```kotlin
val resourceName: String? get() = queue ?: topic ?: eventhub   // TransformConfig.kt
…
"/v1.0/publish/utlxe-servicebus/$outputBinding"   // used as a TOPIC
"/v1.0/bindings/$outputBinding"                   // used as a COMPONENT NAME
```

So `outputBinding` lands in the topic slot on one branch and the component slot on the other. That
is only coherent under UTLXe's own convention — *the component is named after the resource* —
which is exactly what `DaprIntegration` does for self-hosted (`componentName = endpoint.queue!!`).

**Container Apps breaks the convention**, because ARM names components after their role
(`servicebus-output`), not after anybody's topic. Which is the real reason this exists:

```
UTLXE_OUTPUT_BINDING_SERVICEBUS_INPUT = servicebus-output
```

That environment variable is not a feature. It is a patch for the mismatch, keyed by the *input*
component's name — and note `outputIsPubSub` is forced false whenever it is set, so setting it
makes the pub/sub branch unreachable. Azure sets it on every Dapr deployment.

The fix is to stop inferring one from the other: give `MessagingEndpoint` an optional `component:`
field alongside `queue`/`topic`/`eventhub`, defaulting to today's behaviour when absent. Then a
transformation can say "publish to topic `orders-out` through component `servicebus-output`", the
env-var patch can go, `outputIsPubSub` stops being disabled by it, and the Azure naming stops being
a special case rather than being worked around.

### 2e — the Dapr sidecar port was a literal (fixed 2026-11-21)

Found while auditing the codebase for hardcoded ports. `HttpTransport` wrote
`http://localhost:3500/...` at **four** call sites, so the Dapr sidecar port could not be changed
in any deployment that moves it. `DaprIntegration` had already taken it as a constructor parameter
(`private val daprPort: Int = 3500`); this class simply had not followed.

Now read from the environment, which is how Dapr tells an app where its sidecar is — and which
nothing in the repository was reading:

```kotlin
private val daprPort: Int = System.getenv("DAPR_HTTP_PORT")?.toIntOrNull() ?: DEFAULT_DAPR_PORT
```

The wider audit was clean: no literal ports in the main sources of `:cli`, `:core`, `:daemon`,
`:bundle` or `:analysis`, and every other port is a named default on a configurable parameter.
One test violated the rule — `HttpTransportTest` pinned `18085`, which `AGENTS.md` forbids — and
now takes a free port from `ServerSocket(0)`.

### 3 — test coverage: a script change and a decision

`utlxe-acceptance.sh` has **zero** Service Bus assertions and `enableDapr` has never been true in
any deployment we have made. Until that changes, the next release has the same blind spot. Writing
the assertions is easy; having a Service Bus namespace to run them against is a decision about test
infrastructure, and it is what has kept this untested.

## Documentation

Not yet updated. `books/engine-azure` ch05 describes Service Bus setup and does not mention how a
message finds its transformation — which, until this change, was the single most important thing a
customer needed to know and the one thing that would have made their deployment work.
