# UTLXe Marketplace Offer — Deployment Modes

**Document purpose:** Describe the four shapes the Azure offering can run in, show
which one it ships in by default and why that one is unsound, and propose making the
shape a declared `deploymentMode` parameter instead of an emergent property of four
independent knobs that an operator has to get into agreement by hand.

**Status:** Proposal. Nothing in §7–§10 is implemented. §1–§6 describe shipped
behaviour as of 1.4.4 and are verifiable against the sources cited.

**Companion documents:**
- `utlxe-ingress-and-access-patterns.md` — whether the ingress is public. Orthogonal
  to this document: every mode below works public or private.
- `utlxe-reaching-the-webadmin.md` — reaching the console once deployed.
- `deploy/azure-marketplace/README.md` — release and certification procedure.
  (`deploy/` is not merged to `main`, so that is a path rather than a link.)
- `books/engine-azure` ch10 — currently documents shape C only, and should gain the
  table in §5.

---

## 1. Two knobs decide the shape

Everything below follows from the interaction of two settings that are specified in
different places and never checked against each other.

**Replica count** is an ARM parameter. Both plans ship `minReplicas: 1`
(`maxValue: 10`) and `maxReplicas: 5` (`maxValue: 30`).

**Bundle mode** is *not* a parameter. It is detected at startup from the contents of
the Azure Files share:

```kotlin
// BundleMode.kt:44
fun detectBundleMode(dataDirPath: Path?): BundleInfo
//   exactly one *.utlar  -> "locked"
//   several   *.utlar    -> logs an error, uses the first
//   none                 -> "open"
```

Two further facts make the combination matter:

- The transformation registry is **per process** — a `ConcurrentHashMap` in
  `TransformationRegistry`, not shared state.
- The share is read **once**. `UtlxEngine.scanDataDir` carries the comment *"Called
  at startup before the transport starts"*, and `WatchService`, `inotify` and
  `FileAlterationMonitor` return zero matches across every `.kt` file in the repo.
  There is no reconciliation between a running replica and the share.

So:

```
                     |  1 replica            |  N replicas
  -------------------+-----------------------+---------------------------
   open   (no .utlar)|  A  sound - SHIPPED   |  C  UNSOUND - unreachable
   locked (a .utlar) |  B' sound, pointless  |  B  sound, not yet declarable
```

Until 1.4.4 the shipped default was `1 … 5` with a share that ARM creates **empty**,
hence open mode — which landed in **C**, the only quadrant that can give two different
answers to the same question.

**As of 1.4.4 the offering ships shape A.** `maxReplicas` is pinned with
`defaultValue: 1, minValue: 1, maxValue: 1` in both plans and both wizards, and
`minReplicas` is capped at 1 so it cannot exceed the maximum. The right-hand column is
therefore unreachable from the Marketplace *and* from a direct ARM deployment of these
templates. Shapes B and D stay documented here because the pin is a deliberate
simplification, not a verdict on them — lifting it is gated on §9's `--bundle-mode`,
without which N replicas falls back into C rather than reaching B.

---

## 2. Shape A — one app, one replica, open mode

```
                            Internet
                               |
                               v
              +--------------------------------------+
              |  Container Apps ingress              |
              |  external: true   targetPort: 8088   |
              +--------------------------------------+
                               |
  +============================|======================================+
  |  REPLICA 1 of 1                 declared: 2.25 vCPU / 4.5 GiB     |
  |                               v                                   |
  |   +---------------------------+---+                               |
  |   | utlxe-ui   nginx:alpine       |  0.25 vCPU / 0.5 GiB          |
  |   |   /         -> static         |                               |
  |   |   /admin/   -> :8081  --------+--+                            |
  |   |   /health   -> :8081  --------+--|                            |
  |   |   /metrics  -> :8081  --------+--|                            |
  |   +-------------------------------+  | via 127.0.0.1              |
  |                                      | (containers of a replica   |
  |                                      |  share one netns)          |
  |                                      v                            |
  |   +------------------------------------------------+              |
  |   | utlxe   JVM, ZGC, -Xmx3072m                    |              |
  |   |                           2.0 vCPU / 4.0 GiB   |              |
  |   |   :8081  admin + health                        |              |
  |   |   :8085  data plane   UNPUBLISHED  <-----------+---+          |
  |   |   :9090  gRPC         not started              |   |          |
  |   |   registry: in-memory, PER PROCESS             |   |          |
  |   +---------------------+--------------------------+   |          |
  |                         | /utlxe/data                  |          |
  |                         |                 POST /<transformation>   |
  |   +---------------------|------------------------------+--+       |
  |   | daprd                                                 |       |
  |   |   INJECTED BY THE PLATFORM - not listed in            |       |
  |   |   template.containers[], present only when            |       |
  |   |   enableDapr = true                                   |       |
  |   |                                                       |       |
  |   |   appId  utlxe-app   appPort 8085   appProtocol http   |       |
  |   |   pulls  servicebus-input   queue binding, 8 handlers |       |
  |   |   pushes servicebus-output  topic binding             |       |
  |   +------------------+------------------------------------+       |
  +======================|==============|=============================+
                         |              |
                         v              v
          Azure Service Bus      Azure Files  utlxe-data
          queue in / topic out   100 GiB, read ONCE at startup,
                                 written on every admin deploy
```

