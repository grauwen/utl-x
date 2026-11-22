# EF24 — the data plane has no credential, so the offering has no usable HTTP path

**Status:** Open
**Priority:** High — the Marketplace wizard offers "HTTP only" as the default way for a
customer's application to reach UTLXe, and no such path exists
**Component:** `:modules:engine` — `transport/HttpTransport.kt` (authentication), with
`deploy/azure-marketplace/*` and `utlxe-ui/nginx.conf` (topology)
**Found:** 2026-11-18, preparing the offering for a customer pilot

## The gap

**There is no public HTTP path by which a customer's application can send UTLXe a message.**
Azure Service Bus is the only working data path in the offering.

Container Apps ingress publishes exactly one port, and the template spends it on 8088 — the
Web UI container. The UI's nginx proxies `/admin/`, `/health` and `/metrics` to the engine's
*admin* port 8081. Nothing proxies the data plane on 8085, and ingress has no second port to
give it.

### What 1.4.4 already did about it

Until 1.4.4 the wizard offered this as its **default**:

```
HTTP only — your application calls UTLXe       <- promised a path that does not exist
Azure Service Bus — queue in, topic out
```

A customer choosing the default could deploy, open the UI, upload a transformation, and then
have no address to send a message to — something a pilot would hit in the first hour. 1.4.4
reworded it to describe what the mode actually is:

```
Admin API only — evaluate now, add a message source later
Azure Service Bus — queue in, topic out
```

with the step stating plainly that applications cannot post messages over HTTP and that no
public data endpoint exists in this release.

**That closed the false promise, not the gap.** The offering still has one data path, and a
customer who wants to call UTLXe from their own application over HTTP cannot. This report is
about building that path; everything below is unchanged by the rewording.

## Why it cannot simply be proxied

The obvious fix — a `location /transform/` block pointing at 8085 — would publish an
**unauthenticated** interface. `HttpTransport` performs no credential check whatsoever
(`X-Admin-Key` appears zero times in the file), and 8085 serves, among others:

```
POST /api/transform            transform arbitrary data
POST /api/load                 load a transformation  <- a mutation
POST /api/execute/{id}
POST /api/execute-batch/{id}
POST /api/execute-pipeline
POST /{bindingName}            Dapr binding delivery
```

`/api/load` is the one that forbids this outright: publishing 8085 as it stands would let
anyone who finds the URL install transformation logic into the engine.

That is not a defect in `HttpTransport`. The data plane was designed for a **trusted network**:
the Dapr sidecar calls it over localhost inside the pod, and in a self-hosted deployment it
sits on a private network. The design is coherent; what is missing is any way to expose it
safely, and the wizard currently promises exactly that.

## Topology — does the caller even need a public URL?

Usually not. An enterprise integration flow calling UTLXe is typically *also* in Azure — a
Logic App, a Function, AKS, another Container App — and should reach it over the environment's
internal network rather than the public internet. Container Apps supports this directly with
`ingress.external = false`, which publishes an internal FQDN reachable only from inside the
environment or its VNet.

The constraint shaping every option: **a container app has exactly one ingress and one target
port.** `additionalPortMappings` exists only on workload-profile environments, which this offer
does not provision. So the UI and the data plane cannot both be published from one app.

### Option A — internal ingress, no key needed

Set `external: false`. The data plane is reachable only from inside the environment/VNet,
which is where the caller usually is. No credential required, because the network is the
boundary.

Cost: the Web UI stops being reachable from a browser on the internet. Operators need VNet
access, Bastion or a VPN. For an enterprise customer that is often *correct*; for a small
organisation running one engine it removes the operator surface they were sold.

### Option B — two container apps

Engine app with **internal** ingress on 8085; UI app with **external** ingress on 8088, its
nginx proxying `/admin/` to the engine's internal FQDN instead of localhost. Clean separation:
the data plane is never public, the operator surface is.

Cost: two apps instead of one, a second set of CPU/memory, cross-app networking in place of
loopback, and a larger template. It also changes the readiness and scaling story, since the two
scale independently.

### Option C — the credential lives in the proxy, not the engine

UTL-X is a transformation engine. Authentication is not its job, and putting it there means
every change to the auth story ships a new engine image. The UI container already sits in the
pod, already terminates the public path, and is already the only thing that can reach 8085 —
so it is where a public-facing credential belongs.

