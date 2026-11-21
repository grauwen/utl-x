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

The documentation is half right, which is worse than silent. `books/engine-azure` ch05 Step 6 does
state the rule — *"the transformation name must match the Dapr input component name
(`orders-in`)"* — but it states it for the **hand-built** walkthrough, where the reader creates the
component themselves with `az containerapp env dapr-component set` and can therefore name it after
their transformation. ch05 never addresses the **Marketplace** deployment, where the template has
already fixed the component as `servicebus-input` and the customer cannot choose it.

So a Marketplace customer following ch05 names their transformation `orders-in` to match the
component the book told them to create, while the component their deployment actually has is
called something else. Their deployment accepts messages and processes none. Nothing in the
wizard, the README or the offer listing mentions the constraint at all.

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
| 2a | create the `pubsub.azure.servicebus.topics` component — *done 2026-11-21* | both `mainTemplate.json` files | Azure config only |
| 2b | **stop hardcoding the component name** — *done 2026-11-21* | `modules/engine/config/TransformConfig.kt` (`DaprComponents`) | **UTLXe code** |
| 2c | check the engine and the templates agree on component names — *done 2026-11-21* | `deploy/azure-marketplace/build-zips.sh` | build guard |
| 3 | Service Bus assertions | `deploy/azure-marketplace/utlxe-acceptance.sh` | test script + a real namespace |
| 4 | document how a message finds its transformation — *done 2026-11-21* | `books/engine-azure` ch05 | documentation |

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

*What blocks it in Azure is one component that nobody creates.* The name the engine insisted on
was hardcoded in five places across two files, with no environment variable and no parameter.

**Fixed 2026-11-21 (2b).** One object now owns it:

```kotlin
object DaprComponents {
    const val DEFAULT_PUBSUB_NAME = "utlxe-servicebus"
    val pubsubName: String =
        System.getenv("UTLXE_PUBSUB_NAME")?.takeIf { it.isNotBlank() } ?: DEFAULT_PUBSUB_NAME
}
```

All five call sites read it — three in `HttpTransport`, two in `DaprIntegration` — so a deployment
can name the component whatever its templates do, and the default is the historical value, so
nothing that works today changes. Two tests pin it: one asserts `/dapr/subscribe` names
`DaprComponents.pubsubName` rather than a literal, and one asserts the default is still
`utlxe-servicebus`, because changing it would silently orphan any deployment that had already
created a component by the old name.

**And the guard that would have caught the original mismatch (2c), also 2026-11-21.**
`build-zips.sh` now compares the component names the templates *create* against the names anything
*addresses*:

- **Fails** on an orphan — a component the templates create that nothing points at. Verified by
  adding one: `ERROR: starter creates Dapr component(s) nothing addresses: orphan-output`.
- **Notes** the reverse, because `utlxe-servicebus` is a known gap rather than a regression:
  `NOTE: starter creates no 'utlxe-servicebus' component, so the engine's pub/sub path is
  unreachable there (EF26 item 2a).` That note now prints on every build, which is the point —
  the gap was invisible for as long as nothing looked for it. Promote it to a failure when 2a
  lands.

The guard's first version was wrong in the dangerous direction: its regex stopped at the first
`)` inside `parameters(...)`, so it missed
`if(parameters('enableEventHubs'), 'eventhub-output', 'servicebus-output')` and reported both
outputs as orphans. It now matches against the nested templates' `variables` blob instead of
parsing ARM expressions. A guard that cries wolf gets deleted, so this one had to be proven in
both directions before it was kept.

**2a — the component itself, done 2026-11-21.** Both templates now create it alongside the two
bindings, on the same `enableServiceBus` condition:

```
utlxe-servicebus   pubsub.azure.servicebus.topics     ← new
servicebus-input   bindings.azure.servicebus.queues
servicebus-output  bindings.azure.servicebus.topics
eventhub-output    bindings.azure.eventhubs
```

`consumerID` is set to the app id, which is the Service Bus **subscription** Dapr consumes from.
Dapr creates it on first use when the connection string carries Manage rights; otherwise it has
to exist already. That is a prerequisite worth stating in ch05 rather than discovering.

The guard from 2c confirms the contract now holds — the NOTE it printed on every build is gone —
and it still catches a break in both directions. Renaming the component in the template alone
produces, correctly, two complaints at once:

```
ERROR: starter creates Dapr component(s) nothing addresses: utlxe-pubsub-renamed
 NOTE: starter creates no 'utlxe-servicebus' component, so the engine's pub/sub path
       is unreachable there (EF26 item 2a).
```

**What this does and does not buy.** A transformation that declares `input.topic` in its
`transform.yaml` is now subscribed and routed by its own name, so one deployment can serve as many
topics as it has transformations — the cap this report opened with. It is **not** verified: nothing
has been deployed, Dapr has never been enabled in any test run, and adding a transformation needs a
revision restart because Dapr asks `/dapr/subscribe` once at sidecar startup. Until item 3 exists,
this is correct-by-construction, not correct-by-observation.

It is also deliberately **not** claimed in the Marketplace listing yet. `offer-listing.md` still
says queue in, topic out, because that is what has been tested — see item 3.

### 2d — a transformation declares its component AND its resource *(done 2026-11-21)*

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

**Implemented.** `MessagingEndpoint` gains an optional `component`, null by default so every
existing `transform.yaml` behaves exactly as before:

```yaml
output:
  topic: results-out          # the RESOURCE
  component: servicebus-output  # the Dapr COMPONENT, when it is not named after the resource
```

Both output call sites now use it — `/v1.0/publish/<component ?: pubsubName>/<topic>` and
`/v1.0/bindings/<component ?: resourceName>` — so the historical inference survives untouched when
the field is absent.

Verified against a real sidecar, with a deliberately discriminating case: point `component` at
something that does not exist and the publish must fail, because if the field were ignored the
engine would publish to the real pub/sub component and get a 204.

```
WARN  Dapr output topic 'results-out' returned 404
```

Two things that first run got wrong, both worth recording because both would have produced a
false pass:

- **The data class alone was not enough.** `parseMessagingEndpoint` in the Admin API did not read
  the field, so it never reached the config — the first test showed a successful publish and the
  config echoing `output=topic:results-out` with no component. Parsing and `endpointToMap` both
  needed it.
- **I was reading the wrong log line.** `[orders] … → OutputMessageId=…` is logged
  *unconditionally*, whatever the output publish returned. The real signal is the `WARN` above.

### A finding this turned up — a failed output is ACKed as success

Worth separating from EF26 and deciding on its own. When the output publish fails, the engine logs
at WARN and then **returns success to Dapr**, which acknowledges the message:

```kotlin
if (daprStatus !in 200..299) { logger.warn("Dapr output {} '{}' returned {} …") }
…
logger.info("[{}] MessageId={} → OutputMessageId={} …")   // unconditional
// Return success to Dapr (acknowledges the message)
```

So a misconfigured or unavailable output binding silently drops every result while Dapr considers
each message handled — no retry, no dead-letter, and an `info` line that reads like success. The
counter-argument is real: failing the delivery risks a poison-message loop on a permanent
misconfiguration. That is a design decision about at-least-once semantics, not a typo, so it
belongs in its own report rather than being changed here.

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

### Verified against a real Dapr sidecar (2026-11-21)

Everything above was argued from reading the code. It has now been run against **daprd 1.17.6**
locally — no Azure, no Service Bus namespace, and no Docker daemon, using component types that
need no infrastructure at all:

```
components/pubsub.yaml   pubsub.in-memory     named utlxe-servicebus
components/cron.yaml     bindings.cron        named orders-in-queue
```

**The pub/sub contract holds.** daprd read `/dapr/subscribe` and acted on it:

```
Component loaded: utlxe-servicebus (pubsub.in-memory/v1)
app is subscribed to the following topics: [[orders-in]] through pubsub=utlxe-servicebus
```

and a published message was delivered, unwrapped and transformed:

```
[orders] PubSub MessageId=2d4d4996-… ceSource=utlxe-app ceType=com.dapr.event.sent payload=13B
[orders] PubSub MessageId=2d4d4996-… → OutputMessageId=01a1feb3-… in 235718us
```

So the component-name contract (2b/2c), the subscription shape, routing by transformation name,
and CloudEvents unwrapping are all confirmed by an actual sidecar rather than inferred.