**What it is.** One engine process. nginx is both the console and the app's only front
door; it reaches the engine over loopback because the containers of a replica share one
network namespace. `127.0.0.1` rather than `localhost` because the engine binds the
IPv4 wildcard and nginx resolves upstreams at config-parse time.

**Throughput.** One JVM. Netty sizes its own pool from the cgroup CPU quota;
`TransformConfig.maxConcurrent` defaults to `0` (unlimited, `TransformConfig.kt:15`),
so in-flight work is bounded only by CPU.

**Correctness.** Exact. One registry, so the console shows precisely what the data
plane will execute. Reading the share only at startup is harmless when the writer and
the reader are the same process.

**Failure mode.** Total outage on any restart — revision update, node maintenance, an
OOM on an oversized payload. No redundancy.

**Settings.** `minReplicas: 1`, `maxReplicas: 1`, `enablePersistentStorage: true`,
share holds a `transformations/` directory and no `.utlar`.

**Use for** evaluation, development, and customers whose volume fits one replica and
who accept a restart window.

---

## 3. Shape B — one app, N replicas, locked mode

```
         Azure Service Bus   queue utlx-input
         +----------------------------------------------+
         |  msg  msg  msg  msg  msg  msg  msg  msg      |
         +---+--------------+--------------+------------+
             |              |              |
             |   COMPETING CONSUMERS - one daprd per replica,
             |   each pulling independently. Dapr is NOT a
             |   shared singleton.
             v              v              v
  +---------------+ +---------------+     +---------------+
  | REPLICA 1     | | REPLICA 2     | ... | REPLICA 5     |
  |               | |               |     |               |
  | daprd         | | daprd         |     | daprd         |
  |  maxConcurr-  | |  maxConcurr-  |     |  maxConcurr-  |
  |  entHandlers 8| |  entHandlers 8|     |  entHandlers 8|
  |      |        | |      |        |     |      |        |
  |      v :8085  | |      v :8085  |     |      v :8085  |
  | utlxe         | | utlxe         |     | utlxe         |
  |  hello v3     | |  hello v3     |     |  hello v3     | <- IDENTICAL
  |  order v3     | |  order v3     |     |  order v3     |    by construction
  |  admin -> 403 | |  admin -> 403 |     |  admin -> 403 |
  | utlxe-ui      | | utlxe-ui      |     | utlxe-ui      |
  +-------+-------+ +-------+-------+     +-------+-------+
          |                 |                     |
          +--------+--------+---------------------+
                   |  read-only, at startup
                   v
       +-------------------------------+
       |  Azure Files  utlxe-data      |
       |    bundle-v3.utlar  <---------+-- the single source of truth
       +-------------------------------+
         detectBundleMode finds .utlar -> mode = "locked"
         9 structural admin endpoints return 403

   ENVIRONMENT-SCOPED, one definition shared by every replica:
       Microsoft.App/managedEnvironments/daprComponents  servicebus-input
       Microsoft.App/managedEnvironments/daprComponents  servicebus-output
       scopes: ["utlxe-app"]   <-- hardcoded; see section 6

   message concurrency: 5 replicas x 8 handlers = up to 40 in flight
   billed:              5 x 2.25 = 11.25 vCPU / 22.5 GiB (daprd on top)
   deploy:              replace the .utlar, restart the revision
```

