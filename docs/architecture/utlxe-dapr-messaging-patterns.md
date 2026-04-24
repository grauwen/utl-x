# UTLXe Dapr Messaging Patterns

**How UTL-X integrates with Azure Service Bus, Event Hubs, and other message brokers via Dapr**  
*Version 1.0 — April 2026*

---

## 1. Overview

UTLXe supports event-driven messaging through Dapr input/output bindings. Dapr runs as a managed sidecar in Azure Container Apps — UTLXe receives messages as HTTP calls from the sidecar and optionally forwards transformed results to an output binding.

```
Message Broker (Service Bus / Event Hub / Kafka)
    │
    ▼
Dapr Sidecar (managed by Azure Container Apps)
    │ POST /api/dapr/input/{bindingName}
    ▼
UTLXe HTTP Transport
    │ Transform using pre-loaded .utlx
    │ (TEMPLATE / COPY / COMPILED strategy)
    ▼
Dapr Sidecar
    │ POST http://localhost:3500/v1.0/bindings/{outputBinding}
    ▼
Output Broker (Service Bus topic / Event Hub / Blob Storage)
```

UTLXe does not contain any broker SDK code. Dapr handles all broker-specific communication. UTLXe only sees HTTP requests.

---

## 2. Binding-to-Transformation Mapping

**Convention: Dapr binding name = UTLXe transformation ID.**

When Dapr receives a message from a binding named `order-queue`, it calls:

```
POST /api/dapr/input/order-queue
```

UTLXe looks up transformation `order-queue` in its registry and executes it.

**Override via header:** If the transformation has a different name than the binding, set the `X-UTLXe-Transform` header in the Dapr component metadata:

```
X-UTLXe-Transform: normalize-order
```

This routes messages from binding `order-queue` to transformation `normalize-order`.

---

## 3. Messaging Patterns

### Pattern 1: Single Input, Single Output (most common)

One message arrives on a queue. UTLXe transforms it. Result goes to an output topic.

```
Service Bus Queue: "orders-in"
    │ {"orderId": "ORD-001", "customer": "Contoso", "amount": 1500}
    ▼
UTLXe: transformation "orders-in"
    │ %utlx 1.0
    │ input json
    │ output json
    │ ---
    │ {
    │   invoiceId: concat("INV-", $input.orderId),
    │   customer: upperCase($input.customer),
    │   total: $input.amount * 1.21
    │ }
    ▼
Service Bus Topic: "invoices-out"
    {"invoiceId": "INV-ORD-001", "customer": "CONTOSO", "total": 1815.0}
```

**Setup:**
1. Load transformation at startup: `POST /api/load` with `transformationId: "orders-in"`
2. Or use `--bundle` with transformation directory named `orders-in`
3. Output binding defined in `transform.yaml`:

```yaml
# transformations/orders-in/transform.yaml
strategy: TEMPLATE
outputBinding: "invoices-out"    # result forwarded to this Dapr output binding
```

**Output binding resolution (priority order):**
1. Environment variable: `UTLXE_OUTPUT_BINDING_ORDERS_IN=invoices-prod` (operator override at deployment)
2. Transform config: `outputBinding: "invoices-out"` in `transform.yaml` (developer default)
3. HTTP header: `X-UTLXe-Output-Binding: invoices-out` (per-request override)
4. None: result returned in HTTP response only (no forwarding)

### Pattern 2: Multi-Input via Envelope

A transformation that needs multiple inputs (e.g., order + customer data) receives a single message containing all inputs packed as a JSON envelope.

```
Service Bus Queue: "enrichment-in"
    │ {
    │   "order": {"orderId": "ORD-001", "items": [...]},
    │   "customer": {"name": "Contoso", "tier": "Gold", "creditLimit": 25000}
    │ }
    ▼
UTLXe: transformation "enrichment-in"
    │ %utlx 1.0
    │ input: order json, customer json
    │ output json
    │ ---
    │ {
    │   orderId: $order.orderId,
    │   customerName: $customer.name,
    │   discount: if ($customer.tier == "Gold") 0.15 else 0.05,
    │   items: $order.items
    │ }
    ▼
Service Bus Topic: "enriched-orders"
```