**Rule 3 holds, which is the fix this report exists for.** A cron input binding named
`orders-in-queue` delivered to a transformation called `invoices`, which merely *declared* that
queue in its `transform.yaml`:

```
[invoices] MessageId=01a1feb4-… binding=orders-in-queue
```

Before the change that was a 503. The transformation's name is genuinely free of the component's
name, proven end to end. (The parse error that follows is expected: cron posts an empty body and
the transformation declares `input json`. Routing was the subject.)

**Still not verified: rule 1.** `bindings.cron` cannot carry arbitrary message properties, so
whether Dapr surfaces a Service Bus application property as a `metadata.X-UTLXe-Transform` header
remains an inference from the correlation reads in the same function. That one needs a broker that
supports message metadata.

**Nothing Azure-specific is verified by this** — not the ARM components, not Service Bus lock
renewal, dead-lettering or the connection-string path. A local sidecar proves the engine's half of
the contract; it says nothing about the template's half.

### 3 — test coverage: smaller than it looked

The standing assumption here, including in the first draft of this report, was that Service Bus
coverage was blocked on having a Service Bus namespace. The run above shows that is wrong for the
part that matters most: **the routing logic is broker-agnostic**, and `pubsub.in-memory` plus
`bindings.cron` exercise it with no infrastructure, no cloud account and no Docker.

So the work splits:

- **Automated, locally — `scripts/dapr-local-test.sh`, added 2026-11-21.** Builds the jar if
  needed, starts the engine on free ports, starts a real `daprd` against an in-memory pub/sub
  component and two cron input bindings, asserts, and tears everything down. Eight assertions,
  no cloud account, no credentials, no Docker. Run it with `--keep` to leave both processes up.

  It reads `DEFAULT_PUBSUB_NAME` out of the engine source rather than repeating the string, so the
  harness cannot drift from what the engine addresses — the same contract `build-zips.sh` checks
  from the template side.

  Proven load-bearing: reverting the EF26 routing and rebuilding turns it red on exactly the right
  assertion, *binding routed by declared input*, while the seven that test pre-existing behaviour
  stay green.

  Two things it got wrong first, both worth keeping in the file as comments. It asserted rules 3
  and 4 against a **single** component, which can never pass — the exact-name match is
  deliberately more specific, so a transformation named after the component always wins and the
  declared-input path is unreachable. It now uses one component per rule. And it scans logs
  through a Python helper that strips ANSI codes, because `grep` reported `Binary file matches`
  and silently found nothing: the exact trap `AGENTS.md` documents.
- **Still needs a real namespace:** Service Bus semantics (lock renewal, retry, DLQ), the
  connection-string and identity paths, and `metadata.*` property forwarding.

That reframes item 3 from "we need test infrastructure" to "most of it needs nothing, and the rest
is a smaller ask".

### 3 — test coverage: the remainder

`utlxe-acceptance.sh` has **zero** Service Bus assertions and `enableDapr` has never been true in
any deployment we have made. Until that changes, the next release has the same blind spot. Writing
the assertions is easy; having a Service Bus namespace to run them against is a decision about test
infrastructure, and it is what has kept this untested.

## Documentation — done 2026-11-21

ch05 gains a section, *How a Message Finds Its Transformation*, and Step 6 loses its absolute
claim.

What was there before was not silence but a half-truth, which is worse. Step 6 said *"the
transformation name must match the Dapr input component name (`orders-in`)"* — correct for that
walkthrough, where the reader creates the component themselves and can name it anything. It is
wrong for a Marketplace deployment, where the template fixes the component as `servicebus-input`.
A customer reading ch05 and deploying from the Marketplace would follow a rule that cannot be
satisfied.

The new section states the three resolutions in order, says plainly which to use on a Marketplace
deployment and why (option 2 — declare the queue, keep your own name), gives the `curl` for it,
and covers the one-queue-many-transformations case with the `X-UTLXe-Transform` property. It also
records the pub/sub caveat the local harness surfaced: Dapr asks for subscriptions **once**, at
sidecar startup, so a transformation uploaded afterwards is not subscribed until the revision
restarts.

Book rebuilt: 147 pages. One wrinkle worth noting for anyone editing these chapters — Typst uses
single asterisks for bold, and a markdown-style `**bold**` compiles with a warning rather than an
error, so it is easy to leave behind.