**What it is.** The transformation set is an immutable artefact. Every replica loads the
same `.utlar` at boot, and nine structural admin endpoints return `lockedResponse`
(§10), so divergence is impossible rather than merely unlikely.

**Throughput.** Close to linear in concurrent requests; each replica is an independent
engine. Service Bus distributes messages across consumers by itself.

**Cost.** Five nginx sidecars — 1.25 vCPU / 2.5 GiB of pure overhead for 45 KB of
JavaScript — five JVMs JIT-compiling identical code, and **five 3072 MB heaps instead
of one larger one**. Scaling out raises concurrent capacity and does nothing for the
largest single payload, because one transformation runs in one process.

**Settings.** `minReplicas: 2`, `maxReplicas: 5`, `enablePersistentStorage: true`,
share holds exactly one `.utlar`.

**Use for** production with an availability requirement, or volume beyond one replica.
This is the only sound multi-replica shape today.

---

## 4. Shape C — one app, N replicas, open mode *(unreachable as of 1.4.4; was the default)*

```
                            Internet
                               |
              +----------------+----------------+
              |  ingress, no session affinity   |
              +----+-----------------------+----+
                   |                       |
                   v                       v
  +----------------------------+  +----------------------------+
  | REPLICA 1   started 09:00  |  | REPLICA 2   started 14:00  |
  |                            |  |                            |
  | registry:                  |  | registry:                  |
  |   hello    <- from share   |  |   hello                    |
  |   invoice  <- POSTed 11:20 |  |   (no invoice)             |
  |   (no order)               |  |   order  <- on share 13:00 |
  +------------+---------------+  +------------+---------------+
               |                               |
               | writes invoice                | reads share at ITS startup
               v                               v
          +------------------------------------------+
          |  Azure Files  utlxe-data                 |
          |    transformations/hello/                |
          |    transformations/invoice/   <- 11:20   |
          |    transformations/order/     <- 13:00   |
          +------------------------------------------+

  CONSEQUENCES
    GET  /admin/transformations   answer depends on which replica served it
    POST /api/transform invoice   200 on replica 1, 404 on replica 2
    console refresh               the list changes between refreshes
    a share edit at 13:00         takes effect at 14:00, when KEDA scales out
```

**Why it is unsound.** The registry is per process and the share is read once; nothing
reconciles them. The fleet becomes heterogeneous *over time*, so a change made in the
morning can silently activate in the afternoon under load. There is no revision
history for it and nothing to roll back to.

**Sticky sessions are not the fix.** `ingress.stickySessions.affinity = "sticky"` is
absent from both templates and would be a reasonable addition — it is cookie-based, so
it would stop the console flickering between replicas. It does nothing for a Service
Bus consumer or a server-to-server POST, neither of which carries a cookie. It hides
the symptom and leaves the data plane exactly as it is.

**A file watcher is not the fix either.** It would make every replica's behaviour
change at an unpredictable moment, with no revision boundary and nothing to roll back
to. Rejected deliberately.

**The fix is to leave this quadrant** — cap at one replica (A) or lock the bundle (B).

---

## 5. Shape D — two apps, one environment

Proposed under EF24 for publishing the data plane; it also happens to fix the sidecar
multiplication and the per-replica resource ceiling.

```
                            Internet
                               |
                               v
              +--------------------------------------+
              |  ingress of the UI app, external     |
              |  targetPort: 8088                    |
              +--------------------------------------+
                               |
  +============================|=====================================+
  |  MANAGED ENVIRONMENT  utlxe-env     <-- ONE environment,          |
  |                                         ONE quota slot            |
  |                                         (the 1-per-region limit   |
  |                                          is on ENVIRONMENTS;      |
  |                                          apps per environment are |
  |                                          not the constraint)      |
  |                                                                   |
  |   +--------------------------+                                    |
  |   |  APP  utlxe-ui           |   minReplicas 1 / maxReplicas 1    |
  |   |  nginx only              |   0.25 vCPU / 0.5 GiB TOTAL        |
  |   |  ingress: external :8088 |                                    |
  |   |  proxy_pass -> engine's  |                                    |
  |   |     INTERNAL FQDN :8085  |                                    |
  |   +------------+-------------+                                    |
  |                |  cross-app HTTP over the environment network     |
  |                v                                                  |
  |   +--------------------------+                                    |
  |   |  APP  utlxe-engine       |   minReplicas 2 / maxReplicas 5    |
  |   |  ingress: INTERNAL :8085 |   4.0 vCPU / 8.0 GiB per replica   |
  |   |                          |   -- the full Consumption cap,     |
  |   |                          |      no sidecar taking 0.25        |
  |   +--+--------+--------+-----+                                    |
  |      |        |        |                                          |
  |      v        v        v     <-- internal load balancer           |
  |   +-----+  +-----+  +-----+      NO per-replica addressing:       |
  |   | r1  |  | r2  |  | r3  |      there is no replica-3.internal,  |
  |   +--+--+  +--+--+  +--+--+      and Dapr invocation also         |
  |      |        |        |         load-balances by app id          |
  +======|========|========|=========================================+
         +--------+--------+
                  v
       +-------------------------------+
       |  Azure Files  utlxe-data      |
       |    bundle-v3.utlar            |
       +-------------------------------+
```