Two parts, and the second matters more than the first.

**Expose endpoints, not the port.** A blanket `location /transform/ -> :8085` publishes
everything on that port, `/api/load` included. Routing specific paths instead means the
dangerous ones are not reachable by construction rather than blocked by a rule that could be
reordered or removed:

```nginx
# Only what a message sender needs. /api/load is never routed, so no key leak exposes it.
location = /transform          { proxy_pass http://127.0.0.1:8085/api/transform; }
location ~ ^/execute/([^/]+)$  { proxy_pass http://127.0.0.1:8085/api/execute/$1; }
```

**Check the key at the edge:**

```nginx
location = /transform {
    if ($http_x_api_key != "${DATA_API_KEY}") { return 403; }
    proxy_pass http://127.0.0.1:8085/api/transform;
}
```

`DATA_API_KEY` arrives as a Container App secret, exactly as the admin key does after EB19,
and `envsubst` renders it at container start — the same mechanism that already injects
`UI_PORT` and `ADMIN_PORT`. `$http_x_api_key` survives substitution because no environment
variable of that name exists, which is how `$uri` and `$host` already survive.

*The engine needs no change at all.*

#### What this does not give you

- **Not a constant-time comparison.** nginx string equality is not timing-safe. Against a
  random 36-character key the practical risk is small, but it is not zero and should not be
  described as a hardened check.
- **The rendered config holds the literal key** inside the container, readable by anything in
  that container. The same is true of any env-var secret; it is a reason to keep the key out
  of the *engine's* environment, not an argument against this approach.
- **One key for all callers**, rotated only by restarting the container. Per-caller keys,
  revocation and audit need a real gateway — Azure API Management in front of the Container
  App — which is the right answer for a customer who asks for it, and overkill for one who
  does not.
- **It authenticates, it does not authorise.** Every caller holding the key can transform
  through every loaded transformation. If one customer's flows must not reach another's, this
  is not the mechanism.

### Option C-engine — a credential inside the engine (rejected)

Add a second key — `UTLXE_DATA_KEY` — checked by `HttpTransport` on every `/api/*` route, and
proxy a path to 8085 through the existing nginx. One app, one ingress, public data plane.

Requires, at minimum:

1. A new engine credential, distinct from the admin key, so an application sending messages
   does **not** hold authority to load, delete or reconfigure transformations. Sharing the
   admin key for this would be worse than the gap it fixes.
2. A decision about `/api/load` on the public path: it should almost certainly remain
   admin-only or be refused entirely when the request arrives through the public route.
3. A wizard field, template plumbing, and the key stored as a Container App secret — the
   handling EB19 established for the admin key.

### Option D — gRPC

The engine already ships a gRPC transport. `GrpcTransport.kt` exists, `--also-grpc` enables it
on 9090, and `proto/utlxe/v1/utlxe.proto` defines `UtlxeService` with `Execute`, `ExecuteBatch`
and `ExecutePipeline` beside the loading and health calls. Azure Container Apps supports gRPC
ingress directly (`transport: "grpc"`, HTTP/2 over TLS).

**None of it is switched on.** The Marketplace template mentions gRPC zero times: ingress
`transport` is `http`, Dapr's `appProtocol` is `http`, and the container args never pass
`--also-grpc`. The transport is compiled into every image shipped and never reachable.

Where it would genuinely win:

- **Protobuf over HTTP/2** carries a payload without the JSON envelope and escaping problem
  that `/api/execute/{id}` has — `ExecuteRequest.payload` is `bytes`, so XML is XML.
- **A typed contract** the `.NET` and Go wrappers already understand; they currently drive the
  engine over stdio protobuf, so the message types are built and tested.
- **Throughput**, for a customer doing high-volume programmatic integration.

What it does not solve:

- **The same one-port constraint.** gRPC ingress would consume the single published port, so
  the offer would serve gRPC *or* the Web UI, not both. Option B's two-app split applies here
  unchanged.
- **The client barrier.** A customer integrating over gRPC needs the `.proto` and generated
  stubs. For a Logic App, a Function or a partner posting a document, HTTP is what they have.
