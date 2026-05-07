# EF06: Dapr Pub/Sub and Binding Strategy

**Status:** Design — architectural decision needed  
**Priority:** High (shapes the Marketplace offering)  
**Created:** May 2026  
**Depends on:** EF03 (Admin API), EF04 (message tracing), EF05 (Dapr fixes)

---

## Summary

UTLXe currently uses Dapr input/output **bindings** for Azure Service Bus integration. This works but creates a static, one-component-per-queue model that conflicts with UTLXe's dynamic "Swiss Army knife" positioning. This document analyzes the full Dapr integration strategy: bindings vs pub/sub, the Marketplace packaging implications, and the tension between generic engine and Azure-specific offering.

## The Tension

UTLXe is designed as a **generic transformation engine** — deploy once, upload any transformation at runtime. But the Dapr integration creates Azure-specific configuration that the customer must manage outside of UTLXe:

| What UTLXe controls (dynamic) | What Dapr controls (static) |
|-------------------------------|----------------------------|
| Which transformations are loaded | Which queues/topics are listened to |
| Transformation logic (.utlx) | Connection strings, queue names |
| Output binding name | Output queue/topic configuration |
| Hot-swap, pause/resume | Requires container restart to change |

The transformation is dynamic. The plumbing is static. The customer manages two systems with different lifecycles.

## Azure Service Bus: Two Messaging Patterns

Azure Service Bus offers two fundamentally different patterns:

### Queues (point-to-point)

```
Producer ──> Queue ──> ONE Consumer
```

- Message consumed once, then gone
- Competing consumers (multiple instances share the queue)
- Use case: "Process this order" — exactly one handler
- Simpler, very common for integration

### Topics + Subscriptions (publish/subscribe)

```
Producer ──> Topic ──> Subscription A ──> Consumer A
                   ──> Subscription B ──> Consumer B
                   ──> Subscription C ──> Consumer C
```

- Each subscription gets its own copy of every message
- Filtering via subscription rules
- Use case: "Notify everyone about this order" — fan-out
- More flexible, but more infrastructure to manage

## Dapr Mapping

| Azure concept | Dapr building block | Component type | Components needed |
|---|---|---|---|
| Queue (input) | Binding | `bindings.azure.servicebusqueues` | One per queue |
| Queue (output) | Binding | `bindings.azure.servicebusqueues` | One per queue |
| Topic (input, single consumer) | Binding | `bindings.azure.servicebustopics` | One per topic |
| Topic (input, fan-out) | Pub/Sub | `pubsub.azure.servicebus.topics` | ONE for all topics |
| Topic (output) | Pub/Sub | `pubsub.azure.servicebus.topics` | ONE for all topics |

Key difference:
- **Bindings**: one component per queue/topic — static, customer manages N components
- **Pub/Sub**: one component for the entire namespace — UTLXe controls subscriptions programmatically via `GET /dapr/subscribe`

## Pub/Sub: How UTLXe Controls Subscriptions

With Dapr pub/sub, the app implements `GET /dapr/subscribe` and returns a list of subscriptions. Dapr calls this once at startup:

```json
GET /dapr/subscribe

[
  {
    "pubsubname": "utlxe-servicebus",
    "topic": "incoming-orders",
    "route": "/orders-in"
  },
  {
    "pubsubname": "utlxe-servicebus",
    "topic": "incoming-invoices",
    "route": "/invoices-in"
  }
]
```

UTLXe builds this list from loaded transformations — each transformation that declares a `topic` in its config generates a subscription entry. The subscription list is **derived from the transformation bundle**, not from Dapr YAML.

**Limitation:** Dapr calls `/dapr/subscribe` once at sidecar startup. Adding a new topic subscription after startup requires a sidecar restart. Updating or removing a transformation for an EXISTING topic is instant (hot-swap, no restart).

## The Three Customer Scenarios

### Scenario A: Queue-to-Queue (simplest)

```
Service Bus Queue ──> UTLXe ──> Service Bus Queue
```

- Customer has point-to-point queues
- Must use Dapr **bindings** (one per queue)
- Each new queue = new Dapr component + restart
- Static, but matches the customer's static queue topology

### Scenario B: Topic-to-Topic (recommended for Marketplace)

```
Service Bus Topic ──> UTLXe ──> Service Bus Topic
```

- Customer uses topics for flexibility
- Uses Dapr **pub/sub** (one component for all topics)
- UTLXe controls subscriptions via `/dapr/subscribe`
- New topic = upload transformation + restart (Dapr re-queries subscriptions)
- More dynamic, better fit for UTLXe

### Scenario C: Direct HTTP (no queues, no Dapr messaging)

```
HTTP Client ──> UTLXe ──> HTTP Response
```

