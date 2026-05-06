
= Why UTLXe?

Every integration project has the same problem: data arrives in one format and needs to leave in another. An order from Dynamics 365 is JSON. The Peppol e-invoicing network expects UBL XML. A warehouse system sends CSV. An IoT platform produces YAML. The business logic is often simple --- rename a field, calculate a total, filter a list --- but the plumbing to connect these systems is not.

This chapter explains where UTLXe fits in an Azure middleware architecture, what problems it solves, and why those problems are harder than they appear.

== The Transformation Problem

Consider a common Azure integration: Dynamics 365 Business Central publishes sales orders to Azure Service Bus. A downstream system consumes them and expects a different structure.

Without UTLXe, you write the transformation in one of these places:

#table(
  columns: (auto, 1fr, 1fr),
  [*Approach*], [*How*], [*Problem*],
  [Azure Function], [C\# or Python code that parses JSON, builds output, serializes], [Business logic buried in infrastructure code. Every mapping change requires a code deployment.],
  [Logic App], [Visual designer with built-in transforms], [Limited expression language. Complex mappings become unreadable. No version control for visual flows.],
  [API Management policy], [Liquid templates or XSLT in the gateway], [Mixing transformation with routing. Templates are hard to test. No debugging.],
  [In the producer], [D365 emits the target format directly], [Tight coupling. Producer must know every consumer's format. Changes ripple upstream.],
  [In the consumer], [Downstream system accepts the raw format], [Same coupling problem in reverse. Every consumer must handle every producer's format.],
)

All of these approaches scatter transformation logic across the architecture. When the Peppol specification changes, or a new trading partner requires a different XML dialect, or the warehouse system switches from CSV to JSON, someone has to find the right Azure Function, Logic App, or XSLT template and modify it.

#pagebreak()

== What the UTLXe Azure Offering Does

UTLXe is a dedicated transformation engine that runs as a container on Azure Container Apps. Its only job is to transform data --- parse an incoming message, apply mapping logic, and produce output in the required format.

UTLXe exposes two HTTP ports. That is its entire external interface:

```
                        ┌────────────────────┐
  Dapr sidecar    ────> │                    │ ────>  Dapr sidecar
  (Service Bus,         │      UTLXe         │        (output binding)
   Event Hub,           │                    │
   any binding)         │  :8085 data plane  │
                        │  :8081 admin API   │
  HTTP clients    ────> │                    │
  (direct)              │  Transform only.   │
                        │  No routing.       │
                        │  No orchestration. │
                        └────────────────────┘
```

Port 8085 is the data plane --- messages in, results out. Port 8081 is the admin API --- upload transformations, check health, view metrics. UTLXe never connects to Azure services directly. It receives and sends HTTP. The connection to messaging infrastructure is handled by a Dapr sidecar.

The following sections show each connection scenario with a concrete example.

Before showing the scenarios, it helps to understand what runs where. Azure Container Apps deploys UTLXe and Dapr as *two separate containers in the same pod*. They share a `localhost` network --- they can call each other on `localhost` without any network traversal or firewall rules.

```
┌─── Azure Container App (one pod) ──────────────────┐
│                                                      │
│  ┌──────────────────┐    ┌───────────────────────┐  │
│  │ UTLXe container   │    │ Dapr sidecar container│  │
│  │                   │    │ (managed by Azure)    │  │
│  │ localhost:8085 <──────── calls UTLXe here      │  │
│  │ (data plane)      │    │                       │  │
│  │ localhost:8081    │    │ localhost:3500         │  │
│  │ (admin + health)  │    │ (Dapr HTTP API) <──────── │
│  │                   │    │                       │  │ UTLXe
│  │                   │    │ Connects to:          │  │ calls
│  │                   │    │  Service Bus          │  │ here
│  │                   │    │  Event Hub            │  │
│  │                   │    │  (via Azure identity) │  │
│  └──────────────────┘    └───────────────────────┘  │
│                                                      │
│  Only port 8085 exposed via ingress (HTTPS)          │
│  Port 8081: internal VNet only                       │
│  Port 3500: localhost only (never exposed)           │
└──────────────────────────────────────────────────────┘
```

Key points:

- *Port 8085* (UTLXe data plane): exposed via Container App ingress with HTTPS. Dapr calls it on `localhost:8085` from inside the pod. External clients reach it via the ingress URL.
- *Port 8081* (UTLXe admin): internal to the VNet. Not exposed via ingress. Used by operators and CI/CD.
- *Port 3500* (Dapr HTTP API): localhost only, never leaves the pod. UTLXe calls it to send output messages. No firewall rule needed --- it is pod-internal.
- Dapr connects to Service Bus and Event Hub using Azure Managed Identity or connection strings stored as Container App secrets. UTLXe never sees these credentials.

#import "@preview/chronos:0.3.0"

#pagebreak()

=== Scenario 1: Azure Service Bus via Dapr

The most common pattern. Messages arrive on a Service Bus queue, get transformed, and are forwarded to an output queue.

#{set text(size: 6pt); figure(
  chronos.diagram({
    import chronos: *
    _par("SB-In", display-name: "Service Bus (input queue)")
    _par("Dapr", display-name: "Dapr Sidecar — localhost:3500")
    _par("UTLXe", display-name: "UTLXe — localhost:8085")
    _par("SB-Out", display-name: "Service Bus (output queue)")
    _seq("SB-In", "SB-In", comment: "message queued\n(by producer)", enable-dst: true)
    _seq("SB-In", "Dapr", comment: "1. AMQP 1.0 / TLS :5671\ndeliver (peek-lock:\nmessage locked, not removed)", disable-src: true, enable-dst: true)
    _note("right", pos: "SB-In", "message locked\nno reply yet —\ncomplete or\nabandon later")
    _seq("Dapr", "UTLXe", comment: "2. HTTP POST :8085\n/api/dapr/input/orders-in\n{JSON}", enable-dst: true)
    _note("right", pos: "Dapr", "Dapr input binding\n binds queue name to\n /api/dapr/input/orders-in")
    _seq("UTLXe", "UTLXe", comment: "3. Parse → Transform\n→ Serialize")
    _seq("UTLXe", "Dapr", comment: "4. HTTP POST :3500\n/v1.0/bindings/orders-out\n(new connection)", enable-dst: true)
    _seq("Dapr", "SB-Out", comment: "5. AMQP 1.0 / TLS :5671\nsend to output queue", enable-dst: true)
    _seq("SB-Out", "SB-Out", comment: "message\nqueued", disable-dst: true)
    _seq("Dapr", "UTLXe", comment: "6. HTTP 200 OK\n(reply to step 4)", dashed: true, disable-src: true)
    _seq("UTLXe", "Dapr", comment: "7. HTTP 200 OK\n(reply to step 2)", dashed: true, disable-src: true)
    _seq("Dapr", "SB-In", comment: "8. AMQP 1.0 — complete\n(message removed)", enable-dst: true)
    _seq("SB-In", "SB-In", comment: "message\nremoved", disable-dst: true)
    _seq("Dapr", "Dapr", comment: "", disable-dst: true)
  }),
  caption: [Scenario 1: Azure Service Bus queue-to-queue via Dapr sidecar],
)}

