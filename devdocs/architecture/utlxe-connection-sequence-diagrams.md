# UTLXe Connection Sequence Diagrams

**All supported connection scenarios with Mermaid sequence diagrams**  
*Version 1.1 — May 2026*  
*Added: Diagram 2b — Bundle Management API (EF03)*

---

## 1. HTTP REST API (Direct)

The simplest scenario — a client sends a transformation request via HTTP and receives the result synchronously.

```mermaid
---
config:
  look: handDrawn
  theme: neutral
---
sequenceDiagram
    participant Client
    participant UTLXe
    participant Transform as .utlx Script

    Client->>UTLXe: POST /api/transform<br/>{input: JSON/XML/CSV}
    UTLXe->>Transform: Parse input → UDM
    Transform->>Transform: Execute transformation
    Transform->>UTLXe: Output UDM
    UTLXe->>Client: 200 OK<br/>{output: JSON/XML/CSV}
```

---

## 2. HTTP with Pre-loaded Bundle (--bundle flag)

Self-managed mode — transformations are baked into the image or mounted from a volume. The `--bundle` flag points to a local directory.

```mermaid
---
config:
  look: handDrawn
  theme: neutral
---
sequenceDiagram
    participant Client
    participant UTLXe
    participant Bundle as Transformation Bundle<br/>(local directory)

    Note over UTLXe,Bundle: Startup (once)
    UTLXe->>Bundle: Load & compile all .utlx files
    Bundle-->>UTLXe: Compiled transformations cached

    Note over Client,UTLXe: Per message (runtime)
    Client->>UTLXe: POST /api/transform/invoice-to-ubl<br/>{input: Dynamics 365 JSON}
    UTLXe->>UTLXe: Lookup cached "invoice-to-ubl"
    UTLXe->>UTLXe: Parse → Transform → Serialize
    UTLXe->>Client: 200 OK<br/>{output: UBL 2.1 XML}
```

---

## 2b. Bundle Upload via Management API (EF03)

Azure Marketplace mode — the customer uploads transformation bundles via the admin API on port 8081. No custom image or volume mount required.

```mermaid
---
config:
  look: handDrawn
  theme: neutral
---
sequenceDiagram
    participant CICD as Customer<br/>CI/CD Pipeline
    participant Admin as UTLXe :8081<br/>(admin)
    participant Engine as UTLXe Engine
    participant Data as UTLXe :8085<br/>(data plane)
    participant App as Client Application

    Note over Admin: Container starts (empty)

    CICD->>Admin: POST /admin/bundle<br/>X-Admin-Key: ***<br/>[bundle.zip]
    Admin->>Admin: Extract ZIP, validate
    Admin->>Engine: Compile all .utlx files
    Engine-->>Admin: Compiled OK (342ms)
    Admin->>Admin: Write to /utlxe/data/
    Admin-->>CICD: 200 OK<br/>{"status":"deployed",<br/>"transformations": 3}

    Note over Data: Ready for traffic

    App->>Data: POST /api/transform/invoice-to-ubl<br/>{input JSON}
    Data->>Engine: Execute transformation
    Engine-->>Data: {output UBL XML}
    Data-->>App: 200 OK

    Note over CICD,Admin: Update single transformation
    CICD->>Admin: POST /admin/transformations/invoice-to-ubl<br/>[updated .utlx + config]
    Admin->>Engine: Compile → atomic hot-swap
    Admin-->>CICD: 200 OK (zero downtime)
```

---

## 3. Azure Service Bus via Dapr Sidecar

Event-driven — messages arrive from Service Bus, are transformed, and forwarded to an output topic/queue.

```mermaid
---
config:
  look: handDrawn
  theme: neutral
---
sequenceDiagram
    participant SB as Azure Service Bus
    participant Dapr as Dapr Sidecar
    participant UTLXe
    participant OutSB as Output Service Bus

    SB->>Dapr: Message from queue/topic
    Dapr->>UTLXe: POST /api/dapr/input/orders<br/>{message payload}
    UTLXe->>UTLXe: Parse → Transform → Serialize
    UTLXe->>Dapr: POST http://localhost:3500<br/>/v1.0/bindings/output-binding<br/>{transformed payload}
    Dapr->>OutSB: Forward to output queue/topic
    UTLXe-->>Dapr: 200 OK (ack original message)
    Dapr-->>SB: Acknowledge / Complete
```

