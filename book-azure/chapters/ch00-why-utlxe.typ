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

=== Scenario 1: Azure Service Bus via Dapr

The most common pattern. Messages arrive on a Service Bus queue, get transformed, and are forwarded to an output queue or topic.

```
┌──────────────┐         ┌──────────┐         ┌────────────┐         ┌──────────────┐
│ Service Bus  │ ──────> │   Dapr   │ ──────> │   UTLXe    │ ──────> │ Service Bus  │
│ input queue  │         │ sidecar  │  HTTP   │            │  HTTP   │ output topic │
│              │         │          │ :8085   │ transforms │ :3500   │              │
└──────────────┘         └──────────┘         └────────────┘         └──────────────┘
```

Dapr connects to Service Bus using a YAML component definition that you configure in the Azure Portal or via Azure CLI. When a message arrives on the queue, Dapr calls UTLXe at `POST http://utlxe:8085/api/dapr/input/orders-in`. UTLXe transforms the message and sends the result back to Dapr, which delivers it to the output queue.

No Azure SDK, no connection code, no retry logic in UTLXe. Dapr handles connection management, retries, and dead-letter routing. Chapter 4 walks through the complete setup step by step --- from creating the Service Bus namespace to configuring the Dapr component in the Azure Portal.

=== Scenario 2: Azure Event Hub via Dapr

Same pattern, different Dapr component type. Event Hub is used for high-throughput streaming scenarios.

```
┌──────────────┐         ┌──────────┐         ┌────────────┐         ┌──────────────┐
│ Event Hub    │ ──────> │   Dapr   │ ──────> │   UTLXe    │ ──────> │ Event Hub    │
│ partition    │         │ sidecar  │  HTTP   │            │  HTTP   │ output       │
│              │         │          │ :8085   │ transforms │ :3500   │              │
└──────────────┘         └──────────┘         └────────────┘         └──────────────┘
```

The Dapr component type changes to `bindings.azure.eventhubs`, but the UTLXe side is identical --- it does not know or care whether the message came from Service Bus or Event Hub. Dapr abstracts the transport. The step-by-step setup is covered in Chapter 4.

=== Scenario 3: Direct HTTP (no Dapr)

For request/response patterns --- API gateways, batch scripts, testing --- clients call UTLXe directly:

```
┌──────────────┐                   ┌────────────┐
│ HTTP client  │ ────────────────> │   UTLXe    │
│ (API gateway,│       HTTP        │            │
│  script,     │      :8085        │ transforms │
│  curl)       │ <──────────────── │            │
└──────────────┘                   └────────────┘
```

```bash
curl -X POST \
  -H "Content-Type: application/json" \
  -d '{"orderNumber":"12345","amount":200.00}' \
  http://utlxe-ingress:8085/api/transform/invoice-to-ubl
```

No Dapr, no Service Bus. A direct HTTP call, a transformed response. Useful for synchronous integrations, testing, and API format conversion.

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