Step by step:

+ Service Bus delivers the message to Dapr over AMQP using *peek-lock* mode. The message is *locked* (invisible to other consumers) but *not removed*. There is no reply at this point --- the lock is held until Dapr sends a "complete" or "abandon" disposition later (step 8). The lock has a configurable timeout (default 30 seconds).
+ Dapr calls UTLXe: `POST localhost:8085/api/dapr/input/orders-in`. This HTTP request stays open until step 7.
+ UTLXe parses, transforms, and serializes the output.
+ UTLXe opens a *new, separate HTTP connection* to Dapr: `POST localhost:3500/v1.0/bindings/orders-out`. This is not a reply to step 2 --- it is an independent outbound call.
+ Dapr forwards the output to the output Service Bus queue via AMQP.
+ Dapr returns HTTP 200 to UTLXe's request from step 4.
+ UTLXe returns HTTP 200 to Dapr's original request from step 2 --- processing succeeded.
+ Dapr sends a *complete* disposition to Service Bus for the original message from step 1. The message is permanently removed from the input queue. If step 7 had returned HTTP 500, Dapr would send *abandon* instead --- the lock is released, and Service Bus makes the message visible again for retry. After the maximum delivery count is exceeded, the message moves to the dead-letter queue.

No Azure SDK, no connection code, no retry logic in UTLXe. Dapr handles the AMQP connection to Service Bus (port 5671, TLS), peek-lock management, authentication, retries, and dead-letter routing. The customer configures only *what* to connect to --- never *how*. Chapter 4 walks through the complete setup.