---

## 4. Azure Event Hub via Dapr Sidecar

High-throughput streaming — messages from Event Hub partitions are processed and forwarded.

```mermaid
---
config:
  look: handDrawn
  theme: neutral
---
sequenceDiagram
    participant EH as Azure Event Hub
    participant Dapr as Dapr Sidecar
    participant UTLXe
    participant OutEH as Output Event Hub

    EH->>Dapr: Event from partition
    Dapr->>UTLXe: POST /api/dapr/input/events<br/>{event payload + metadata}
    UTLXe->>UTLXe: Parse → Transform → Serialize
    UTLXe->>Dapr: POST http://localhost:3500<br/>/v1.0/bindings/output-eventhub<br/>{transformed event}
    Dapr->>OutEH: Publish to output Event Hub
    UTLXe-->>Dapr: 200 OK
    Dapr-->>EH: Checkpoint offset
```

---

## 5. Kafka Consumer/Producer

Direct Kafka integration (without Dapr) for on-premise or multi-cloud deployments.

```mermaid
---
config:
  look: handDrawn
  theme: neutral
---
sequenceDiagram
    participant KIn as Kafka Input Topic
    participant UTLXe
    participant KOut as Kafka Output Topic

    KIn->>UTLXe: Poll messages (consumer group)
    UTLXe->>UTLXe: Parse → Transform → Serialize
    UTLXe->>KOut: Produce transformed message
    UTLXe->>KIn: Commit offset
    
    Note over KIn,KOut: 8-128 workers process<br/>partitions in parallel
```

---

## 6. Stdio / Unix Pipeline

CLI-style integration — UTLXe reads from stdin, writes to stdout. Used for scripting, CI/CD, and process piping.

```mermaid
---
config:
  look: handDrawn
  theme: neutral
---
sequenceDiagram
    participant Source as Source Process<br/>(curl, cat, etc.)
    participant UTLXe
    participant Sink as Sink Process<br/>(file, API, etc.)

    Source->>UTLXe: stdin (JSON/XML/CSV)
    UTLXe->>UTLXe: Parse → Transform → Serialize
    UTLXe->>Sink: stdout (transformed output)
    
    Note over Source,Sink: cat orders.json | utlxe pipe invoice.utlx > invoices.xml
```

---

## 7. Proto/gRPC (Native Binary Wrapper)

For .NET, Go, and Python wrappers — communication via Protocol Buffers over stdio or gRPC.

```mermaid
---
config:
  look: handDrawn
  theme: neutral
---
sequenceDiagram
    participant App as .NET/Go/Python App
    participant Wrapper as Language Wrapper
    participant UTLXe

    App->>Wrapper: Transform(input, scriptName)
    Wrapper->>UTLXe: Protobuf request via stdio
    UTLXe->>UTLXe: Deserialize proto → UDM<br/>Transform → Serialize
    UTLXe->>Wrapper: Protobuf response via stdio
    Wrapper->>App: TransformResult
    
    Note over App,UTLXe: Single long-running UTLXe process<br/>shared by all wrapper calls
```

---

## 8. Pipeline Chaining (Multi-Stage)

Multiple transformations chained — output of one feeds input of next via in-process queues.

```mermaid
---
config:
  look: handDrawn
  theme: neutral
---
sequenceDiagram
    participant Input as Input Source
    participant T1 as Stage 1<br/>parse-order.utlx
    participant T2 as Stage 2<br/>validate-order.utlx
    participant T3 as Stage 3<br/>route-by-country.utlx
    participant Output as Output Sink

    Input->>T1: Raw order (JSON)
    T1->>T2: Parsed canonical order<br/>(in-process, zero-copy)
    T2->>T3: Validated order<br/>(in-process, zero-copy)
    T3->>Output: Routed order (XML/JSON)
    
    Note over T1,T3: All stages in single JVM<br/>in-process queues between stages
```

---

## 9. Multi-Input Transformation

Single transformation consuming from multiple sources simultaneously.