**How it works:** The sender (upstream pipeline step) packs all inputs into one JSON envelope with keys matching the input names declared in the `.utlx` header. UTLXe's multi-input envelope parsing splits the message and feeds each part to the transformation.

**Who packs the envelope?** The upstream system — not UTLXe. This is the sender's responsibility. In Open-M, the Go wrapper builds the envelope from the MPPM pipeline state.

### Pattern 3: Format Conversion

Messages on the queue are XML (e.g., from a legacy system). UTLXe transforms to JSON for downstream consumption.

```
Service Bus Queue: "legacy-orders" (XML messages)
    │ <Order><Id>ORD-001</Id><Customer>Contoso</Customer></Order>
    ▼
UTLXe: transformation "legacy-orders"
    │ %utlx 1.0
    │ input xml
    │ output json
    │ ---
    │ { orderId: $input.Order.Id, customer: $input.Order.Customer }
    ▼
Service Bus Topic: "modern-orders" (JSON messages)
    {"orderId": "ORD-001", "customer": "Contoso"}
```

**Supported input formats on Dapr bindings:** JSON, XML, CSV, YAML — any format UTLXe supports. The `Content-Type` header from Dapr tells UTLXe how to parse the payload. If not provided, the `.utlx` header's declared input format is used.

### Pattern 4: Request/Reply

**Use HTTP, not message queues.** Dapr bindings are fire-and-forget — there is no native request/reply on Service Bus via Dapr.

For synchronous request/reply:

```
Client Application
    │ POST /api/transform (or /api/execute/{id})
    │ {payload, utlxSource}
    ▼
UTLXe HTTP Transport
    │ Transform
    ▼
HTTP Response
    {output}
```

Use the HTTP endpoints (`/api/transform`, `/api/execute/{id}`) for request/reply. Use Dapr bindings (`/api/dapr/input/{binding}`) for async fire-and-forget processing.

### Pattern 5: Fan-Out (one input, multiple outputs)

A single message needs to be transformed into multiple formats for different consumers.

```
Service Bus Queue: "raw-events"
    │ {"event": "order-placed", "data": {...}}
    ▼
UTLXe: transformation "raw-events"
    │ Transforms to JSON
    ▼
Output 1: Service Bus Topic "events-json" (via Dapr output binding)
Output 2: Blob Storage "events-archive" (via second Dapr output binding)
```

**Current limitation:** UTLXe sends to one output binding per message (via `X-UTLXe-Output-Binding` header). For fan-out, use a Service Bus topic with multiple subscriptions — one transformation writes to the topic, multiple subscribers consume.

---

## 4. Strategy Selection for Messaging

All execution strategies work with Dapr messaging:

| Strategy | When to use with messaging |
|----------|--------------------------|
| **TEMPLATE** | Default. No schema needed. Good for development and varied message formats. |
| **COPY** | When input schema is known at deployment time. Pre-builds UDM skeleton. Faster for high-volume queues with consistent message structure. |
| **COMPILED** | When maximum throughput is needed. Compiles .utlx to JVM bytecode. Best for sustained high-volume processing. |
| **AUTO** | Schema provided → COPY (auto-compiles to bytecode). No schema → TEMPLATE. |

### Using COPY/COMPILED with schemas

Load the transformation with schema via `/api/load` or `--bundle`:

**Via API (at startup or init container):**

```json
POST /api/load
{
  "transformationId": "order-queue",
  "utlxSource": "%utlx 1.0\ninput json\noutput json\n---\n...",
  "strategy": "COPY",
  "config": {
    "validate_input": "true",
    "input_schema": "{\"type\":\"object\",\"required\":[\"orderId\"],\"properties\":{...}}",
    "input_schema_format": "json-schema"
  }
}
```

**Via bundle (`--bundle /path/to/bundle`):**

```
my-bundle/
├── transformations/
│   └── order-queue/              ← name matches Dapr binding
│       ├── transform.yaml        ← strategy + schema config
│       └── order-queue.utlx      ← transformation
└── engine.yaml
```

```yaml
# transformations/order-queue/transform.yaml
strategy: COPY
validationPolicy: WARN
inputs:
  - name: input
    schema: schemas/order-v1.json
outputBinding: "orders-processed"    # Dapr output binding (optional)
maxConcurrent: 8
```