- **Authorisation is still absent.** `UtlxeService` exposes `LoadTransformation` and
  `LoadBundle` on the same service as `Execute`, so publishing the service wholesale has
  exactly the `/api/load` problem in a different protocol. gRPC interceptors or an
  nginx/Envoy layer would have to restrict it per-method.
- **It is wholly unexercised in Azure.** Nothing in this repository has ever deployed the
  engine with gRPC enabled, so "it works" is an inference from the code, not an observation.

gRPC is the right answer for a high-volume, programmatic customer and the wrong one for a first
HTTP integration. It belongs in the same decision as Options A–C rather than instead of them —
and if it is ever enabled, `--also-grpc` plus a second container app is the shape, not a
replacement for the HTTP path.

## Which endpoint can be published — and why one small engine change is needed

Of what 8085 serves, most is disqualified:

| endpoint | publishable | why |
|---|---|---|
| `POST /api/load` | **no** | loads a transformation |
| `POST /api/transform` | **no** | carries `utlxSource` — the caller supplies the script |
| `POST /{bindingName}` | no | acknowledges only; the result goes to the output binding, never the response |
| `GET /api/transformations` | maybe | lists what is loaded; information disclosure |
| `POST /api/execute/{id}` | **yes** | runs a *pre-loaded* transformation; body carries data only |

So `/api/execute/{id}` is the one, and it has a usability problem for this product:

```kotlin
data class ExecuteRequestBody(val payload: String, val contentType: String = "application/json", ...)
```

`payload` is a **String**. An XML message must be escaped into a JSON string to be sent:

```json
{"payload":"<Order id=\"A-1\"><Customer><Name>Acme</Name></Customer></Order>",
 "contentType":"application/xml"}
```

For an engine whose premise is "XML, JSON, CSV, YAML in, any of them out", requiring callers
to JSON-escape their XML is the wrong front door — and it grows the payload and forces every
client to build an envelope.

### A raw-body variant, and why it belongs in the engine

```kotlin
// POST /api/execute/{id}/raw — body as-is, Content-Type honoured
post("/api/execute/{id}/raw") {
    val id = call.parameters["id"] ?: return@post /* 400 */
    val bytes = call.receive<ByteArray>()
    val contentType = call.request.contentType().toString()
    // same ExecuteRequest construction as /api/execute/{id}, without the envelope
}
```

About twenty lines beside the existing handler, reusing `TransportHandlers.handleExecute`.

This is a *different kind* of engine change from putting a credential in it. Authentication is
a gateway concern and belongs in the proxy — that separation stands. **Accepting data in its
native format is not a gateway concern**; it is the engine's core job, and nginx cannot do it
anyway (building a JSON envelope from a raw body needs Lua or njs).

So the division of labour is:

- **nginx** — the credential, and narrow routing that publishes this path and nothing else
- **engine** — one endpoint that takes a message in the format it is already in

## Recommendation

**Option C, with the credential in the proxy**, is the cheapest way to deliver an HTTP data
path honestly: one nginx config, one new secret, one UI image, plus roughly twenty lines in the
engine for a raw-body execute endpoint. Narrow routing — publishing `/api/transform` and `/api/execute/{id}`
rather than port 8085 — is the part that makes it safe, and it holds even if the key leaks.

Keeping authentication out of the engine is also the right separation on its own terms: a
transformation engine should transform, and a change to the auth story should not require a
new engine release.

The interim step — relabelling the wizard so the offer advertises only what it delivers — was
taken in 1.4.4. It removes the misleading promise and buys time to build the path properly,
but an offering with a single data path is a narrower product than the one the documentation
and the engine's own `/api/*` surface imply.

## What must not happen

Proxying 8085 without a credential, as a quick fix to make the wizard's promise true. It would
turn a missing feature into an unauthenticated remote code path.

---

# Implementation plan — the raw-body route

Written 2026-11-19, after [EB21](../bugs-fixed/EB21-http-json-envelope-destroys-the-payload-charset.md)
established what the enveloped route actually does to a payload. The plan below supersedes the
sketch in *"A raw-body variant"* above in two ways: the new route is **not** a variant of
`/api/execute/{id}`, and `/api/execute/{id}` is **not** published at all.

## Why not publish `/api/execute/{id}`

EB21 fixed its charset handling, so it is no longer *wrong*. It is still the wrong thing to
publish:

1. **Binary formats stay inexpressible.** `ExecuteRequestBody.payload` is a `String`; a JSON
   string holds text, not arbitrary bytes, and there is no base64 field. Avro and Protobuf — two
   of the ten supported formats — cannot be submitted that way at all. This is the shape, not a
   bug, so no fix removes it.
2. **It inverts the product's premise at the one place customers touch.** The UDM exists so
   transformation logic never sees the source format. An envelope that requires every document to
   be escaped into JSON makes JSON privileged at the API boundary.
3. **A published shape is permanent; the credential gap is an afternoon.** Narrow nginx routing
   plus a second secret is config. Changing a request shape customers have integrated against is a
   deprecation window and two code paths, forever.
4. **It inflates the body ~16% on quote-heavy XML**, measured, which turns the 10 MB
   `MAX_REQUEST_BODY_BYTES` ceiling into roughly 8.6 MB of actual document for no reason the
   caller can see.

The shape to publish instead is the one the engine already runs in production: **id in the path,
document as the body, `Content-Type` as the header.** Three code paths use it today —
`handleDaprInput`, `handlePubSubInput`, and `POST /admin/transformations/{name}/test` — and all
three reach the same `TransportHandlers.handleExecute`. Publishing it exposes a proven path rather
than a new one.

## Naming: no version prefix, and the admin API's own shape

An earlier draft of this plan put the route at `POST /v1/transform/{id}`. Both halves of that were
wrong.

**The `/v1/` prefix was an exception for its own sake.** Nothing else in this codebase versions a
path, so it would have been the single versioned route, needing explanation in the book and a
decision from whoever adds the next one. The argument for it -- "a published contract must be able
to evolve" -- also undercuts the argument for the raw-body shape in the first place: the shape is
chosen *because* it is durable. And an unversioned path forecloses nothing. If the contract ever
has to break, a new path can simply appear alongside it; the old one keeps working, which is
exactly what a version prefix would have bought.

**`transform` was the wrong word.** `/v1/transform/{id}` sits one prefix away from
`DELETE /api/transform/{id}`, the unauthenticated unload. Two paths that differ by a prefix, one of
which must never be published, is a hazard in the two places it matters: the nginx config and the
access log.

The convention already exists. The admin API uses `transformations/{name}/{action}` in thirteen
routes -- `/test`, `/pause`, `/resume`, `/config`, `/sync`, `/messaging`, `/validation` -- and
`GET /api/transformations` already exists on the data plane. So:

```
POST /api/transformations/{id}/run
```

is the data-plane sibling of `/admin/transformations/{name}/test`, which is the same operation
behind the admin key. The plural and the `/run` suffix keep it clear of the singular unload path,
and the anchored nginx pattern still publishes exactly one shape.

## Step 1 — engine: a public raw-body execute route

New route on the data plane (8085), beside the existing ones in `HttpTransport.start`:

```kotlin
// EF24: the published data path. Raw body, id in the path, Content-Type honoured.
post("/api/transformations/{id}/run") {
    if (engine.isHeapPressure()) { /* 503 HEAP_PRESSURE, as /api/execute/{id} does */ }
    val id = call.parameters["id"]
        ?: return@post call.respond(HttpStatusCode.BadRequest, …)

    val bytes = call.receive<ByteArray>()
    val proto = ExecuteRequest.newBuilder()
        .setTransformationId(id)
        .setPayload(ByteString.copyFrom(bytes))                       // EB21: raw bytes
        .setContentType(call.request.contentType().toString())
        .setCorrelationId(call.request.header("X-Correlation-Id") ?: "")
        .setMessageId(call.request.header("X-Message-Id") ?: UuidV7.generate())
        .build()
    val resp = TransportHandlers.handleExecute(proto, engine)
    …
}
```

Three deliberate differences from `/api/execute/{id}`:

- **The response body is the output, with the transformation's own content type** — not a JSON
  object with the output nested as an escaped string. A caller asking for CSV should get CSV back,
  not `{"output":"a,b\nc,d\n"}`. This needs the output format from the registry
  (`instance.config`) to set the response `Content-Type`.
- **`correlationId` and `messageId` come from headers**, which is how Dapr already passes them
  (`metadata.CorrelationId`). Nothing in-band.
