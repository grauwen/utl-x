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