**Environment variable override at deployment:**
```bash
# In Bicep or docker-compose, override output binding per environment
UTLXE_OUTPUT_BINDING_ORDER_QUEUE=orders-processed-prod
```

This lets operators rewire output destinations without touching the bundle.

The schema is compiled once at startup. Every message from Service Bus benefits from the pre-built skeleton — no per-message schema parsing.

---

## 5. Deployment: Bundle + Dapr

The recommended production deployment pattern:

```bash
utlxe --mode http --bundle /utlxe/bundle
```

This combines:
- **`--bundle`**: loads transformations + schemas at startup (init-time compilation, skeleton building)
- **`--mode http`**: serves Dapr input binding requests + direct HTTP API calls

```
┌─────────────────────────────────────────────────────┐
│ Azure Container App                                  │
│                                                      │
│  ┌────────────┐     ┌──────────────────────────┐    │
│  │ Dapr       │────→│ UTLXe (--mode http       │    │
│  │ Sidecar    │     │         --bundle /bundle) │    │
│  │            │←────│                           │    │
│  └────────────┘     │ Port 8085: HTTP API       │    │
│        ↑            │ Port 8081: Health/Metrics  │    │
│        │            │                           │    │
│  Service Bus        │ Pre-loaded at startup:    │    │
│  queue/topic        │  - order-queue (COPY)     │    │
│                     │  - invoice-queue (COMPILED)│    │
│                     └──────────────────────────┘    │
└─────────────────────────────────────────────────────┘
```

The bundle directory is mounted as a volume in the Docker container or baked into the image at build time.

### Docker with bundle:

```dockerfile
FROM ghcr.io/utlx-lang/utlxe:latest
COPY my-bundle/ /utlxe/bundle/
CMD ["--mode", "http", "--bundle", "/utlxe/bundle"]
```

### Bicep with volume mount:

```bicep
// Mount Azure Files share as bundle volume
containers: [{
  name: 'utlxe'
  image: containerImage
  args: ['--mode', 'http', '--bundle', '/utlxe/bundle']
  volumeMounts: [{
    volumeName: 'bundle'
    mountPath: '/utlxe/bundle'
  }]
}]
volumes: [{
  name: 'bundle'
  storageName: 'bundle-share'
  storageType: 'AzureFile'
}]
```

---

## 6. What UTLXe Does NOT Do

| Concern | Who handles it | Not UTLXe |
|---------|---------------|-----------|
| Reading from Service Bus | Dapr sidecar | UTLXe only sees HTTP |
| Writing to Service Bus | Dapr sidecar | UTLXe calls Dapr's HTTP API |
| Message retry/dead-letter | Service Bus + Dapr | UTLXe returns 200 (success) or 500 (fail) — Dapr decides retry |
| Message ordering | Service Bus sessions | UTLXe processes whatever arrives |
| Correlation across queues | Upstream pipeline (e.g., Open-M) | UTLXe transforms one message at a time |
| Schema registry | External (Confluent, Azure Schema Registry) | UTLXe loads schemas from bundle or /api/load config |
| Authentication to broker | Dapr + Managed Identity | UTLXe has no broker credentials |
| Scaling decisions | KEDA + Azure Container Apps | UTLXe doesn't know about queue depth |

UTLXe is the transformation engine. Everything else is infrastructure.

---

## 7. Error Handling

| Scenario | UTLXe response | Dapr behavior |
|----------|---------------|---------------|
| Transformation succeeds | HTTP 200 | Message acknowledged, removed from queue |
| Transformation fails (bad data) | HTTP 500 | Message NOT acknowledged — Dapr retries (per Service Bus delivery count) |
| Transformation not found | HTTP 500 | Retry — may succeed after init |
| UTLXe not ready (starting up) | Connection refused | Dapr retries until readiness probe passes |
| Payload too large (>10MB) | HTTP 413 | Message dead-lettered after max retries |

For STRICT validation policy: schema validation failure → HTTP 500 → message retried → eventually dead-lettered. This is correct — an invalid message should not be silently dropped.

For WARN validation policy: schema validation warnings logged, transformation proceeds → HTTP 200 → message acknowledged.

---

*Dapr messaging patterns for UTL-X. April 2026.*