**What it buys.**
- One nginx for the whole deployment instead of one per engine replica.
- The engine gets the full **4.0 vCPU**. This matters now: the environment is created
  with no `workloadProfiles`, so it is Consumption-only, which caps a replica at
  4 vCPU / 8 GiB — and `professional` asks for `4.0 + 0.25 = 4.25 vCPU` and
  `8.0 + 0.5 = 8.5 GiB` in one replica. Every example in `utlxe-acceptance.sh` uses
  `./starter/mainTemplate.json`, so the professional plan appears never to have been
  deployed. **Needs verifying before 1.4.4 is submitted.**
- A second ingress, which is what publishing the data plane requires (EF24).
- Dapr belongs on the engine app, and the UI app carries no sidecar — see §6.5.
- No extra quota: one environment, two apps.

**What it costs.**
- Loopback becomes a network hop, with DNS, TLS decisions, and a failure mode that
  does not exist today.
- The container-app section of the template roughly doubles, in both plans. The
  plan-equivalence guard in `build-zips.sh` has already caught two divergences.
- **It cannot be sound in open mode.** The UI's requests cross the engine app's
  internal load balancer, and Container Apps exposes no per-replica address, so the
  console would reach a different engine on each request. D requires locked mode: it
  is B's topology with the sidecar factored out, not an alternative to B.

**Corollary.** A registry in which engine replicas announce themselves to the UI
cannot be built on Container Apps. The UI would hold a list of replicas it has no way
to address individually.

---

### Comparison

|                          | A: 1 replica, open | B: N replicas, locked | C: N replicas, open | D: two apps, locked |
|--------------------------|--------------------|-----------------------|---------------------|---------------------|
| console authoritative    | yes                | n/a                   | **no**              | n/a                 |
| divergence possible      | no                 | no                     | **yes**            | no                  |
| redundant                | no                 | yes                    | yes                 | yes                 |
| sidecar overhead         | 11%                | 11% x N                | 11% x N             | one, fixed          |
| engine CPU ceiling       | 2.0                | 2.0                    | 2.0                 | **4.0**             |
| largest single payload   | one heap           | one heap               | one heap            | **larger heap**     |
| data plane publishable   | no                 | no                     | no                  | **yes**             |
| config change            | live, via console  | new `.utlar` + restart | undefined           | new `.utlar` + restart |
| available today          | **shipped (1.4.4)**    | needs a `.utlar` *and* §9 | **unreachable** | needs EF24          |

---

## 6. The Dapr sidecar — one per replica, not one per deployment

The earlier diagrams omitted it. It matters, because it is the mechanism by which
Service Bus mode scales, and because two of its settings are wrong or frozen.

### 6.1 N replicas means N sidecars

Dapr is not a container you declare. The template asks for it on the app:

```json
"dapr": "[if(parameters('enableDapr'),
    createObject('enabled', true(),
                 'appId',       format('{0}-app', parameters('namePrefix')),
                 'appPort',     8085,
                 'appProtocol', 'http'),
    createObject('enabled', false()))]"
```

and Container Apps injects a `daprd` process **into every replica**. It does not
appear in `template.containers[]`, and there is no shared, singleton Dapr service. So:

```
   component DEFINITIONS   environment-scoped, ONE of each, shared
     Microsoft.App/managedEnvironments/daprComponents   servicebus-input
     Microsoft.App/managedEnvironments/daprComponents   servicebus-output

   component CONSUMERS     one daprd per replica
     replica 1  daprd --+
     replica 2  daprd --+--> both read the same two definitions
     replica 5  daprd --+     and pull the same queue independently
```