- Synchronous request/response
- No Dapr messaging involved
- Works out of the box, no configuration needed

## Marketplace Packaging

### Option 1: Single offering, both patterns documented

ONE Marketplace listing with documentation for both queues and topics:

```
Azure Marketplace: "UTLXe — Data Transformation Engine"
  Includes:
    - UTLXe container
    - Dapr sidecar (enabled)
    - Azure Files (optional persistence)
  Customer configures:
    - Dapr components (bindings for queues, pub/sub for topics)
    - Transformations (via Admin API)
```

**Pro:** One product, one price, maximum flexibility.
**Con:** Customer must understand Dapr components. More documentation needed. The "Swiss Army knife" requires the customer to assemble the knife.

### Option 2: Two offerings (Queue edition + Topic edition)

Two Marketplace listings with pre-configured Dapr components:

```
"UTLXe Starter — Queue Processing"
  Pre-configured: Dapr binding component for Service Bus queues
  Customer provides: connection string, queue names
  Best for: simple point-to-point integration

"UTLXe Professional — Topic Processing"
  Pre-configured: Dapr pub/sub component for Service Bus topics
  Customer provides: connection string
  UTLXe manages subscriptions dynamically
  Best for: multi-topic, fan-out scenarios
```

**Pro:** Simpler customer experience — less Dapr knowledge needed. Clear value proposition per tier.
**Con:** Two products to maintain. Customer locked into one pattern. The generic story is diluted.

### Option 3: Single offering with guided setup (RECOMMENDED)

ONE Marketplace listing. The `createUiDefinition.json` wizard asks the customer which pattern they use:

```
Step 1: Choose your messaging pattern
  ○ Queue-to-Queue (Azure Service Bus Queues)
  ○ Topic-to-Topic (Azure Service Bus Topics)  [recommended]
  ○ Direct HTTP only (no message queues)

Step 2: Provide your Service Bus connection
  Connection string: [________________]

Step 3: Persistent storage
  ☐ Enable persistent transformation storage
```

The Bicep template deploys different Dapr components based on the choice:
- Queue → binding components (customer specifies queue names in step 2)
- Topic → pub/sub component (UTLXe controls subscriptions)
- Direct HTTP → no Dapr messaging components

**Pro:** One product, guided experience, customer chooses at deploy time.
**Con:** Bicep template is more complex (conditional resources).

## The Transformation Config

Regardless of which pattern, the transformation declares its messaging intent:

```yaml
# transform.yaml — for queue-based (bindings)
inputBinding: "orders-in"           # Dapr binding component name
outputBinding: "orders-out"         # Dapr output binding name

# transform.yaml — for topic-based (pub/sub)
input:
  pubsub: "utlxe-servicebus"       # Dapr pub/sub component name
  topic: "incoming-orders"          # Service Bus topic name
output:
  pubsub: "utlxe-servicebus"
  topic: "processed-orders"
```

UTLXe detects which model is used (binding vs pub/sub) from the transformation config and routes accordingly.

## What Changes in UTLXe

### For pub/sub support (new)

| Component | Change |
|-----------|--------|
| `HttpTransport.kt` | Implement `GET /dapr/subscribe` — return subscription list from loaded transformations |
| `HttpTransport.kt` | Handle `POST /{route}` for pub/sub delivery (CloudEvents envelope — unwrap before transforming) |
| `TransformConfig.kt` | Add `pubsub` and `topic` fields alongside existing `outputBinding` |
| `HttpTransport.kt` | Output via `POST localhost:3500/v1.0/publish/{pubsub}/{topic}` for pub/sub output |

### For bindings (existing, fix only)

| Component | Change |
|-----------|--------|
| `HttpTransport.kt` | EF05 fixes: OPTIONS handler, root path registration, metadata headers |

### CloudEvents handling

Dapr pub/sub wraps messages in CloudEvents 1.0 envelope:

```json
{
  "specversion": "1.0",
  "type": "com.servicebus.topic",
  "source": "/utlxe-servicebus/incoming-orders",
  "id": "uuid-...",
  "datacontenttype": "application/json",
  "data": { "orderId": "ORD-001", ... }
}
```

UTLXe must unwrap the `data` field before passing to the transformation. The CloudEvents metadata (`id`, `source`, `type`) can feed into the messaging triad (EF04):
- CloudEvents `id` → `MessageId` (if not already set)
- CloudEvents `source` → metadata for logging

## The Honest Assessment

### What we gain

- **Pub/sub**: one Dapr component for all topics — dramatically simpler for the customer
- **Programmatic subscriptions**: UTLXe controls what it listens to — closer to the "Swiss Army knife" vision
- **Guided setup wizard**: customer doesn't need to understand Dapr — they pick a pattern and provide a connection string