- **Errors use status codes, not a success flag.** `handleExecute` already classifies:
  `ErrorClass.TRANSIENT` → 503, otherwise 422, mirroring `HttpTransport.kt:197`.

Roughly 40 lines, no new handler logic.

**Metrics need no change.** An earlier note in this document said test-shaped calls were invisible
to metrics. That was wrong: `TransportHandlers.handleExecute` calls `instance.recordExecution()`
itself at `TransportHandlers.kt:191`, so every path through it is counted. A comment at
`AdminEndpoint.kt:287` claimed *"Test calls are NOT counted in metrics (don't call
recordExecution)"* — it described a caller obligation the shared handler had taken over. Corrected
in place 2026-11-19, so no work is outstanding here.

### What this route must not do

- No `utlxSource`. The caller supplies **data**, never a script. That is what disqualifies
  `/api/transform` from publication regardless of its shape.
- No load, unload, or bundle verb. Those stay on 8081 behind the admin key.
- No listing. `GET /api/transformations` tells a caller what else exists; keep it unpublished.

## Step 2 — nginx: the credential, and routing narrow enough to survive a leak

`utlxe-ui/nginx.conf` gains one location, and **only** one:

```nginx
# EF24: the published data path. A key distinct from the admin key, so a data-plane
# credential can never load, delete, or list a transformation.
location ~ ^/api/transformations/[A-Za-z0-9._-]+/run$ {
    if ($http_x_api_key != "${DATA_PLANE_KEY}") { return 401; }
    proxy_pass http://127.0.0.1:8085;
    proxy_set_header Host $host;
    client_max_body_size 10m;          # match MAX_REQUEST_BODY_BYTES; fail at the edge
    proxy_read_timeout 120s;           # a large XML transform outlives the 60s default
}
```

Four things are load-bearing:

- **The regex, not a prefix.** `location /api/transformations/` would also match
  `/api/transformations/../transform` before normalisation in some configurations. An anchored pattern
  limited to one path segment publishes exactly one shape.
- **A separate key.** `DATA_PLANE_KEY`, not `UTLXE_ADMIN_KEY`. This is the whole point: the
  credential a message sender holds must not be able to deploy or delete a transformation. It is
  also the answer to *"we're going to have key certification in the Kotlin code? Don't like that.
  It's a mapping tool."* — authentication is a gateway concern and stays in the gateway.
- **Narrow routing, so a leak is bounded.** Even with the key, a holder can execute pre-loaded
  transformations and nothing else. Port 8085 is never proxied wholesale.
- **`127.0.0.1`, not `localhost`.** The engine binds the IPv4 wildcard, and nginx resolves
  upstreams at config-parse time; `localhost` can resolve to `::1` and get connection refused from
  a healthy engine. The existing proxies already carry this comment.

`Dockerfile` gains `ENV DATA_PLANE_KEY=""` so `envsubst` has something to substitute, and the
location must be inert — 401 on everything — when the variable is empty. An unset key must never
mean "no check".

## Step 3 — template: generate and wire the key

Both `mainTemplate.json` files, mirroring how `adminKey` is handled since 1.4.4:

- a `dataPlaneKey` parameter, `type: "secureString"`, declared on the parent **and** on the
  `container-app` nested template (`expressionEvaluationOptions.scope: "inner"` means the nested
  template sees only its own parameters — the same thing that blocked EB18);
- a second Container App **secret**, alongside `admin-key`, and
  `{"name": "DATA_PLANE_KEY", "secretRef": "data-plane-key"}` in the `utlxe-ui` container's `env`
  — the UI container, because that is where nginx runs;
- a new output, `dataPlaneUrl` → `[uri(<base>, 'api/transformations/')]`. Name it so arm-ttk's URI rule
  is satisfied: a nested output whose name looks like a URI must be a URI, which is what forced
  `containerAppUrl` → `containerAppAddress` in 1.4.3.

Both wizards gain a field in the admin-key section with the same 12-character minimum and a
generated UUID default, plus one line of InfoBox text saying plainly what the two keys do
differently.

`build-zips.sh` needs nothing new — the plan-equivalence guard will fail the build if only one
plan gets the change, which is what it is for.

## Step 4 — tests

**Engine** — `EF24RawBodyRouteTest.kt`, `ServerSocket(0)` for the port:

1. XML posted raw returns the transformed output with the **output** content type, not a JSON
   envelope.
2. A non-UTF-8 payload survives — the EB21 guard transformation, reused, so this route cannot
   regress into `copyFromUtf8`.
3. A payload that is not valid text at all (an Avro or Protobuf fixture) round-trips. **This is
   the test that justifies the whole route**, because it is the case `/api/execute/{id}` cannot
   express.
4. An unknown id returns 404, not 500.
5. `X-Correlation-Id` is echoed into the response and appears in the execution record.
6. Over 10 MB is rejected on `Content-Length` without being read.

**Acceptance** — `utlxe-acceptance.sh` gains a group that, against a live deployment:

1. posts raw XML with the data-plane key and asserts the transformed output;
2. posts with the **admin** key and asserts **401** — proving the keys are genuinely separate,
   which is the security claim the whole design rests on;
3. posts to `/api/load` and `/admin/transformations/x` through the public URL and asserts both are
   unreachable — proving the routing is narrow and not merely documented as narrow.

Assertion 2 is the one that must never be skipped. If the two keys are ever wired to the same
secret, everything else here still passes.

## Step 5 — documentation

- `books/engine-azure` ch02: the Outputs table gains `dataPlaneUrl`; the *Message source* bullet
  stops saying *"applications cannot post messages to the engine over HTTP — there is no public
  data endpoint in this release"*.
- ch05 or ch06: a short section with a `curl` a customer can paste, and an explicit statement that
  the data-plane key cannot deploy or delete.
- ch11 (Security): the two-key model, and what a leaked data-plane key can and cannot do.
- `deploy/azure-marketplace/README.md`: the second secret in the release checklist.
- The wizard's messaging labels, reworded in 1.4.4 to *"Admin API only — evaluate now, add a
  message source later"*, can finally offer HTTP as a real answer.

## Sequencing, and what not to bundle

| order | step | status |
|---|---|---|
| 1 | engine route + tests | **done** — `POST /api/transformations/{id}/run`, `EF24RawBodyRouteTest` (9 tests) |
| 2 | nginx + Dockerfile | **done** — anchored regex location, `DATA_PORT`, `DATA_PLANE_KEY` defaulting to empty |
| 3 | template + wizards | **done** — `dataPlaneKey` secureString, `data-plane-key` secret, `dataPlaneUrl` output, PasswordBox in both wizards; arm-ttk clean |
| 4 | acceptance assertions | **done** — 8 new assertions incl. admin-key-rejected and three narrow-routing probes |
| 5 | book and README | **done** — ch02 data-path section + `dataPlaneUrl`, ch11 "Two Keys, Two Jobs", README "The two keys" |

**Not yet done, and the reason the above is not finished work:** nothing has been deployed. The
nginx config has never been parsed by nginx — the Docker daemon was not running locally, so
`nginx -t` could not be run; the template substitution was verified by rendering it, which checks
that `$http_x_api_key` and `$dp_key` survive `envsubst` and that braces balance, but not that
nginx accepts the directives. A `utlxe-ui:1.4.4` image must be built and both plans deployed
before any of this can be called verified. A malformed nginx config means the container fails to
start, which the acceptance suite would catch immediately — but it has to be run.

Three things to keep out of this change:

- **The two-app split (Option B).** It is a better long-term topology and a much larger change,
  and it is not required for a credentialed data path. Keep them separate decisions.
- **gRPC (Option D).** One ingress, one `targetPort`: publishing gRPC instead would hit the same
  constraint with a higher client barrier, and it is wholly unexercised in Azure.
- **Lifting `maxReplicas: 1`.** Publishing a data path does not make open mode safe on N replicas.
  That stays gated on `--bundle-mode` — see `devdocs/azure/utlxe-deployment-modes.md` §9.
  (`devdocs/` is not merged to `main`, so that is a path rather than a link.)

## Estimate

Engine route and tests, half a day. nginx and Dockerfile, an hour. Templates and wizards in both
plans, half a day including an arm-ttk round trip. Acceptance assertions, two hours. Documentation,
half a day. Call it **two to three days** including one real deployment — with the caveat that the
1.4.4 package has not been submitted yet, so this could ship as 1.5.0 rather than as a patch on
top of an unpublished release.