Answering the question directly: with two instances there are **two** Dapr sidecars,
reading **one** pair of component definitions.

### 6.2 That is a feature, not waste

The input component is a queue binding:

```
componentType: bindings.azure.servicebus.queues
queueName:     <inputQueueName>
direction:     input
```

Each replica's `daprd` pulls from that queue and POSTs to `localhost:8085` — the
engine's data plane, which is why `appPort` is 8085 and why the data plane needs no
public ingress for Service Bus mode to work. N sidecars on one queue are **competing
consumers**, so message distribution across replicas is Service Bus's own behaviour
and needs no coordination from us. This is the one place where scaling out is cleanly
right, and it pairs with the KEDA rule that scales on queue depth
(`messageCount: 5`, `activationMessageCount: 1`).

It is also the second control path into the engine: `daprd` reaches `:8085` over
loopback with no credential, which is the subject of EF24.

### 6.3 `maxConcurrentHandlers` is the real concurrency knob — and it is frozen at 8

```
  plan          component metadata                value
  ---------------------------------------------------------
  starter       maxConcurrentHandlers              8
  professional  maxConcurrentHandlers              8
```

In Service Bus mode this, not `--workers`, decides how many messages a replica
processes at once. It is **identical in both plans**, so per replica Professional
processes exactly as many concurrent messages as Starter — it merely has more CPU and
heap to do each one with.

Worse, the plan-equivalence guard in `build-zips.sh` normalises only
`workers`, `cpuCores`, `memoryGi` and `maxReplicas`. Any difference in
`maxConcurrentHandlers` would therefore **fail the build**. The guard that exists to
stop the plans drifting is currently preventing them from differing on the one setting
that governs Service Bus throughput. If the plans should differ here, the setting has
to join the `SIZING` exception set.

### 6.4 `scopes` is hardcoded and disagrees with `appId`

Both component definitions carry:

```json
"scopes": ["utlxe-app"]
```

while the appId is computed: `[format('{0}-app', parameters('namePrefix'))]`. Dapr
scopes restrict which appIds may resolve a component, so the two must agree.

They agree **only because `namePrefix` is effectively a constant.** The wizard emits
`"namePrefix": "utlxe"` as a literal, so every Marketplace deployment produces appId
`utlxe-app` and the scope matches. The parameter's `defaultValue` is also `utlxe`.

A direct ARM deployment that passes any other `namePrefix` gets an appId the
components are not scoped to. The expected result is that the components resolve for
nobody: the input binding never delivers and the output binding fails. Nothing in the
template or `build-zips.sh` catches it, and the acceptance script would not either
unless it both changes the prefix and exercises Service Bus.

**This is latent, not shipped-broken** — no Marketplace customer can reach it today.
The fix is one expression:

```json
"scopes": ["[format('{0}-app', parameters('namePrefix'))]"]
```

### 6.5 Where the sidecar goes under shape D

Dapr is configured per app, so in the two-app split it belongs on the **engine** app
(`appPort 8085`), not the UI app. The engine app then carries one `daprd` per engine
replica, and the UI app carries none. That is also what makes D coherent for Service
Bus: the message path never touches the UI.

### 6.6 Open — does the sidecar count against the replica ceiling?

`daprd` is platform-injected and not declared in `template.containers[]`, so it is not
part of the `2.25` / `4.25` vCPU totals computed in §5. Whether Container Apps charges
it against a replica's resource ceiling is platform behaviour that this repository
cannot answer. It matters for exactly one case: `professional` already asks for
`4.25 vCPU / 8.5 GiB` of declared containers against a Consumption cap of 4 / 8, and
if the sidecar counts too, the margin is worse rather than merely negative. Settled by
the same single deployment §13 calls for.

---

## 7. Proposal — make the shape a declared mode

Replace four knobs that must agree with one parameter that sets them.

```
  deploymentMode     replicas   storage   bundle mode   console      shape
  ------------------------------------------------------------------------
  evaluation         1 .. 1     on        auto          editable      A
  production         2 .. N     on        locked        fixed set     B
  custom             operator sets everything                         any
  published (1.5+)   2 .. N     on        locked        fixed set     D
```

In ARM this is a variable fan-out, not a second template:

```json
"modeDefaults": {
  "evaluation": { "minReplicas": 1, "maxReplicas": 1, "bundleMode": "auto"   },
  "production": { "minReplicas": 2, "maxReplicas": 5, "bundleMode": "locked" }
},
"effectiveScale": "[if(equals(parameters('deploymentMode'),'custom'),
     createObject('min', parameters('minReplicas'), 'max', parameters('maxReplicas')),
     variables('modeDefaults')[parameters('deploymentMode')])]"
```

In the wizard: an OptionsGroup, with the replica and sizing fields `visible` only
under `custom`.

Mode is orthogonal to plan, so this is **one parameter on two plans** — not four or
six packages to keep in step. That is the better answer to "should we publish more
offerings for Service Bus and HTTP separately": given that `build-zips.sh` has already
caught two plan divergences, adding packages is the expensive axis and adding a
parameter is the cheap one.

---

## 8. The blocker — mode is detected, not declared

`detectBundleMode` (§1) infers the mode from what is on the share, and **ARM creates
the share empty**. So a deployment the customer selected as "production" would boot in
*open* mode with `maxReplicas: 5` — shape C, the exact quadrant the mode exists to make
unreachable.

ARM cannot close this itself. It has no way to write file content into an Azure Files
share without a `Microsoft.Resources/deploymentScripts` resource — a managed identity
and a helper container — and the customer's bundle is not known at deployment time in
any case.

So enforcement has to live where the share is visible, which is the engine at startup.

---

## 9. The engine change — `--bundle-mode`

Add `--bundle-mode open|locked|auto`, defaulting to `auto`, which is today's
behaviour, so nothing existing changes.

| flag     | `.utlar` present | result                                                     |
|----------|------------------|------------------------------------------------------------|
| `auto`   | yes              | locked *(today)*                                           |
| `auto`   | no               | open *(today)*                                             |
| `locked` | yes              | locked                                                     |
| `locked` | **no**           | **starts, but fails readiness** — no traffic is routed     |
| `open`   | yes              | refuses to start — two contradictory instructions          |

The fourth row is the point of the whole proposal. A `deploymentMode` that only sets
replica counts is a *label*, not a guarantee; the customer picks production, deploys
with an empty share, and silently gets shape C. Failing readiness is the right
response because Container Apps already treats readiness as the routing gate.

Touch points: a flag in `Main.kt` beside `--workers`, a branch in `detectBundleMode`,
and a condition in `HealthEndpoint` next to the `engine.state == RUNNING` check that
EB19 introduced. Readiness is on the path EB19 touched, so this needs
`:modules:engine:test`, not only a compile.

### Bootstrap

The empty-share case has to go somewhere, and this is where:

```
  deploy in production mode
        |
        v
  no .utlar on the share  ->  not ready, no traffic routed, console unreachable
        |
        |  operator supplies the bundle, either:
        |    az storage file upload  ... --source bundle-v3.utlar
        |    or deploy in evaluation mode first and POST /admin/bundle
        |       (allowed in open mode, 403 in locked)
        v
  restart the revision  ->  locked, ready, serving
```

---

## 10. "Locked" is not read-only — the customer text must not say so

Nine **structural** endpoints return `lockedResponse` (`AdminEndpoint.kt:1295`):

```
POST   /admin/transformations/{name}              :149
DELETE /admin/transformations/{name}              :220
POST   /admin/bundle                              :293
DELETE /admin/bundle                              :494
POST   /admin/transformations/{name}/messaging    :673
DELETE /admin/transformations/{name}/messaging    :761
POST   /admin/transformations/{name}/config       :827
POST   /admin/schemas/{filename}                  :1033
DELETE /admin/schemas/{filename}                  :1049
```

These **operational** endpoints stay available in locked mode:

```
POST   /admin/transformations/{name}/pause        :517
POST   /admin/transformations/{name}/resume       :533
POST   /admin/transformations/{name}/test         :251
POST   /admin/bundle/validate                     :579
POST   /admin/sync  and  .../{name}/sync          :948, :917
POST   /admin/transformations/{name}/validation   :1085
DELETE /admin/transformations/{name}/validation   :1109
POST   /admin/log/level                           :1188
DELETE /admin/logs                                :1249
POST   /admin/backpressure                        :1267
```

That is the right boundary — structural changes frozen, operational ones not — but
"read-only console" would be inaccurate. The honest phrasing is *"the transformation
set is fixed; operational controls remain available"*.