==== Rainy day: transformation fails

When UTLXe cannot transform a message (null reference, type mismatch, schema validation failure), it returns HTTP 500 instead of 200. This triggers the retry and dead-letter path:

#{set text(size: 6pt); figure(
  chronos.diagram({
    import chronos: *
    _par("SB-In", display-name: "Service Bus (input queue)")
    _par("Dapr", display-name: "Dapr Sidecar")
    _par("UTLXe", display-name: "UTLXe :8085")
    _par("DLQ", display-name: "Dead-Letter Queue")
    _seq("SB-In", "Dapr", comment: "1. AMQP deliver\n(peek-lock)", enable-dst: true)
    _seq("Dapr", "UTLXe", comment: "2. HTTP POST :8085\n/api/dapr/input/orders-in", enable-dst: true)
    _seq("UTLXe", "UTLXe", comment: "3. Transform FAILS\n(e.g. null reference)")
    _seq("UTLXe", "Dapr", comment: "4. HTTP 500 Error\n{error details}", dashed: true, disable-src: true)
    _seq("Dapr", "SB-In", comment: "5. AMQP abandon\n(unlock message)", disable-src: true, enable-dst: true)
    _seq("SB-In", "SB-In", comment: "delivery count++\nmessage visible again", disable-dst: true)
    _note("right", pos: "SB-In", "Service Bus retries\ndelivery (up to\nmaxDeliveryCount)")
    _seq("SB-In", "Dapr", comment: "... retry attempts ...", enable-dst: true)
    _seq("Dapr", "UTLXe", comment: "still fails", enable-dst: true)
    _seq("UTLXe", "Dapr", comment: "HTTP 500", dashed: true, disable-src: true)
    _seq("Dapr", "SB-In", comment: "abandon again", disable-src: true, enable-dst: true)
    _seq("SB-In", "DLQ", comment: "maxDeliveryCount exceeded\n→ move to dead-letter queue", disable-src: true, enable-dst: true)
    _seq("DLQ", "DLQ", comment: "message preserved\nfor investigation", disable-dst: true)
  }),
  caption: [Rainy day: transformation fails → retry → dead-letter queue],
)}

When a transformation fails:
- UTLXe returns HTTP 500 with error details (line number, error message).
- Dapr *abandons* the message on Service Bus --- the peek-lock is released, and the message becomes visible again.
- Service Bus increments the delivery count and redelivers the message.
- After `maxDeliveryCount` attempts (configurable on the queue, default 10), Service Bus moves the message to the *dead-letter queue* (DLQ).
- The DLQ preserves the original message for investigation. Use the Admin API (`GET /admin/transformations/{name}/errors`) to see recent error details.

The Event Hub rainy day is simpler: there is no dead-letter queue. If UTLXe returns 500, Dapr does not checkpoint the offset. The event is redelivered on the next consumer restart. Persistent failures require manual intervention (pause the transformation, fix, resume).

Dapr connects to Service Bus using YAML component definitions. The *component name* determines which UTLXe transformation is called --- Dapr posts to `/{component-name}` on the app port, and UTLXe looks up a transformation with that name.

At startup, Dapr sends an `OPTIONS /{component-name}` probe to verify UTLXe is listening. UTLXe responds with 200, and Dapr activates the binding. No `route` metadata is needed --- the component name IS the route.

```yaml
# Dapr input binding — component name "orders-in"
componentType: bindings.azure.servicebusqueues
metadata:
  - name: connectionString
    secretRef: servicebus-connection
  - name: queueName
    value: "incoming-orders"
  - name: route
    value: "/orders-in"       # binds this queue to transformation "orders-in"
scopes: [utlxe]
```