### What we lose

- **Generality**: the Marketplace offering is now Azure Service Bus specific. The Dapr portability story ("swap to Kafka by changing YAML") is still true but hidden behind the wizard.
- **Simplicity**: supporting both bindings AND pub/sub means two code paths, two test suites, two documentation sets
- **Focus**: we started with a generic transformation engine and are now building Azure-specific plumbing

### The philosophical question

UTLXe the engine is generic. UTLXe the Azure Marketplace offering is specific. These are different products with different customers:

| | UTLXe Engine | UTLXe Azure Offering |
|---|---|---|
| Customer | Developer with integration need | Azure customer buying from Marketplace |
| Knows about | Transformations, data formats | Azure Service Bus, Container Apps |
| Expects | Flexibility, any transport | Works out of the box with Azure services |
| Dapr knowledge | Optional | Zero (hidden by the wizard) |

The Marketplace offering wraps the generic engine in Azure-specific packaging. This is not a compromise — it's a product decision. The engine stays generic (gRPC, stdio, HTTP, any Dapr component). The offering is specific (Azure Service Bus, guided wizard, pre-configured Dapr).

## Deployment Order (Corrected)

With pub/sub, the deployment order becomes cleaner:

```
1. Customer deploys from Marketplace
   → Bicep creates: UTLXe + Dapr pub/sub component + Azure Files
   → Dapr connects to Service Bus namespace (not specific topics yet)
   → UTLXe starts, /dapr/subscribe returns [] (no subscriptions)
   → Container running, no messages flowing

2. Customer uploads transformations via Admin API
   → POST /admin/bundle with transformations declaring topics
   → UTLXe compiles, persists to Azure Files
   → /dapr/subscribe now returns [{"topic":"incoming-orders",...}]

3. Customer restarts container (one time)
   → az containerapp revision restart
   → UTLXe loads from Azure Files
   → Dapr calls /dapr/subscribe → gets subscription list
   → Dapr subscribes to declared topics
   → Messages flow

4. Update transformation (no restart)
   → Upload new .utlx for existing topic → hot-swap, instant

5. Add NEW topic (restart needed)
   → Upload transformation declaring new topic → restart
   → Dapr re-queries /dapr/subscribe → picks up new topic
```

For **queues** (bindings), steps 1-3 are different:
```
1. Customer deploys from Marketplace
2. Customer uploads transformations via Admin API
3. Customer adds Dapr binding components via Azure CLI (one per queue)
   → az containerapp env dapr-component set ...
   → Triggers restart
   → Dapr probes OPTIONS → UTLXe has transformations → bindings active
```

## Effort Estimate

| Task | Effort |
|------|--------|
| `GET /dapr/subscribe` endpoint (derive from loaded transformations) | 1 day |
| CloudEvents envelope unwrapping for pub/sub input | 1 day |
| Pub/sub output (`POST :3500/v1.0/publish/{pubsub}/{topic}`) | 0.5 day |
| TransformConfig: pubsub/topic fields | 0.5 day |
| Bicep template: conditional Dapr components (queue vs topic wizard) | 1 day |
| createUiDefinition.json: messaging pattern choice | 0.5 day |
| Tests (pub/sub + bindings, local Redis + Service Bus) | 2 days |
| Documentation (book, architecture docs, SDK examples) | 1.5 days |
| **Total** | **~8 days** |

## Open Questions

1. **Should the Marketplace wizard default to topics or queues?** Topics are more flexible but less familiar to simple integration scenarios.

2. **~~Can we avoid the restart for new topic subscriptions?~~** **RESOLVED:** Yes. Dapr Component Hot Reload (`HotReload` feature gate, preview since v1.13, improved in v1.17) watches the `--resources-path` directory for file changes. UTLXe can write/delete binding YAML files at runtime → Dapr picks them up within ~1 second. For pub/sub, streaming subscriptions (v1.14+, alpha, gRPC) allow dynamic subscribe/unsubscribe from code without any YAML. See EF10 for the full design.

3. **Should we support Kafka and Event Hub in the wizard too?** Or keep it Service Bus only for v1 and document others as "advanced, bring your own Dapr component"?

4. **Does the pub/sub CloudEvents wrapping break the format-agnostic story?** The transformation receives unwrapped `data` — it doesn't see CloudEvents. But the wrapping adds latency (parse envelope → extract data → parse data).

---

*Feature EF06. May 2026. Design document.*
*Key insight: Dapr bindings are per-queue (static). Dapr pub/sub is per-namespace (dynamic subscriptions). For the Marketplace "Swiss Army knife" offering, pub/sub is the better fit — but queues must also be supported.*