---

## 11. What this retires

Three current inaccuracies in the listing resolve as a side effect:

- The replica story is inconsistent, but not in the way it first looks. The starter
  wizard **hardcodes** `maxReplicas: 2`, which agrees with its listing text *"up to 2
  auto-scaling instances"*. The professional wizard reads it from a slider while its
  listing says *"up to 10"*. Both **templates** default to `5`, with `maxValue: 30`.
  So the same plan scales differently depending on whether it was deployed from the
  Marketplace or straight from ARM — the wizard-vs-template inconsistency already
  recorded in `deploy/azure-marketplace/README.md`. Under a mode, one value drives
  both paths.
- The wizard offers a `maxReplicas` slider whose value contradicts that same text.
  Under a mode it is hidden except in `custom`.
- The plan descriptions sell *"8 worker threads"* against *"32 workers"* as the
  difference between the plans. `--workers` is read in exactly two places, both
  `StdioProtoTransport` (`Main.kt:222`, `:237`); `HttpTransport` has no such
  parameter, and the offering runs `--mode http`. The flag is passed, parsed, and
  never read. Nothing is throttled — Netty sizes its pool from the cgroup CPU quota
  and `maxConcurrent` defaults to unlimited — but the number controls nothing, and the
  real difference between the plans is 2.0 vCPU / 3072 MB heap against 4.0 vCPU /
  6144 MB. Tracked separately; a mode does not fix it.

---

## 12. Scope and sequencing

| | work | status |
|---|---|---|
| 1.4.4 | plan descriptions and instance counts rewritten in both wizards; `--workers` dropped from `engineArgs` and the parameter removed from both templates and both wizards | **done** 2026-11-19 |
| 1.4.4 | Dapr `scopes` -> `[format('{0}-app', parameters('namePrefix'))]` in both plans (§6.4) | **done** — latent break removed |
| 1.4.4 | `maxReplicas` pinned to 1 and `minReplicas` capped at 1, in both templates and both wizards; professional's `maxReplicas` slider removed | **done** — shape C is now unreachable |
| 1.4.4 | `build-zips.sh` gains two guards: no `workers` mention, and no hardcoded Dapr scope. Both verified to fail the build when the defect is reintroduced | **done** |
| 1.4.4 | `books/engine-azure` ch02 / ch06 / ch10 / appendix-b corrected; ch06 gains "Which concurrency knob applies" | **done** — 141 pages |
| 1.5.0 | `--bundle-mode` + readiness enforcement + tests | behaviour change at startup and on the readiness path |
| 1.5.0 | `deploymentMode` in both templates and both wizards | the plan-equivalence guard keeps the two in step |
| 1.5.0 | ch10 gains the §5 table and drops its implicit assumption of shape C | documentation only |
| later | shape D, with EF24 | a new resource graph |

The one combination to avoid is shipping `deploymentMode` **without** the engine flag.
A mode that sets replica counts but cannot enforce the bundle is a promise the
deployment is unable to keep.

---

## 13. Open questions

1. **Does `professional` deploy at all?** `4.25 vCPU / 8.5 GiB` per replica against a
   Consumption cap of 4 / 8. Needs one deployment to settle, and it is a packaging bug
   in an unsubmitted release if it fails.
2. **Should `production` default to `minReplicas: 2` or `3`?** Two survives a node
   loss; during a revision update it can briefly be one.
3. **Is `custom` worth exposing in the Marketplace wizard**, or should it stay
   available only to a direct ARM deployment?
4. **Should `maxConcurrentHandlers` differ between the plans?** It is 8 in both
   (§6.3), and the plan-equivalence guard would fail a build that changed one. If
   Professional is meant to process more messages concurrently per replica, the
   setting has to move into the guard's `SIZING` exception set and the plan
   descriptions have to say so.
5. **Does the `daprd` sidecar count against the Consumption replica ceiling?**
   (§6.6) Same deployment settles it as question 1.
6. **Does `evaluation` want `minReplicas: 0`** (scale to zero) to cut idle cost? The
   cold start is JVM boot plus bundle compile — seconds for `COMPILED`.
7. **Should `stickySessions` be enabled unconditionally?** It is strictly an
   improvement for the console and irrelevant to the data plane, so the argument
   against it is only that it makes C look survivable.