The `route` metadata makes the binding explicit: queue `incoming-orders` → route `/orders-in` → transformation `orders-in`. Without it, Dapr uses the component name as the path (same result, but implicit). The `route` is recommended because it makes the binding visible in one place --- you can read the YAML and know exactly which transformation handles this queue.

```yaml
# Dapr output binding — component name "orders-out"
# UTLXe calls: POST localhost:3500/v1.0/bindings/orders-out
componentType: bindings.azure.servicebusqueues
metadata:
  - name: connectionString
    secretRef: servicebus-connection
  - name: queueName
    value: "processed-orders"
scopes: [utlxe]
```

The output binding name (`orders-out`) is configured in the transformation's `transform.yaml` as `outputBinding: orders-out`. UTLXe calls Dapr's standard binding API at `localhost:3500/v1.0/bindings/orders-out`.

The complete wiring:

#table(
  columns: (auto, auto, 1fr),
  [*Configuration*], [*Where*], [*Connects*],
  [`route: /orders-in`], [Dapr input component], [Service Bus queue → `POST /orders-in` → UTLXe transformation `orders-in`],
  [`outputBinding: orders-out`], [UTLXe transform.yaml], [UTLXe → `POST :3500/v1.0/bindings/orders-out` → Dapr],
  [`queueName: processed-orders`], [Dapr output component], [Dapr output binding → Service Bus output queue],
  [`appPort: 8085`], [Container App Dapr config], [Dapr sidecar → UTLXe HTTP port],
)

==== How UTLXe knows the output binding

The output binding name (`orders-out`) is not passed by Dapr in the input call. It is pre-configured on the UTLXe side, in the transformation's `transform.yaml`:

```yaml
outputBinding: orders-out
```

UTLXe resolves the output binding from three sources (highest priority first): an environment variable override (`UTLXE_OUTPUT_BINDING_ORDERS_IN`), the `transform.yaml` config, or an `X-UTLXe-Output-Binding` HTTP header. The most common is the config file.

==== Concurrency and correlation

When multiple messages are processed concurrently (multiple Dapr calls to UTLXe at the same time), there is no correlation problem. Each message is handled on its own pair of HTTP connections:

```
Worker 1:  conn A: Dapr──POST :8085──>UTLXe (open)
           conn B: UTLXe──POST :3500──>Dapr (new, independent)
           conn A: UTLXe──200 OK──>Dapr (same TCP connection)

Worker 2:  conn C: Dapr──POST :8085──>UTLXe (open)
           conn D: UTLXe──POST :3500──>Dapr (new, independent)
           conn C: UTLXe──200 OK──>Dapr (same TCP connection)
```

HTTP is connection-based --- the 200 response goes back on the *same TCP connection* that Dapr opened. There is no cross-worker confusion. The output binding call (connection B/D) is completely independent --- Dapr does not need to match it to the input. The only thing that determines the input message's fate (complete vs. abandon) is the HTTP status code returned on the original connection.

If message ordering matters, set `maxConcurrentHandlers: 1` in the Dapr input component metadata, or use Service Bus sessions. But that is a Service Bus concern, not a UTLXe or Dapr correlation concern.

==== Message tracing and correlation

In production, you need to trace which input message produced which output message. UTLXe preserves the following metadata across the transformation:

UTLXe implements the standard messaging triad:

#table(
  columns: (auto, auto, 1fr),
  [*ID*], [*Constant?*], [*What happens*],
  [`MessageId`], [No], [New UUID generated for each output message.],
  [`CorrelationId`], [Yes], [Preserved from input. Links all messages in the same business process.],
  [`CausationId`], [No], [Set to the input's `MessageId`. Links each output to its immediate cause.],
  [`traceparent`], [—], [W3C Trace Context forwarded. Azure Monitor shows the full distributed trace.],
)

In a multi-step chain, the `CorrelationId` stays constant while the `CausationId` traces the parent:

All IDs are UUIDv7 (RFC 9562) --- time-ordered with an embedded timestamp, so IDs are sortable by creation time:

```
Producer:   MessageId=UUID-A  CorrelationId=UUID-CORR  CausationId=(none)
UTLXe:      MessageId=UUID-B  CorrelationId=UUID-CORR  CausationId=UUID-A
Next step:  MessageId=UUID-C  CorrelationId=UUID-CORR  CausationId=UUID-B
```

Every processed message is logged with all three IDs:

```
INFO [orders-in] MessageId=UUID-A  CorrelationId=UUID-CORR
     → transformed in 3ms → output MessageId=UUID-B
     CausationId=UUID-A
```

Error entries in the Admin API (`GET /admin/transformations/{name}/errors`) also include all three IDs, so you can correlate a failed transformation back to the specific input message on Service Bus.

#pagebreak()

=== Scenario 2: Azure Event Hub via Dapr

Same 8-step flow, different Dapr component type. UTLXe does not know the difference.

#{set text(size: 6pt); figure(
  chronos.diagram({
    import chronos: *
    _par("EH-In", display-name: "Event Hub (partition)")
    _par("Dapr", display-name: "Dapr Sidecar — localhost:3500")
    _par("UTLXe", display-name: "UTLXe — localhost:8085")
    _par("EH-Out", display-name: "Event Hub (output)")
    _seq("EH-In", "EH-In", comment: "event\navailable", enable-dst: true)
    _seq("EH-In", "Dapr", comment: "1. AMQP 1.0 / TLS :5671\ndeliver event", disable-src: true, enable-dst: true)
    _seq("Dapr", "UTLXe", comment: "2. HTTP POST :8085\n/api/dapr/input/events-in\n{JSON}", enable-dst: true)
    _seq("UTLXe", "UTLXe", comment: "3. Parse → Transform\n→ Serialize")
    _seq("UTLXe", "Dapr", comment: "4. HTTP POST :3500\n/v1.0/bindings/events-out\n(new connection)", enable-dst: true)
    _seq("Dapr", "EH-Out", comment: "5. AMQP 1.0 / TLS :5671\npublish to output", enable-dst: true)
    _seq("EH-Out", "EH-Out", comment: "event\nstored", disable-dst: true)
    _seq("Dapr", "UTLXe", comment: "6. HTTP 200 OK\n(reply to step 4)", dashed: true, disable-src: true)
    _seq("UTLXe", "Dapr", comment: "7. HTTP 200 OK\n(reply to step 2)", dashed: true, disable-src: true)
    _seq("Dapr", "EH-In", comment: "8. AMQP 1.0\ncheckpoint offset", enable-dst: true)
    _seq("EH-In", "EH-In", comment: "offset\ncommitted", disable-dst: true)
    _seq("Dapr", "Dapr", comment: "", disable-dst: true)
  }),
  caption: [Scenario 2: Azure Event Hub via Dapr sidecar],
)}

The only difference from Scenario 1 is the Dapr component type and the checkpointing mechanism. Event Hub uses a storage account for offset tracking. The UTLXe transformation is identical.

```yaml
# Dapr input binding — Event Hub
componentType: bindings.azure.eventhubs
metadata:
  - name: connectionString
    secretRef: eventhub-connection
  - name: consumerGroup
    value: "utlxe-consumer"
  - name: storageAccountName
    value: "stutlxecheckpoint"
  - name: storageContainerName
    value: "checkpoints"
scopes: [utlxe]
```

#pagebreak()

=== Scenario 3: Direct HTTP (no Dapr)

For synchronous request/response patterns --- API gateways, batch scripts, testing --- clients call UTLXe directly via the Azure ingress. No Dapr sidecar, no message queue, no YAML configuration.

#{set text(size: 6pt); figure(
  chronos.diagram({
    import chronos: *
    _par("Client", display-name: "HTTP Client (external)")
    _par("Ingress", display-name: "Azure Ingress (HTTPS :443)")
    _par("UTLXe", display-name: "UTLXe (HTTP localhost:8085)")
    _seq("Client", "Ingress", comment: "1. HTTPS POST :443\n/api/transform/invoice-to-ubl\n{JSON}", enable-dst: true)
    _seq("Ingress", "UTLXe", comment: "2. HTTP POST :8085\n(TLS terminated at ingress)", enable-dst: true)
    _seq("UTLXe", "UTLXe", comment: "3. Parse → Transform\n→ Serialize")
    _seq("UTLXe", "Ingress", comment: "4. HTTP 200\n{transformed output}", dashed: true, disable-src: true)
    _seq("Ingress", "Client", comment: "5. HTTPS 200\n{transformed output}", dashed: true, disable-src: true)
  }),
  caption: [Scenario 3: Direct HTTP via Azure ingress, no Dapr],
)}