```mermaid
---
config:
  look: handDrawn
  theme: neutral
---
sequenceDiagram
    participant Orders as Order Queue
    participant Customers as Customer API
    participant Rates as Exchange Rate File
    participant UTLXe
    participant Output as Output Queue

    Note over UTLXe: Startup: load customers + rates
    Customers->>UTLXe: Load customer reference data
    Rates->>UTLXe: Load exchange rates (constant pipe)

    Note over Orders,Output: Per message (runtime)
    Orders->>UTLXe: Order message arrives
    UTLXe->>UTLXe: $orders = message<br/>$customers = cached reference<br/>$rates = cached rates
    UTLXe->>UTLXe: Transform with all 3 inputs
    UTLXe->>Output: Enriched order
```

---

## 10. Health Check and Monitoring

Prometheus metrics scraping and Kubernetes liveness/readiness probes.

```mermaid
---
config:
  look: handDrawn
  theme: neutral
---
sequenceDiagram
    participant K8s as Kubernetes
    participant Prom as Prometheus
    participant UTLXe as UTLXe<br/>:8081 (health)<br/>:8085 (data)

    loop Every 10s
        K8s->>UTLXe: GET /health (liveness)
        UTLXe-->>K8s: {"status":"UP"}
    end

    loop Every 15s
        Prom->>UTLXe: GET /metrics
        UTLXe-->>Prom: utlxe_messages_total{...} 12345<br/>utlxe_transform_duration_seconds{...}<br/>utlxe_errors_total{...}
    end

    Note over K8s,UTLXe: Data plane on :8085<br/>Health/metrics on :8081<br/>(separate ports for security)
```

---

## 11. Dapr with Dead-Letter and Retry

Error handling flow — failed transformations are routed to dead-letter queue.

```mermaid
---
config:
  look: handDrawn
  theme: neutral
---
sequenceDiagram
    participant SB as Service Bus
    participant Dapr as Dapr Sidecar
    participant UTLXe
    participant DLQ as Dead-Letter Queue

    SB->>Dapr: Message arrives
    Dapr->>UTLXe: POST /api/dapr/input/orders
    UTLXe->>UTLXe: Parse → Transform
    
    alt Transformation succeeds
        UTLXe->>Dapr: POST /v1.0/bindings/output<br/>{transformed message}
        UTLXe-->>Dapr: 200 OK
        Dapr-->>SB: Complete message
    else Transformation fails
        UTLXe-->>Dapr: 500 Error<br/>{error details}
        Dapr-->>SB: Abandon (retry)
        Note over SB: After max retries
        SB->>DLQ: Move to dead-letter queue
    end
```

---

## 12. Full Azure Deployment (Case Study 1 — Peppol)

End-to-end flow for the European e-invoicing case study.

```mermaid
---
config:
  look: handDrawn
  theme: neutral
---
sequenceDiagram
    participant D365 as Dynamics 365<br/>Business Central
    participant SB1 as Service Bus<br/>Input Queue
    participant Dapr as Dapr Sidecar
    participant UTLXe as UTLXe Container App
    participant SB2 as Service Bus<br/>Output Topic
    participant AP as Peppol<br/>Access Point

    D365->>SB1: Invoice (OData JSON)
    SB1->>Dapr: Deliver message
    Dapr->>UTLXe: POST /api/dapr/input/invoices
    
    Note over UTLXe: 3 transformations loaded:<br/>1. invoice-to-ubl.utlx<br/>2. validate-ubl.utlx<br/>3. route-by-country.utlx
    
    UTLXe->>UTLXe: Stage 1: JSON → UBL 2.1 XML
    UTLXe->>UTLXe: Stage 2: Validate against Peppol BIS 3.0
    UTLXe->>UTLXe: Stage 3: Add country-specific tax rules
    
    UTLXe->>Dapr: POST /v1.0/bindings/peppol-output<br/>{UBL XML}
    Dapr->>SB2: Publish to output topic
    SB2->>AP: Deliver to Peppol Access Point
    AP-->>D365: AS4 receipt
```

---

*All diagrams use Mermaid syntax — renderable in GitHub, VS Code, and most Markdown viewers.*