```bash
curl -X POST \
  -H "Content-Type: application/json" \
  -H "X-Message-Id: <UUID-A>" \
  -H "X-Correlation-Id: <UUID-CORR>" \
  -H "X-Causation-Id: <UUID-PREV>" \
  -d '{"orderNumber":"12345","amount":200.00}' \
  https://myapp.azurecontainerapps.io/api/transform/invoice-to-ubl
```

Direct HTTP clients can send `X-Message-Id`, `X-Correlation-Id`, and `X-Causation-Id` headers for traceability. If `X-Message-Id` is omitted, UTLXe generates a UUID --- every request is traceable, even ad-hoc curl calls. The response echoes the correlation headers:

```
HTTP/1.1 200 OK
X-Message-Id: <UUID-B>
X-Correlation-Id: <UUID-CORR>
X-Causation-Id: <UUID-A>
X-Transform-Duration-Ms: 3
```

No Dapr, no Service Bus. A direct HTTPS call, a transformed response. Azure ingress handles TLS --- UTLXe receives plain HTTP on localhost:8085.

==== Rainy day: direct HTTP error

#{set text(size: 6pt); figure(
  chronos.diagram({
    import chronos: *
    _par("Client", display-name: "HTTP Client (external)")
    _par("Ingress", display-name: "Azure Ingress (HTTPS :443)")
    _par("UTLXe", display-name: "UTLXe (HTTP localhost:8085)")
    _seq("Client", "Ingress", comment: "1. HTTPS POST :443\n/api/transform/invoice-to-ubl\n{JSON}", enable-dst: true)
    _seq("Ingress", "UTLXe", comment: "2. HTTP POST :8085", enable-dst: true)
    _seq("UTLXe", "UTLXe", comment: "3. Transform FAILS\n(e.g. null reference)")
    _seq("UTLXe", "Ingress", comment: "4. HTTP 500\n{error, line, message}", dashed: true, disable-src: true)
    _seq("Ingress", "Client", comment: "5. HTTPS 500\n{error details}", dashed: true, disable-src: true)
  }),
  caption: [Rainy day: direct HTTP --- error returned to client],
)}

With direct HTTP, the error goes straight back to the caller. There is no retry, no dead-letter queue --- the client decides what to do (retry, log, alert). The error response includes the transformation name, line number, and error message.

=== The Transformation

In all three scenarios, the transformation is the same `.utlx` file:

```
%utlx 1.0
input json
output xml
---
{
  Invoice: {
    ID: concat("INV-", $input.orderNumber),
    IssueDate: formatDate(now(), "yyyy-MM-dd"),
    BuyerParty: { Name: $input.customer.name },
    Total: round($input.amount * 1.21, 2)
  }
}
```

The transformation does not contain any reference to Service Bus, Event Hub, or HTTP. It expresses only the mapping. The transport is a deployment concern, not a transformation concern.

This pattern applies wherever data crosses a format or schema boundary: e-invoicing (Peppol, UBL), API format conversion, IoT sensor normalization, ERP migration, and B2B partner integration.

UTLXe is capable of much more than what this Azure offering exposes --- including gRPC transport, protobuf-based language wrappers for .NET, Go, and Python, Kafka integration, and pipeline chaining. For the full capabilities, see the UTLXe documentation at `https://github.com/grauwen/utl-x`. This book covers only the Azure Marketplace deployment.

== The Cost of Embedded Transformation

To understand why a dedicated engine matters, consider what happens when transformation logic is embedded in an Azure Function:

```csharp
// OrderToInvoice.cs — 180 lines
public static async Task<IActionResult> Run(
    [ServiceBusTrigger("orders")] string message,
    [ServiceBus("invoices")] IAsyncCollector<string> output)
{
    var order = JsonConvert.DeserializeObject<Order>(message);
    var invoice = new Invoice {
        ID = "INV-" + order.OrderNumber,
        IssueDate = DateTime.UtcNow.ToString("yyyy-MM-dd"),
        // ... 150 more lines of mapping logic
    };
    var xml = new XmlSerializer(typeof(Invoice))...
    await output.AddAsync(xml);
}
```

This works, but:

- The mapping logic is mixed with the infrastructure (Service Bus trigger, serialization, error handling).
- Testing requires a running Service Bus emulator or mock.
- Changing one field mapping requires a full C\# build and deployment.
- The developer needs to know C\#, the Azure Functions SDK, and the XML serialization API --- in addition to the business mapping rules.

With UTLXe, the same mapping is twelve lines of `.utlx`. No build step, no SDK, no deployment pipeline for the container. Upload the file and it works. Change a field, re-upload, zero downtime.

== How UTLXe Runs on Azure

UTLXe is available as a pre-built container on the Azure Marketplace. You deploy it like any other Container App --- no custom Docker image needed.

```
┌──────────────────────────────────────────────────────────────────┐
│  Azure Container Apps Environment                                 │
│                                                                    │
│  ┌─────────────────┐    ┌────────────────┐    ┌────────────────┐ │
│  │  Dapr Sidecar    │    │                │    │  Dapr Sidecar   │ │
│  │  (input binding) │───>│     UTLXe      │───>│  (output bind.) │ │
│  └─────────────────┘    │                │    └────────────────┘ │
│         ↑                │  :8085 data    │           │           │
│  Azure Service Bus      │  :8081 admin   │    Azure Service Bus │
│  or Event Hub            │                │    or Event Hub       │
│                          └───────┬────────┘                       │
│                                  │                                │
│                          ┌───────┴────────┐                       │
│                          │  /utlxe/data/  │                       │
│                          │  Azure Files   │  persistent (optional)│
│                          └────────────────┘                       │
└──────────────────────────────────────────────────────────────────┘
```

- *Azure Container Apps* runs the container and handles scaling, health checks, networking, and TLS.
- *Dapr sidecar* connects to Azure messaging services. Configured with YAML, no code.
- *UTLXe* transforms messages. Configured with `.utlx` files, no code.
- *Azure Files* (optional) persists uploaded transformations across container restarts.

== The Deployment Workflow

Unlike Azure Functions or Logic Apps, UTLXe separates the container from the transformations. The container is deployed once from the Marketplace. The transformations are deployed independently via the Admin API:

+ *Deploy the container* from the Azure Marketplace (once).
+ *Upload transformations* via `POST /admin/bundle` or individual `POST /admin/transformations/{name}`.
+ *Test* each transformation with sample input via `POST /admin/transformations/{name}/test`.
+ *Send traffic* to the data plane on port 8085.
+ *Update transformations* at any time --- upload a new version, zero downtime.

No Docker builds, no container restarts, no CI/CD pipeline for infrastructure. The pipeline only ships `.utlx` files.

== What UTLXe Does Not Do

UTLXe is a transformation engine, not an integration platform. It deliberately does not route messages, orchestrate workflows, store data, or manage connections. Dapr handles messaging. Logic Apps handle orchestration. Azure Container Apps handles infrastructure. UTLXe handles transformation --- and only transformation.

This narrowness is intentional. A tool that does one thing well composes better than a tool that does everything adequately.

== When to Use UTLXe

Use UTLXe when:

- Data arrives in one format and must leave in another.
- Transformation logic changes more often than infrastructure.
- Multiple team members need to read and modify mappings.
- You want version-controlled, testable, reviewable transformation logic.
- You need zero-downtime updates for transformation rules.

Use Azure Functions or Logic Apps instead when:

- The integration involves orchestration (multi-step, conditional branching, human approval).
- The transformation is trivial (rename one field) and doesn't justify a dedicated engine.
- You need to call external APIs as part of the transformation (UTLXe is stateless and does not make outbound API calls during transformation).
